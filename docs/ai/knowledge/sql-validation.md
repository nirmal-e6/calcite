# SQL Validation And Name Resolution

This is the reviewed subsystem guide for validator-facing name resolution,
grouping legality, and nearby clause-local boundaries.

## Core Model

- Keep representation, propagation, explicit lookup, and grouped-expression
  equivalence separate.
- Validator-visible row types should preserve SQL-visible output shape as
  closely as possible, including duplicate output names when they are the real
  SQL-visible result.
- `SELECT *` is positional propagation, not repeated explicit lookup.
- Explicit identifier resolution must bind to one unique visible field or fail
  as ambiguous.
- Grouped-expression legality is its own comparison problem; it is not the
  same as ordinary name lookup.
- `SqlToRelConverter` consumes validator semantics; rel-layer field naming does
  not define SQL semantics.

## Durable Invariants

- Preserve SQL-visible output names in validator row types.
- Enforce ambiguity during explicit lookup, not by rewriting validator-visible
  output names early.
- Preserve field identity through propagation so nested `SELECT *` does not
  fall back to name lookup.
- Treat rel-level uniquification as a later internal concern.
- Keep grouped-expression comparison narrow and semantics-driven; do not make
  it depend on parser spelling or shared mutable tree history.
- For clause-local features such as `PIVOT`, keep parser admission, AST
  bookkeeping, validator legality, row-type derivation, and `sql2rel`
  lowering aligned on the same semantic model.

## Architectural Boundaries

- Representation: validator row types and namespaces define the SQL-visible
  shape.
- Propagation: star expansion and outward namespace exposure preserve that
  shape and field identity through nesting.
- Lookup and ambiguity: scopes own explicit resolution semantics, including
  `ORDER BY`-specific lookup behavior.
- Grouped-expression equivalence: validator comparison logic decides whether a
  grouped `SELECT` expression is semantically the same grouped expression.
- Clause-local front ends: parser, clause AST, validator, and `sql2rel` each
  own a distinct part of the feature contract.

## Common Failure Modes

- Early uniquification hides duplicate output names that should still be
  visible in validator row types.
- Lost field identity during `*` makes nested `SELECT *` behave like explicit
  lookup.
- Ambiguity is enforced in the wrong layer, or one explicit-resolution path
  behaves as "first match wins".
- Rel-layer naming behavior leaks back into validator semantics.
- Grouped-expression comparison treats inconsistent internal rewrites as if
  they were semantic differences.
- Parser casing appears to cause the bug, but is only exposing a deeper
  representation problem.
- Clause-local syntax, legality, and lowering disagree on the feature's real
  shape.
- Consumed-column bookkeeping is too literal, so implicit grouping becomes
  silently wrong.

## Debugging Playbook

- Start with the closest owning tests:
  `SqlValidatorTest` for validator semantics,
  `SqlToRelConverterTest` for the validator-to-rel boundary,
  parser tests for syntax admission,
  planner or end-to-end tests only after the semantic owner is clear.
- Classify the bug before editing:
  row-type representation, propagation, explicit lookup, grouped-expression
  comparison, clause-local semantics, or rel-boundary misuse.
- Trace name-resolution bugs in this order:
  validator row type, outward namespace row type, scope lookup,
  `ORDER BY` or alias-specific resolution, then rel conversion.
- Trace grouped-expression bugs in this order:
  parsed expression text, alias expansion, qualified identifier form,
  type/coercion rewrite, grouped-expression comparison site.
- Trace clause-local feature bugs in this order:
  parser admission, clause AST shape, validator legality, row-type and
  implicit-grouping derivation, `sql2rel` lowering, then rel helper usage.
- Prefer structural probes that isolate layers:
  explicit lookup vs `SELECT *`,
  nested `SELECT *` vs flat query,
  `GROUP BY alias` vs direct `GROUP BY <same expression>`,
  default parser casing vs case-preserving parser casing,
  implicit `NULL` vs explicit typed `NULL`.

## Fix-Placement Heuristics

- If visible output shape is wrong, fix validator representation.
- If nested `*` loses field identity, fix propagation.
- If explicit lookup resolves when it should fail, fix scopes or explicit
  lookup logic.
- If grouping legality depends on rewrite history, fix grouped-expression
  comparison.
- If a clause-local feature has the wrong semantic shape, fix the layer that
  owns that part of the shape.
- Touch `SqlToRelConverter` only after validator semantics are already correct.

## Anti-Patterns

- Special-casing one query shape instead of fixing the owning invariant.
- Preserving old suffixes or old names only because tests currently expect
  them.
- Teaching `ORDER BY` or another explicit-resolution path to pick a first
  match.
- Changing parser defaults to hide a grouped-expression bug.
- Forcing validator paths to share one mutable `SqlNode` tree to preserve
  accidental success.
- Relaxing clause-local validation without updating lowering.
- Teaching a generic rel helper SQL semantics it should not own.
