# SQL Validation and Name Resolution in Calcite

## Why this note exists

Calcite bugs around duplicate column names, star expansion, alias lookup, and
nested subqueries are rarely isolated syntax problems. They usually come from a
mismatch between:

- how the validator represents query output columns,
- how that representation is propagated through nesting,
- and where explicit name lookup is allowed to fail as ambiguous.

This note is a reusable mental model for contributors who need to work in
Calcite's SQL validator and name-resolution stack. It is intentionally focused
on the validator boundary rather than on one specific bug.

## Plain-English model

When Calcite validates a query, it is trying to answer two different questions:

1. What columns does this query produce?
2. If a later expression says `x`, which visible column does `x` refer to?

Those are related but distinct.

A query result may legitimately contain duplicate output names. That is a fact
about the result shape.

An explicit reference such as `select x`, `where x = 1`, or `order by x` is a
different operation. It asks Calcite to bind a name to one unique visible
column. If more than one visible column matches, the reference is ambiguous.

`SELECT *` is different again. It does not ask for a unique binding by name. It
means "project all currently visible output columns in order".

The main mistake in this area is to collapse these three ideas into one:

- output representation,
- propagation through nesting,
- explicit lookup and ambiguity.

If those concerns are mixed together, Calcite starts silently renaming fields
too early, or it incorrectly rejects legal `SELECT *`, or it incorrectly allows
ambiguous outer references.

## Core concepts

### Row type

A row type is the ordered list of output fields produced by a relational or SQL
expression. In validator terms, it carries field names, types, nullability, and
order.

Important rule:

- the validator row type should reflect SQL-visible output shape as closely as
  possible

That means duplicate names may be valid in validator row types.

### Namespace

A namespace is Calcite's object for "this SQL construct produces these
columns". A table, subquery, join, or select item can all expose a namespace.

A namespace answers questions like:

- what row type does this thing expose?
- where did a field come from?
- what is visible to an outer query?

### Scope

A scope is the name-resolution context. It answers:

- what names are visible here?
- how do identifiers qualify to a specific source?
- if a name appears in more than one visible place, is it ambiguous?

Scopes are where explicit name lookup should fail when the visible name is not
unique.

### Field lookup

Field lookup is the operation of resolving an identifier to a visible field.
This is where ambiguity matters.

If there are two visible `DEPTNO` fields and the query explicitly says
`DEPTNO`, name lookup should fail.

### `SELECT *`

`SELECT *` is not ordinary name lookup. It is expansion of the current visible
row type by position and order.

That means `SELECT *` should still work even when the visible row type contains
duplicate names.

### Alias

An alias is a visible output name assigned by the query, for example
`ename as num`.

Aliases affect the output row type. They do not guarantee uniqueness.

### Outer name resolution

A subquery result becomes visible to an outer query through its namespace. The
outer scope then performs explicit lookup against the subquery's exposed row
type.

If the subquery exposes duplicate `NUM` columns, `select * from (...)` is fine,
but `select num from (...)` should be ambiguous.

## End-to-end data flow

For a typical nested query:

1. Calcite parses SQL into a `SqlNode` tree.
2. The validator builds or derives a row type for each `SELECT`.
3. That row type is attached to a namespace representing the select or
   subquery.
4. The outer scope sees the subquery through that namespace.
5. `SELECT *` expands the visible row type positionally.
6. Explicit identifiers are resolved by name through scope lookup.
7. After validation, `SqlToRelConverter` turns validated SQL into relational
   algebra and may uniquify internal rel-field names if needed.

The important architectural point is that validator semantics come first. The
converter should consume an already-correct interpretation, not invent SQL name
semantics on its own.

## The invariant model

### Invariant 1: validator row types preserve SQL-visible output names

If a query produces two output columns both named `NUM`, the validator row type
should preserve both `NUM` fields in order.

This applies whether the duplicates came from:

- repeated explicit aliases,
- `expr, *`,
- a nested subquery,
- or multiple star expansions such as `e.*, d.*`.

### Invariant 2: `SELECT *` is positional

`SELECT *` means "project the visible output row type in order". It should not
re-run ambiguous name lookup just because visible names happen to be duplicated.

### Invariant 3: explicit name lookup must be unique

Any explicit reference by name should fail if more than one visible field
matches that name.

This includes:

- select-list expressions
- `ORDER BY`
- alias-based lookup
- outer references into subqueries

### Invariant 4: rel-level uniquification is a later concern

Relational operators often need unique field names for internal bookkeeping,
digest stability, or generated plans. That does not mean SQL-visible validator
semantics should uniquify names early.

The correct split is:

- validator: preserve SQL-visible names and ambiguity behavior
- sql-to-rel / rel layer: uniquify internal field names if needed

## Architectural boundaries

### Representation

Representation is about what fields exist and what their visible names are.

This belongs in validator row-type derivation and namespace construction.

If duplicate names are destroyed here, every later layer is forced to work from
the wrong model.

### Propagation

Propagation is about keeping the same logical field identity through star
expansion and nesting.

This belongs in validator star expansion and qualification metadata.

If star-expanded fields are converted back into plain identifiers without
preserving source-field identity, nested `SELECT *` will reintroduce ambiguity
incorrectly.

### Lookup and ambiguity

Lookup is about binding an explicit name to one visible field.

This belongs in scopes and explicit resolution paths such as `ORDER BY` and
alias expansion.

If ambiguity is checked too early, legal `SELECT *` breaks. If ambiguity is
checked too late, explicit references resolve when they should fail.

## Classes and methods to know

### `SqlValidatorImpl`

The main validator. It owns select-list validation, star expansion, type
derivation, and a large part of SQL-visible row-type behavior.

Methods worth inspecting first:

- `expandStar(...)`
  - expands `*` and `t.*` into individual output expressions
- `addToSelectList(...)`
  - adds expanded expressions and field names into the validator's output list
- `validateSelectList(...)`
  - builds validated select output shape
- `DeriveTypeVisitor.visit(SqlIdentifier)`
  - derives identifier types during validation
- `OrderExpressionExpander.visit(SqlIdentifier)`
  - resolves `ORDER BY` expressions and alias references

This is usually the first file to inspect when star expansion and duplicate
output names behave inconsistently.

### `DelegatingScope`

The common scope implementation for identifier qualification.

Its job is to turn a visible identifier into a qualified field reference in the
current scope chain. This is a key place to inspect when explicit resolution and
star-expanded field identity disagree.

### `OrderByScope`

The special scope used for `ORDER BY`.

Its job is to apply `ORDER BY`-specific lookup rules. This is the right place to
check whether explicit `ORDER BY x` is correctly rejected as ambiguous.

### `SqlValidatorNamespace`

The abstraction for "this SQL construct exposes these columns".

It is the right place to think about what an outer query sees from a subquery,
join, or table reference.

### `SqlToRelConverter`

The boundary from validated SQL into relational algebra.

Its job is to convert already-validated semantics into `RelNode` and `RexNode`
form. It may need internal field ordinals and rel-level unique names, but it
should not redefine validator ambiguity rules.

## Common failure modes

### Failure mode 1: early uniquification

Symptoms:

- duplicate names disappear from subquery row types
- explicit outer references incorrectly succeed
- result metadata exposes synthetic suffixes too early

Typical cause:

- star expansion or row-type derivation forces unique aliases in the validator

### Failure mode 2: lost field identity during `*`

Symptoms:

- `select * from (select num, num from ...)` fails when it should succeed
- nested `SELECT *` becomes ambiguous even though no explicit name reference was
  made

Typical cause:

- star-expanded fields are reconstructed as ordinary identifiers and later
  re-resolved by name

### Failure mode 3: ambiguity checked in the wrong layer

Symptoms:

- `SELECT *` fails for duplicate names
- `ORDER BY x` behaves differently from `SELECT x`
- alias expansion picks the first match instead of rejecting ambiguity

Typical cause:

- explicit ambiguity checks are missing from scope resolution or `ORDER BY`
  expansion

### Failure mode 4: rel-layer behavior leaks back into validator semantics

Symptoms:

- internal plan field names such as `NUM0` become treated as SQL-visible names
- fixes are attempted in `SqlToRelConverter` without fixing validator semantics

Typical cause:

- confusion between SQL-visible row types and internal relational field naming

## Debugging playbook

### Start with tests, not with code

First determine which existing test class already owns the behavior:

- `SqlValidatorTest`
  - validator semantics and row types
- `SqlToRelConverterTest`
  - validated SQL to rel boundary
- `RelToSqlConverterTest`
  - SQL regeneration constraints when names are duplicated
- `JdbcTest`, `BabelTest`, `CoreQuidemTest`
  - user-visible metadata and end-to-end behavior

Add or inspect the smallest failing query in the validator first.

### Fast entry points

If you are new to this area, start from this short routing table before reading
more code.

| Symptom | First code to inspect | Why |
|---|---|---|
| Inner query exposes the wrong field names or wrong duplicate behavior | `SqlValidatorImpl.expandStar(...)`, `SqlValidatorImpl.addToSelectList(...)`, `SqlValidatorImpl.validateSelectList(...)` | These methods build the validator-visible output shape. |
| Nested `SELECT *` fails even though no explicit name is being referenced | `SqlValidatorImpl.expandStar(...)`, `DelegatingScope.fullyQualify(...)` | This usually means star-expanded field identity was lost and Calcite fell back to name lookup. |
| `ORDER BY x` behaves differently from `SELECT x` | `OrderByScope.resolveColumn(...)`, `SqlValidatorImpl.OrderExpressionExpander` | `ORDER BY` has its own explicit resolution path. |
| Validator behavior is right but the rel plan points at the wrong field | `SqlToRelConverter.convertIdentifier(...)` | This is where validated field identity is mapped to rel ordinals. |
| SQL regeneration fails when duplicate output names exist | `RelToSqlConverterTest` and the rel-to-sql implementation being exercised | Rel-to-sql often needs to avoid emitting `SELECT *` when duplicate names would make later references ambiguous. |

### Fast test entry points

Use the smallest test surface that owns the behavior.

| Concern | Test class to start with |
|---|---|
| Validator semantics, row types, ambiguity | `SqlValidatorTest` |
| Validator-to-rel boundary | `SqlToRelConverterTest` |
| Rel-to-sql regeneration constraints | `RelToSqlConverterTest` |
| JDBC-visible result labels and metadata | `JdbcTest` |
| Dialect-specific visible-name behavior | `BabelTest` |
| End-to-end scripted regressions | `CoreQuidemTest`, `CoreQuidemTest2` |

### Classify the bug before editing

Ask four questions:

1. Is the output row type wrong?
2. Is the output row type right, but lost through nesting?
3. Is propagation correct, but explicit lookup is wrong?
4. Is the validator correct, but the rel boundary is misusing that information?

If you do not answer those separately, you will likely patch the wrong layer.

### Trace in this order

1. Validator row type of the inner query
2. Namespace row type exposed to the outer query
3. Scope lookup for the outer identifier
4. `ORDER BY` / alias-specific resolution
5. `SqlToRelConverter` ordinal mapping

This order forces you to distinguish representation, propagation, and lookup.

### Prefer structural probes over guesswork

Useful probes:

- assert validator field names directly in `SqlValidatorTest`
- add targeted `SqlToRelConverterTest` plans to see rel-level naming
- inspect `explain plan` only after validator behavior is understood
- compare a plain query with its nested `SELECT *` version
- compare explicit lookup (`select x`) with positional lookup (`select *`)

### Decide the correct layer for the fix

Use these heuristics:

- if the visible output shape is wrong, fix the validator
- if nested `*` loses identity, fix star expansion or qualification metadata
- if explicit lookup resolves when it should fail, fix scopes or `ORDER BY`
- if the validator is right and only rel conversion is wrong, fix
  `SqlToRelConverter`

### Be suspicious of "small" fixes

Tempting but usually wrong approaches:

- special-casing one query form such as subquery stars only
- preserving old suffixes just because tests expect them
- teaching `ORDER BY` to pick a first match
- uniquifying names early to avoid ambiguity

These approaches often make one test pass while preserving the wrong model.

## How to use this note in future sessions

When a bug involves validation or name resolution:

1. Read this note first.
2. Write down the intended invariant before changing code.
3. Classify the failure as representation, propagation, lookup, or rel-boundary.
4. Extend the smallest relevant test class first.
5. Only after the core invariant is fixed, update downstream metadata or Quidem
   expectations.

## Knowledge-maintenance workflow

After each major debugging session in this area:

1. Extract reusable invariants and add them here.
2. Keep this file focused on durable system knowledge.
3. Put issue-specific details in a separate archive note.
4. Do not fill the core note with one-off commands, temporary probes, or branch
   history.
