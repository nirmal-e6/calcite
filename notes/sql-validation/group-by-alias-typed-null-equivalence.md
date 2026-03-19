# Session Archive: `GROUP BY` Alias Validation and Typed-`NULL` Equivalence

## Why this archive exists

This note captures one concrete debugging session in the validator so future
work can reuse the reasoning without polluting the core subsystem guide.

The bug looked like a parser-casing or case-insensitive lookup problem, but the
real issue was deeper:

- name resolution succeeded
- grouped-expression comparison did not

That distinction matters, because parser changes or schema normalization would
only hide the symptom.

## Problem statement in plain English

Under case-preserving, case-insensitive parser settings, a query like this
could fail:

```sql
SELECT
  CASE
    WHEN true THEN CONCAT('WEEK', '_', e.ename)
    WHEN false THEN e.sal
  END AS group_by_timeperiod
FROM emp e
GROUP BY group_by_timeperiod
```

with:

```text
Expression 'e.ename' is not being grouped
```

even though the `GROUP BY` alias expands to the same logical `CASE`
expression.

The same family also failed when only one leaf already matched schema case:

```sql
SELECT
  CASE
    WHEN true THEN CONCAT('WEEK', '_', e.ENAME)
    WHEN false THEN e.sal
  END AS group_by_timeperiod
FROM emp e
GROUP BY group_by_timeperiod
```

## What was misleading at first

Several surface details looked suspicious but turned out not to be essential:

- `DISTINCT` was not required
- scalar subqueries in the `WHEN` clauses were not required
- direct `GROUP BY <same CASE expression>` already passed

So the failure was not "complex SQL" in general. It was a narrower validator
path involving:

- same-level `GROUP BY alias`
- case-preserving, case-insensitive identifier rewrite
- and a `CASE` whose type information could be represented differently across
  validator paths

## Root cause

The validator compared grouped expressions using raw structural equality
(`SqlNode.equalsDeep`).

For this query family, the same logical `CASE` expression could end up in two
different internal forms:

- on the `GROUP BY` path, validator coercion could physically insert
  `CAST(NULL AS <type>)`
- on the `SELECT` path, the same `NULL` branch could stay as bare `NULL` and
  receive its type only through validator metadata

Case-preserving parsing made this easier to trigger because identifier
qualification rewrote some leaves, which caused the `GROUP BY` alias-expansion
path to stop reusing the same `SqlNode` subtree as the `SELECT` path.

So the true bug class was:

- inconsistent representation across validator paths
- in the grouped-expression equivalence layer

not:

- failed name lookup
- wrong schema casing
- or wrong rel-layer output naming

## Why the default parser path passed

The default parser configuration uppercases unquoted identifiers.

That reduced how much rewriting the validator had to do later. In this query
family, the `GROUP BY` alias-expansion path often kept sharing more of the same
mutable tree that the `SELECT` side later used.

Then type coercion on one path effectively mutated the shared tree, and the
later structural comparison happened to succeed.

That success was accidental. It depended on shared rewrite history, not on a
stable semantic rule.

## Exact architectural layer

The correct layer for the fix was grouped-expression comparison in the
validator.

Why:

- parser casing owns identifier text representation, not grouping legality
- schema normalization owns lookup semantics, not expression equivalence
- rel conversion is downstream and too late to rescue validator rejection

So the fix belonged where Calcite answers:

- "Is this `SELECT` expression one of the grouped expressions?"

## Code areas involved

### `SqlValidatorImpl`

The main validator.

Relevant because it owns:

- `GROUP BY` validation
- alias expansion
- expression expansion
- select-list validation order

### `GroupByScope`

The scope used while validating `GROUP BY`.

Relevant because this is where `GROUP BY alias` expansion begins.

### `DelegatingScope`

The common qualification scope.

Relevant because it can rewrite parsed `ename` into schema field `ENAME` during
case-insensitive lookup.

### `TypeCoercionImpl`

Calcite's implicit-coercion implementation.

Relevant because `CASE` branch coercion can materialize `CAST(NULL AS <type>)`
in one validator path.

### `SqlCaseOperator`

The operator that derives `CASE` types.

Relevant because it can also assign types to `NULL` branches through validator
metadata without necessarily materializing a cast node.

### `AggregatingSelectScope`

The scope used when validating expressions in an aggregate query.

Relevant because it checks grouped-expression legality later, against the
collected grouped-expression set.

### `AggChecker`

The aggregate-validation visitor.

Relevant because it is where the final "expression is not being grouped" error
was raised.

### `SqlValidatorUtil`

Shared validator helper code.

Relevant because this was the right place to centralize the grouping
equivalence rule instead of duplicating ad hoc comparisons.

## Structural fix

The fix introduced a narrow grouped-expression normalization rule in the
validator comparison layer:

- strip `AS`
- treat `CAST(NULL AS <type>)` and bare `NULL` as equivalent
- keep all other structure significant

This was intentionally not a general canonicalizer for all SQL expressions.

It should be viewed as:

- the first justified rule in grouped-expression normalization

not as:

- permission to ignore arbitrary casts
- permission to broaden equivalence without a reproducer

## Why smaller or different fixes were wrong

Wrong alternatives included:

- changing parser casing defaults
- switching to case-sensitive lookup
- normalizing schema storage case
- forcing both validator paths to share the same mutable tree
- ignoring all casts in grouped-expression comparison

Those either changed semantics in the wrong layer or encoded the current
accidental success mechanism as if it were a design rule.

## Boundary findings that matter for future debugging

These were verified while investigating the bug family:

- `DISTINCT` is not required
- scalar subqueries in the `WHEN` clauses are not required
- direct `GROUP BY <case-expression>` already passed before the fix
- non-null branch coercion alone was not reproduced as a failure
- `NULLIF(...)` was not reproduced as a failure
- `COALESCE(...)`, including its internal `CAST NOT NULL` rewrite, was not
  reproduced as a failure

So the currently proven failing family remains narrow:

- same-level `GROUP BY alias`
- case-preserving, case-insensitive identifier rewrite that causes divergence
- typed-`NULL` representation mismatch within `CASE`

This matters because it argues for a narrow normalization rule, not a broad
theory of "ignore structural differences".

## Reproducer and control strategy

Future sessions should keep this probing pattern in mind:

1. Start with the smallest failing `GROUP BY alias` query.
2. Remove `DISTINCT`.
3. Remove subqueries and other incidental SQL.
4. Compare `GROUP BY alias` with direct `GROUP BY <same expression>`.
5. Compare implicit `NULL` with explicit typed `NULL`.
6. Compare default parser casing with case-preserving parser casing.

If only the alias form fails, the comparison layer is usually more suspicious
than parsing or lookup.

## Lessons to carry forward

1. Name resolution can be correct while grouped-expression comparison is wrong.
2. Parser casing often exposes representation bugs instead of causing them.
3. Success that depends on shared mutable `SqlNode` history is not a valid
   semantic invariant.
4. Grouped-expression equivalence belongs in the validator comparison layer.
5. Do not broaden normalization rules without a pre-fix failing reproducer for
   the new case.
