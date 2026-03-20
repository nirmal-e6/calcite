# SQL Validation and Name Resolution in Calcite

## Why this guide exists

Calcite validator bugs often look like isolated SQL oddities:

- duplicate output names behave inconsistently
- `SELECT *` works in one nesting shape and fails in another
- `ORDER BY x` resolves differently from `SELECT x`
- `GROUP BY alias` passes under one parser configuration and fails under another

Those are usually not four unrelated problems. They tend to come from the same
architectural mistake: mixing together responsibilities that should stay
separate.

The main separations to preserve are:

- representation: what SQL-visible shape a query produces
- propagation: how that shape and field identity move through nesting and `*`
- lookup: how an explicit identifier binds to one visible field
- grouped-expression equivalence: how aggregate validation decides whether a
  `SELECT` expression is one of the grouped expressions

This guide is meant to be the reusable starting point for future contributor
sessions in this part of Calcite.

## Plain-English model

When Calcite validates a query, it is trying to answer several different
questions.

1. What columns does this query produce?
2. If a later expression says `x`, which visible column does `x` refer to?
3. If the query groups, is a `SELECT` expression legal because it is grouped or
   aggregated?

Those questions are related, but they are not the same.

Examples:

- A query result may legally contain duplicate output names.
- An explicit identifier such as `select x` or `order by x` must bind to one
  unique visible field, or fail as ambiguous.
- `SELECT *` is not explicit lookup at all; it means "project all visible
  fields in order".
- Aggregate legality is not ordinary name lookup either; Calcite has to compare
  the current `SELECT` expression against the set of grouped expressions it has
  collected from `GROUP BY`, `SELECT DISTINCT`, or grouping functions.

Most bugs in this area happen when Calcite treats one of those questions as if
it were another.

## Core concepts

### Row type

A row type is the ordered list of output fields exposed by a SQL construct.

In validator terms it carries:

- field names
- types
- nullability
- order

Important rule:

- validator row types should preserve SQL-visible output shape as closely as
  possible

That means duplicate names may be correct in validator row types.

### Namespace

A namespace is Calcite's object for "this SQL construct exposes these columns".

A namespace answers questions like:

- what row type does this construct expose?
- where did each field come from?
- what becomes visible to an outer query?

Subqueries, tables, joins, and selects all expose namespaces.

### Scope

A scope is the current name-resolution context.

It answers:

- what names are visible here?
- how are identifiers qualified?
- if more than one visible field matches a name, is the reference ambiguous?

Scopes own explicit lookup semantics.

### Identifier representation

The parser stores identifiers as text inside the `SqlNode` tree.

That text depends on parser configuration:

- `withUnquotedCasing(...)`
- `withQuotedCasing(...)`
- `withCaseSensitive(...)`

But the parsed spelling is not the final semantic truth. Later validator steps
may:

- fully qualify identifiers
- rewrite identifier spelling to match schema fields
- expand aliases back into expressions

Therefore parser casing can expose representation bugs even when lookup itself
is semantically correct.

### Name matcher

Name lookup is controlled by a `SqlNameMatcher`, typically provided by the
catalog reader.

Its job is to answer:

- whether matching is case-sensitive
- which schema/table/field a textual name refers to

This layer owns lookup semantics. It does not, by itself, define grouped
expression equivalence.

### Alias

An alias is a SQL-visible output name, for example `ename as num`.

Aliases affect the output row type. They do not guarantee uniqueness.

Aliases also matter in aggregate validation because lenient `GROUP BY alias`
behavior expands the alias back to the underlying select expression.

### Grouped expression

A grouped expression is an expression the validator treats as available in an
aggregating query because it came from:

- `GROUP BY`
- `SELECT DISTINCT`
- grouping functions or related constructs

This is not just about identifiers. Whole expressions such as `CASE`,
`COALESCE`, or expanded aliases may need to match.

## End-to-end validator flow

For a typical query, the high-level flow is:

1. Parse SQL into a `SqlNode` tree.
2. Build scopes and namespaces.
3. Derive row types and visible output names.
4. Resolve explicit identifiers through scopes.
5. Validate grouping, ordering, and other context-sensitive rules.
6. Only after validation, convert to relational algebra.

The important architectural point is:

- validator semantics come first
- `SqlToRelConverter` should consume an already-correct interpretation
- rel-layer field naming should not define SQL semantics

## Aggregate and `GROUP BY` validation flow

Aggregate validation adds a second, more subtle pipeline:

1. Parse the original expression text using the configured casing policy.
2. Validate `GROUP BY` before the final `SELECT` aggregate legality checks.
3. If grouping by alias is allowed, expand `GROUP BY alias` back to the
   underlying select expression.
4. Fully qualify identifiers through scope resolution.
5. Derive types and apply coercion, for example in `CASE` branches.
6. Collect grouped expressions.
7. Later validate each `SELECT` expression in an aggregating scope by comparing
   it against the grouped-expression set.

This is where a distinct class of bugs appears:

- the grouped-expression set and the `SELECT` expression being checked may have
  traveled through different validator rewrite paths
- if Calcite compares raw trees too literally, a semantically valid grouped
  expression can be rejected

## Clause-local front-end flow

Some SQL features are not "just another expression inside `SELECT`". They are
clause-local constructs with their own small front-end pipeline.

Plain English first:

- the parser has to admit the clause syntax
- the SQL tree has to remember the clause structure
- the validator has to decide what the clause means and what row type it
  exposes
- `SqlToRelConverter` has to lower that validated meaning into rel algebra

If one of those layers still assumes an older semantic model, the feature is
not actually implemented even if another layer was relaxed.

Typical examples are:

- `PIVOT`
- clause-local grouping constructs
- other SQL features whose semantics are not just "ordinary expression
  validation inside `SELECT`"

For these features, the important distinctions are:

- what syntax the parser admits
- what structure the clause AST records
- what forms the validator accepts as legal
- what output shape the validator exposes
- how `sql2rel` lowers the validated meaning

Those are different questions. They should not be collapsed into one method or
one flag.

### `PIVOT` as a model example

`PIVOT` is a good model problem because it exercises all four layers at once.

It has:

- a dedicated clause node
- clause-local validation rules
- implicit grouping derived from clause structure
- a lowering step that often rewrites into filtered aggregates plus final
  projection logic

The reusable lesson is not "`PIVOT` is special". The lesson is:

- if a clause-local feature changes semantic shape, parser admission, AST
  bookkeeping, validator legality, and lowering all need to be checked

For `PIVOT`, one common example is when a measure stops being "one aggregate
call" and becomes "a larger scalar expression built from aggregate terms".

### Parser, validator, and converter must agree on the semantic shape

For a clause-local feature, there is a recurring failure pattern:

1. The parser still admits only the old syntax shape.
2. The validator is relaxed to accept a richer semantic shape.
3. The converter still lowers according to the old assumption.

This creates a design mismatch, not a small bug.

Example:

- if validator starts accepting "scalar expression over aggregate terms"
- but converter still assumes "one measure = one aggregate call"

then the feature is only half implemented. The correct fix is not to add a
special-case rejection later. The correct fix is to teach the converter the new
semantic shape.

### Structural bookkeeping versus support policy

Clause-local AST helpers often need to be broader than the currently supported
public surface.

For `PIVOT`, a structural helper may need to answer:

- "is this subtree an aggregate term?"
- "which input columns are consumed by the clause?"

That is different from:

- "is this measure form supported under the current conformance?"

This distinction matters because structural bookkeeping is used for:

- implicit grouping
- aggregate-term extraction
- expression decomposition

while support policy belongs in validator legality checks.

If those concerns are merged, Calcite often ends up with one of two bad
outcomes:

- structural analysis becomes too weak to support future extensions
- or unsupported syntax accidentally becomes accepted because the structural
  helper was mistaken for the policy layer

### Generic rel helpers are not the SQL semantic authority

Calcite often uses generic rel-building helpers under the SQL front end.

That does not make those helpers the owner of SQL semantics.

For `PIVOT`, a rel helper such as `RelBuilder.pivot(...)` can reasonably own:

- a generic "filtered aggregate pivot" construction

But it should not silently decide:

- which `PIVOT` measures are legal SQL
- how output names should be validated
- whether a richer measure is a scalar expression over aggregate terms

Those belong to parser, AST, validator, and `SqlToRelConverter` respectively.

## The invariant model

### Invariant 1: validator row types preserve SQL-visible output names

If a query produces two output columns both named `NUM`, the validator row type
should preserve both `NUM` fields in order.

This applies whether duplicates come from:

- repeated explicit aliases
- `expr, *`
- a nested subquery
- multiple star expansions such as `e.*, d.*`

### Invariant 2: `SELECT *` is positional propagation, not explicit lookup

`SELECT *` means "project the current visible row type in order".

It should not be rejected just because visible names happen to be duplicated.

### Invariant 3: explicit name lookup must be unique

Any explicit reference by name should fail if more than one visible field
matches.

This includes:

- select-list expressions
- `ORDER BY`
- alias-based lookup
- outer references into subqueries

### Invariant 4: rel-level uniquification is a later concern

Relational operators often need unique field names for internal bookkeeping.
That does not mean validator semantics should uniquify names early.

Correct split:

- validator: preserve SQL-visible names and ambiguity behavior
- rel layer: uniquify internal field names if needed

### Invariant 5: grouped-expression equivalence must ignore validator-only
representation noise

When Calcite checks whether a `SELECT` expression is grouped, the answer should
not depend on whether validator rewriting encoded type information:

- in the tree itself
- or only in validator metadata

This rule is intentionally narrow:

- ignore validator-only representation noise
- keep semantically meaningful structure significant

For example, grouped-expression comparison may need to treat:

- bare `NULL`
- `CAST(NULL AS <type>)`

as equivalent when the cast was introduced only by validator coercion.

### Invariant 6: grouped-expression legality must not depend on parser spelling
or tree sharing

Parser spelling may affect the initial `SqlNode` text.

That does not mean aggregate legality should flip merely because:

- the parser preserved lower-case spelling instead of upper-case
- an identifier had to be fully qualified on one path but not another
- one validator path reused a mutable tree and another rebuilt it

If success depends only on shared mutable `SqlNode` identity, Calcite is not
using a sound semantic invariant.

## Architectural boundaries

### Representation

Representation is about what fields exist and what visible names they have.

This belongs in:

- row-type derivation
- namespace construction
- select-list output construction

If representation is wrong, every later layer is operating on the wrong model.

### Propagation

Propagation is about carrying field identity and output shape through:

- `SELECT *`
- subqueries
- alias expansion
- nesting

If propagation loses identity, later stages often fall back to name lookup and
create false ambiguity.

### Lookup and ambiguity

Lookup is about binding an explicit textual reference to one visible field.

This belongs in:

- scopes
- qualification
- `ORDER BY`-specific lookup
- alias lookup rules

If ambiguity is checked too early, legal `SELECT *` breaks.
If ambiguity is checked too late, illegal explicit references succeed.

### Grouped-expression equivalence

Grouped-expression equivalence is about deciding whether a `SELECT` expression
is available in an aggregating query.

This belongs in:

- grouped-expression collection
- grouped-expression comparison
- aggregate validation

It should not be "fixed" by:

- changing parser casing defaults
- changing schema storage case
- forcing different validator paths to share one mutable tree

## Classes and methods to know

### `SqlValidatorImpl`

The main validator.

It owns select-list validation, star expansion, type derivation, grouping
validation, and several expansion paths that feed aggregate checking.

Useful entry points:

- `expandStar(...)`
  - expands `*` and `t.*` into concrete output expressions
- `addToSelectList(...)`
  - constructs the validator-visible output list
- `validateSelectList(...)`
  - validates select items and builds output row shape
- `validateGroupClause(...)`
  - validates and expands the `GROUP BY` clause
- `expand(...)`
  - expands expressions for validator comparison and qualification

### `DelegatingScope`

The common scope implementation for identifier qualification.

Its job is to turn visible identifiers into qualified field references in the
current scope chain.

This is the place where a parsed `ename` may become schema field `ENAME`.

### `OrderByScope`

The special scope for `ORDER BY`.

Its job is to apply `ORDER BY`-specific lookup rules, which may differ from the
raw select-list scope.

### `GroupByScope`

The scope used while validating `GROUP BY`.

Its job is to expand group expressions, including lenient `GROUP BY alias`
behavior.

### `AggregatingSelectScope`

The scope used when validating a grouped `SELECT`.

Its job is to decide whether a `SELECT` expression is legal given grouped
expressions and aggregates.

### `AggChecker`

The visitor that walks `SELECT` expressions in aggregate queries.

It is often the place where grouped-expression bugs surface, because it throws
the final "expression is not being grouped" validation error.

### `SqlValidatorUtil`

A shared validator utility class.

It is the right place for reusable helper logic such as grouped-expression
lookup and grouped-expression equivalence rules.

### `SqlPivot`

The SQL parse-tree node for `PIVOT`.

It owns the clause structure:

- input query
- measure list
- axis list
- `IN` list

This is also the right place for structural helpers such as:

- aggregate-term discovery inside a measure expression
- consumed-column discovery for implicit grouping

### `PivotScope`

The name-resolution scope used inside a `PIVOT` clause.

Its job is to resolve expressions in pivot measures and pivot axes against the
input query.

### `PivotNamespace`

The validator namespace for a `PIVOT` result.

Its job is to expose the row type that the validated `PIVOT` clause produces.

### `SqlValidatorFixture`

The fluent test fixture used in `SqlValidatorTest`.

It is the fastest way to probe validator behavior because it lets you vary
conformance, parser config, case-sensitivity, catalog behavior, and expected
validation outcome without leaving the validator layer.

### `SqlTestFactory`

The test-time factory that wires parser config, validator config, operator
table, and catalog reader together.

It is the place to inspect when a validator test changes behavior under custom
parser casing or case-sensitivity settings, because this is where those knobs
actually enter the test harness.

### `TypeCoercionImpl`

Calcite's main implicit-coercion implementation.

Its job is to decide or insert coercions for expressions such as `CASE`,
`COALESCE`, comparisons, and row-type alignment.

### `SqlCaseOperator`

The operator that derives types for `CASE` and CASE-equivalent expressions.

It infers the return type, assigns types to `NULL` branches, and cooperates
with implicit coercion when branch types differ.

### `SqlToRelConverter`

The boundary from validated SQL into relational algebra.

Its job is to consume already-validated semantics and map them to rel ordinals
and expressions. It should not redefine validator lookup or ambiguity rules.

For clause-local features such as `PIVOT`, this is also the layer that must
perform structural lowering:

- decompose validated SQL constructs into rel-friendly pieces
- preserve the validated meaning while doing so
- keep rel-internal names from leaking back into SQL semantics

### `RelBuilder`

Calcite's helper for constructing relational algebra trees.

Its job is to provide convenient generic rel-building operations. When used from
the SQL front end, it is a lowering tool, not the owner of SQL validation
rules.

## Common failure modes

### Failure mode 1: early uniquification

Symptoms:

- duplicate names disappear from subquery row types
- explicit outer references incorrectly succeed
- synthetic suffixes such as `DEPTNO0` appear too early

Typical cause:

- validator row-type construction forces uniqueness instead of preserving the
  SQL-visible shape

### Failure mode 2: lost field identity during `*`

Symptoms:

- nested `SELECT *` becomes ambiguous even though no explicit name was used
- `select * from (select num, num from ...)` fails when it should succeed

Typical cause:

- star-expanded fields were rebuilt as ordinary identifiers and later
  re-resolved by name

### Failure mode 3: ambiguity checked in the wrong layer

Symptoms:

- `SELECT *` fails for duplicate names
- `ORDER BY x` behaves differently from `SELECT x`
- alias resolution silently picks one match

Typical cause:

- explicit ambiguity logic lives in the wrong layer or is missing from the
  relevant scope

### Failure mode 4: rel-layer behavior leaks back into validator semantics

Symptoms:

- internal rel names such as `NUM0` are treated as SQL-visible names
- fixes are attempted in `SqlToRelConverter` even though the validator model is
  wrong

Typical cause:

- confusion between SQL-visible semantics and rel-internal field naming

### Failure mode 5: inconsistent grouped-expression representation

Symptoms:

- `GROUP BY alias` works under one parser configuration and fails under another
- the failure message points at an identifier that actually resolves correctly
- `GROUP BY alias` fails while `GROUP BY <same expression>` succeeds

Typical cause:

- grouped expressions and select expressions were compared after traveling
  through different validator rewrite paths
- equality relied on raw `SqlNode.equalsDeep`

### Failure mode 6: parser casing exposes a validator bug instead of causing it

Symptoms:

- default parser casing passes
- `UNCHANGED + caseSensitive(false)` fails
- changing the spelling of one leaf changes success unexpectedly

Typical cause:

- parser casing changed whether a tree had to be rewritten, copied, or fully
  qualified
- the validator depended on structural coincidence rather than a stable
  invariant

### Failure mode 7: syntax, validation, and lowering disagree on clause-local
feature shape

Symptoms:

- the parser rejects syntax that validator and converter conceptually support
- validation accepts a construct that rel conversion cannot lower
- a feature works for one trivial expression shape but fails for the first
  composed expression

Typical cause:

- parser, validator, and `SqlToRelConverter` are still modeling different
  versions of the same feature

### Failure mode 8: consumed-column accounting is wrong

Symptoms:

- `PIVOT` groups by a column that should have been consumed by a measure
- adding scalar structure around an aggregate changes the grouping result
- a feature appears to "work" only because the query was accidentally grouped
  more finely than intended

Typical cause:

- the AST layer tracks identifiers naively instead of tracking identifiers
  inside aggregate terms or other semantically consuming contexts

### Failure mode 9: structural helper is mistaken for the support policy

Symptoms:

- a broad AST classifier is treated as proof that a syntax form is supported
- validator rejects forms that structural decomposition still needs to
  understand
- future extensions become harder because structural analysis was narrowed to
  the current policy boundary

Typical cause:

- structural bookkeeping and conformance policy were implemented in the same
  helper instead of separate layers

## Debugging playbook

### Start with tests, not with code

First determine which test class owns the behavior:

- `SqlValidatorTest`
  - validator semantics, row types, ambiguity, grouping legality
- `SqlToRelConverterTest`
  - validator-to-rel boundary
- `RelToSqlConverterTest`
  - SQL regeneration when naming is constrained
- `JdbcTest`, `BabelTest`
  - user-visible metadata and dialect-facing behavior
- `CoreQuidemTest`, `CoreQuidemTest2`
  - end-to-end scripted regressions

### Fast routing table

| Symptom | First code to inspect | Why |
|---|---|---|
| Inner query exposes the wrong field names or wrong duplicate behavior | `SqlValidatorImpl.expandStar(...)`, `SqlValidatorImpl.addToSelectList(...)`, `SqlValidatorImpl.validateSelectList(...)` | These methods build validator-visible output shape. |
| Nested `SELECT *` fails even though no explicit name is being referenced | `SqlValidatorImpl.expandStar(...)`, `DelegatingScope.fullyQualify(...)` | This usually means field identity was lost and Calcite fell back to name lookup. |
| `ORDER BY x` behaves differently from `SELECT x` | `OrderByScope`, `SqlValidatorImpl.OrderExpressionExpander` | `ORDER BY` has its own explicit resolution path. |
| `GROUP BY alias` works under default casing but fails under `UNCHANGED + caseSensitive(false)` | `GroupByScope`, `SqlValidatorImpl` group-by expansion, `AggregatingSelectScope`, `AggChecker`, `TypeCoercionImpl.caseWhenCoercion(...)` | This often means grouped-expression comparison depends on inconsistent internal trees. |
| `PIVOT` accepts a measure during validation but fails or lowers incorrectly in `sql2rel` | `SqlValidatorImpl.validatePivot(...)`, `SqlPivot`, `SqlToRelConverter.convertPivot(...)` | Clause-local features need parser, AST, validator, and lowering to agree on the same semantic shape. |
| Changing a `PIVOT` measure from `SUM(x)` to `SUM(x) / SUM(y)` changes the implicit grouping unexpectedly | `SqlPivot.usedColumnNames(...)`, `SqlPivot` aggregate-term helpers, `SqlToRelConverter.convertPivot(...)` | This usually means Calcite is not tracking which input columns are consumed by the measure correctly. |
| Validator behavior is right but the rel plan points at the wrong field | `SqlToRelConverter.convertIdentifier(...)` | This is where validated field identity becomes rel ordinals. |

### Classify the bug before editing

Ask these questions explicitly:

1. Is the validator-visible row type wrong?
2. Is the row type right, but lost through propagation?
3. Is propagation right, but explicit lookup wrong?
4. Is grouped-expression comparison using the wrong equivalence rule?
5. Is the validator correct, but the rel boundary misusing that information?

If you do not classify the bug first, you will usually patch the wrong layer.

### Trace in this order

For name-resolution bugs:

1. validator row type
2. namespace row type exposed outward
3. scope lookup for the identifier
4. `ORDER BY` / alias-specific resolution
5. rel conversion only after validator semantics are understood

For grouped-expression bugs:

1. parsed expression text under the configured casing policy
2. `GROUP BY` alias expansion result
3. fully-qualified identifier form
4. type/coercion-rewritten form
5. grouped-expression comparison site

For clause-local features such as `PIVOT`:

1. exact parser admission rule
2. clause-local AST shape
3. validator legality rule
4. output row-type and implicit-grouping derivation
5. `SqlToRelConverter` lowering shape
6. rel helper usage only after the first five are understood

### Structural probes to use

Prefer comparisons that isolate one layer at a time:

- explicit lookup vs `SELECT *`
- nested `SELECT *` vs flat query
- `GROUP BY alias` vs `GROUP BY <same expression>`
- default parser casing vs case-preserving parser casing
- implicit `NULL` branches vs explicit typed `NULL`

These probes are more valuable than adding random surface-SQL complexity.

### Decide the correct layer for the fix

Use these heuristics:

- if visible output shape is wrong, fix validator representation
- if nested `*` loses field identity, fix propagation
- if explicit lookup resolves when it should fail, fix scopes or explicit
  lookup logic
- if grouping legality depends on rewrite history, fix grouped-expression
  comparison
- if a clause-local feature has the wrong semantic shape, fix the layer that
  owns that part of the shape: parser admission, AST bookkeeping, validator
  legality, or lowering
- only if validator semantics are already correct should you touch
  `SqlToRelConverter`

### Be suspicious of small fixes

Tempting but usually wrong approaches:

- special-casing one query shape
- preserving old suffixes just because tests expect them
- teaching `ORDER BY` to pick a first match
- changing parser defaults to hide a grouped-expression bug
- forcing two validator paths to share one mutable `SqlNode` tree
- relaxing clause-local validation without updating lowering
- teaching a generic rel helper SQL-semantics it should not own

These often make one test pass while preserving the wrong model.

## How to use this guide in future sessions

When a bug involves validation or name resolution:

1. Read this guide first.
2. Write down the intended invariant before changing code.
3. Classify the failure as representation, propagation, lookup, grouped
   equivalence, or rel-boundary.
4. Extend the smallest relevant test class first.
5. Fix the owning layer, not the first place where the wrong behavior becomes
   visible.

## Knowledge-maintenance workflow

This guide is the durable subsystem note.

Workflow and branch-hygiene rules live in `AGENTS.md`.

When a session produces reusable knowledge, update this guide only with:

- enduring invariants
- architectural boundaries
- reusable debugging strategy

Put issue-specific details in a separate archive note and update the notes
index so the archive is discoverable.
