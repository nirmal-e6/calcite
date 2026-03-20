# Session Archive: `PIVOT` Measure Expressions and Front-End Layering

## Why this archive exists

This note records one concrete session around richer `PIVOT` support so that
future work can reuse the architectural lessons without polluting the core
subsystem guide with issue-specific history.

The visible problem looked simple:

- Calcite allowed `SUM(x)` in `PIVOT`
- but rejected `SUM(x) / SUM(y)` and similar expressions

The real issue was deeper:

- parser syntax, clause AST shape, validator rules, and `sql2rel` lowering were
  all modeling `PIVOT` measures as if they were one top-level aggregate call

That is a front-end layering problem, not just a validator bug.

## Problem statement in plain English

The target was to support richer `PIVOT` measures such as:

```sql
PIVOT (
  SUM(dus) AS dus,
  SUM(retail_value) AS retail_value,
  TRY_DIVIDE(SUM(dus), SUM(dus_total)) * 100 AS dus_perc,
  COALESCE(SUM(x), 0) AS x0,
  1 AS bucket_marker
  FOR shop_group IN ('A', 'B')
)
```

The key design questions were:

1. What should count as a valid `PIVOT` measure expression?
2. Where should that rule be enforced?
3. How should a valid richer measure be lowered to relational algebra?
4. Should Calcite copy Databricks runtime behavior for empty buckets?

The final session answer was:

- support scalar expressions over aggregate terms
- also support input-independent scalar measures such as `1`
- keep ordinary filtered-aggregate semantics for empty buckets
- do not copy Databricks' observed "empty bucket becomes `NULL`" behavior
  without stronger evidence that it is intentional and normative

## Current Calcite model before the fix

Before the session fix, Calcite effectively modeled a `PIVOT` measure as one
aggregate call across several layers.

### Parser

`Parser.jj` parsed the `PIVOT` measure list using a function-call-shaped rule.

That meant arithmetic, `CASE`, `COALESCE`, and constants were blocked at syntax
admission time even before validation.

Role:

- `Parser.jj`
  - owns SQL syntax admission and parse-tree construction

### SQL tree

`SqlPivot` already represented the clause as:

- input query
- measure list
- axis list
- `IN` list

But its bookkeeping had been written assuming measures were simple aggregate
calls rather than larger expressions built from aggregate terms.

Role:

- `SqlPivot`
  - the parse-tree node that stores the structure of a validated `PIVOT`

### Validator

`SqlValidatorImpl.validatePivot(...)` enforced a "pure aggregate only" rule.

That meant even if a richer measure was parsable, validator still rejected it
unless the outermost node was an aggregate.

Roles:

- `SqlValidatorImpl`
  - the main validator and the owner of clause-local semantic legality
- `PivotScope`
  - the scope used to resolve names inside the `PIVOT` clause
- `PivotNamespace`
  - the namespace that exposes the validated `PIVOT` row type

### `sql2rel`

`SqlToRelConverter.convertPivot(...)` and `RelBuilder.pivot(...)` assumed:

- one measure maps to one `AggregateCall`

That shape can lower `SUM(x)` or `AVG(x)`, but not
`SUM(x) / SUM(y)` unless the converter first decomposes the measure into base
aggregate terms and a later scalar expression.

Roles:

- `SqlToRelConverter`
  - lowers validated SQL semantics into rel algebra
- `RelBuilder.pivot(...)`
  - a generic filtered-aggregate pivot helper used by the SQL front end

## Root cause

The root cause was a deeper architectural mismatch, expressed in three places.

### 1. Syntax was narrower than the intended semantic feature

If parser admits only aggregate-call-shaped measures, validator cannot even see
the richer expression.

### 2. Validator and converter were not modeling the same feature

Relaxing validator alone would still have been wrong because `sql2rel` was
still built around "one measure = one aggregate call".

### 3. Clause bookkeeping was too literal about identifiers

`PIVOT` derives implicit group keys by subtracting:

- axis columns
- measure-consumed columns

from the input columns.

Once a measure becomes a larger expression, Calcite needs to know which input
columns are consumed inside aggregate terms, not just which identifiers appear
somewhere in the syntax tree.

## Exact semantic model chosen in the session

The final rule for `allowPivotAggregateExpression()` was:

- a measure may be a scalar expression over aggregate terms
- or an input-independent scalar expression
- every input-column reference must appear under an aggregate term
- input-independent means "no input-column references at all"

Examples that should be valid:

- `SUM(x) / SUM(y)`
- `COALESCE(SUM(x), 0)`
- `CASE WHEN SUM(x) = 0 THEN NULL ELSE SUM(y) / SUM(x) END`
- `SUM(x) + 1`
- `1`
- `COALESCE(1, 0)`

Examples that should remain invalid:

- `x`
- `SUM(x) + y`
- `CASE WHEN y = 0 THEN 1 ELSE SUM(x) END`
- `SUM(SUM(x))`
- `SUM(x) OVER (...)`
- scalar subqueries

Deferred for this patch:

- aggregate `FILTER`
- `WITHIN GROUP`
- `WITHIN DISTINCT`
- `IGNORE NULLS` / `RESPECT NULLS`

Those forms matter structurally, but they were not included in the supported
surface for this session's feature patch.

## Structural fix

The structural fix aligned parser admission, clause bookkeeping, validator
legality, and `sql2rel` lowering.

### Conformance

The feature was gated by a neutral conformance hook:

- `SqlConformance.allowPivotAggregateExpression()`

This keeps richer `PIVOT` measure syntax out of default Calcite behavior while
making the feature reusable by dialect-specific conformances later.

Roles:

- `SqlConformance`
  - the public interface for parser/validator SQL-conformance behavior
- `SqlAbstractConformance`
  - the default implementation base
- `SqlDelegatingConformance`
  - the wrapper used to define custom conformance in tests or downstream code
- `SqlConformanceEnum`
  - Calcite's built-in conformance presets

## Conformance-design lesson

One reusable lesson from the session is that conformance design has to respect
layer boundaries too.

Good uses of conformance flags:

- syntax admission
- validator legality
- dialect-visible semantic choices that are intentionally different

Bad use:

- pretending a flag alone implements a feature whose converter still models the
  old semantic shape

For this `PIVOT` work, the correct split was:

- conformance decides whether richer measure expressions are admitted and
  accepted
- `SqlToRelConverter` still has to implement the lowering implied by that
  choice

So the rule is:

- use conformance to gate behavior
- do not use conformance as a substitute for missing structural lowering

### Parser admission

`Parser.jj` was changed so that, under
`allowPivotAggregateExpression()`, `PIVOT` measures parse as full non-query
expressions instead of only function-call-shaped syntax.

Why parser had to change:

- validator cannot legalize syntax that never parses

Why parser alone was not enough:

- parser still does not know whether an expression is semantically legal in a
  `PIVOT` measure

### Structural clause bookkeeping

`SqlPivot` gained structural helpers that are intentionally separate from
support policy.

Those helpers answer questions such as:

- is this subtree an aggregate term?
- which input columns are consumed by aggregate terms?
- which aggregate terms need to be extracted from a larger measure expression?

This logic exists so that:

- implicit grouping remains correct
- the converter can split aggregate work from post-aggregate scalar work

Important lesson:

- a structural aggregate-term classifier can be broader than the currently
  supported public feature surface
- validator still owns which forms are legal under current conformance

### Validator rule

`SqlValidatorImpl.validatePivot(...)` became the semantic owner of richer
measure legality.

It now distinguishes:

- valid aggregate terms
- valid input-independent scalar expressions
- invalid naked input-column references
- nested aggregates
- windowed aggregates
- subqueries
- deferred wrapper forms that are structurally understandable but currently
  unsupported

This is the correct layer because:

- legality is a semantic rule, not a parsing trick
- output row types are validator-owned
- the parser cannot decide whether an expression is input-independent in the
  semantic sense that matters here

### `sql2rel` lowering

`SqlToRelConverter.convertPivot(...)` was changed to lower richer measures in
two stages:

1. extract base aggregate terms from each measure
2. lower those terms through the existing filtered-aggregate pivot rewrite
3. add a final projection that rebuilds the original scalar measure expression

This was the critical architectural step.

Without it, validator would accept syntax that the converter still could not
represent.

Important lesson:

- if a clause-local feature grows from "one aggregate call" to "scalar over
  aggregate terms", the converter must usually decompose and reconstruct

## Why constants were eventually supported

There was an intermediate design where Calcite allowed only expressions that
contained at least one aggregate term.

That looked safe at first, but it turned out to be too narrow.

Under the filtered-aggregate interpretation of `PIVOT`, an input-independent
scalar measure such as `1` has a coherent meaning:

- evaluate the scalar once per output group
- repeat that value for each pivot bucket column

That is different from an input-column reference such as `x`, which is not
input-independent and therefore still needs aggregate protection.

So the session broadened the measure rule from:

- "must contain an aggregate term"

to:

- "must either be input-independent or be a scalar expression whose input
  column references are all under aggregate terms"

## Databricks behavior and why the session did not adopt it

Databricks documentation described `PIVOT` in filtered-aggregate terms, but
runtime experiments showed a different behavior for empty buckets:

- `PIVOT (COUNT(*) ...)` produced `NULL` for absent buckets
- a manual `COUNT(*) FILTER (WHERE ...)` produced `0`

The same difference appeared for:

- constants
- `COALESCE(SUM(...), ...)`

This created a documentation/runtime mismatch.

The session's conclusion was:

- treat Databricks' empty-bucket `NULL` behavior as a possible product or
  documentation bug until proven otherwise
- keep Calcite on the coherent filtered-aggregate model instead of copying a
  harder-to-justify runtime quirk

That choice simplified the design materially:

- no extra conformance flag for empty-bucket nulling
- no hidden presence counts
- no final `CASE WHEN bucket_present THEN ... ELSE NULL END`

## Test map for this feature family

When this area breaks again, these are the first tests to inspect or extend.

### `SqlParserTest`

Parser coverage for clause syntax admission.

Use this layer to answer:

- does the grammar admit the richer `PIVOT` measure syntax under the intended
  conformance?

### `SqlValidatorTest`

Validator coverage for semantic legality and row-type consequences.

Use this layer to answer:

- which measure forms are legal?
- which forms are rejected and why?
- did a parser change accidentally widen or narrow the semantic surface?

### `SqlToRelConverterTest`

Plan-shape coverage for lowering.

Use this layer to answer:

- are base aggregate terms extracted correctly?
- is the final scalar expression reconstructed in the right place?
- did a validator relaxation leave `sql2rel` on the old semantic model?

### `pivot.iq` / Quidem execution coverage

End-to-end runtime coverage for user-visible results.

Use this layer to answer:

- does the chosen semantic model survive through execution?
- are filtered-aggregate semantics and bucket behavior reflected in actual
  output, not just in plan shape?

## Classes and methods that mattered

### `Parser.jj`

Calcite's SQL grammar template.

Relevant because `AddPivotAgg` defines what syntax a `PIVOT` measure is allowed
to take.

### `SqlPivot`

The `PIVOT` parse-tree node.

Relevant because it owns clause structure and the structural helpers needed for
aggregate-term extraction and consumed-column bookkeeping.

### `PivotScope`

The scope used while validating expressions inside `PIVOT`.

Relevant because pivot measures and axes must resolve names against the input
query, not against the outer query.

### `PivotNamespace`

The namespace that exposes the validated `PIVOT` row type.

Relevant because the validated `PIVOT` output shape is SQL-visible and must be
owned in validator space.

### `SqlValidatorImpl.validatePivot(...)`

The validator entry point for `PIVOT`.

Relevant because this is where richer measure legality, output typing, and
clause-local semantic checks belong.

### `SqlToRelConverter.convertPivot(...)`

The rel-lowering entry point for `PIVOT`.

Relevant because this is where validated measure expressions must be decomposed
into aggregate work plus final scalar reconstruction.

### `RelBuilder.pivot(...)`

A rel-building helper for pivot-like filtered aggregate rewrites.

Relevant because it is useful infrastructure, but it should not be treated as
the owner of SQL legality rules.

## Debugging lessons to carry forward

1. For clause-local SQL features, always classify the bug by layer first:
   parser admission, AST bookkeeping, validator legality, row-type derivation,
   or `sql2rel` lowering.
2. If validation is relaxed but lowering still assumes the old shape, the fix
   is incomplete by design.
3. Structural classifiers and public support policy are related but different.
4. Incorrect consumed-column bookkeeping can silently turn into wrong implicit
   grouping, which makes the feature look "almost right" while still being
   semantically wrong.
5. Vendor runtime behavior should not be copied blindly when it conflicts with
   documented semantics and a cleaner architectural model.

## Test strategy that proved useful

The session converged fastest when tests were added in this order:

1. parser tests for syntax admission under conformance
2. validator tests for legal and illegal measure forms
3. `SqlToRelConverterTest` plan checks for decomposition and reconstruction
4. end-to-end or external-engine probes only after the internal semantic model
   was clear

This order matters because it separates:

- syntax
- semantic legality
- lowering correctness
- vendor-compatibility questions

instead of mixing them into one large opaque failure.
