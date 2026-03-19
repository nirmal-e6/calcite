# Session Archive: Duplicate Output Names Through `SELECT *`

## Why this archive exists

This note captures one concrete debugging session so that future work can reuse
the lessons without polluting the core validator note with issue-specific
history.

The bug was not just "one missing ambiguity check". It exposed a deeper design
question:

- should duplicate output names be preserved in validator row types?
- should `SELECT *` be allowed over those duplicates?
- where should ambiguity actually be enforced?

The final answer was yes, yes, and "during explicit lookup".

## Problem statement in plain English

Calcite already rejected this query as ambiguous:

```sql
select deptno from (select deptno, deptno from dept)
```

But it did not reject this one:

```sql
select deptno from (select deptno, * from dept)
```

Calcite also rejected some legal star projections such as:

```sql
select * from (select ename as num, deptno as num from emp)
```

and nested forms such as:

```sql
select * from (select * from (select num, num from table t))
```

The intended semantics were:

- duplicate output names may exist in a subquery result
- `SELECT *` over that result should be legal
- explicit references to a duplicated visible name should fail as ambiguous

During investigation it became clear that the same issue also applied to
multi-star cases such as:

```sql
select deptno
from (
  select e.*, d.*
  from emp e join dept d on e.deptno = d.deptno
)
```

Baseline Calcite accepted that query, but it should fail as ambiguous.

## Root cause

The root cause was a combination of three problems.

### 1. Star expansion was not preserving the correct validator model

For some star-expansion paths, Calcite was uniquifying names too early in the
validator. That meant the visible output row type of a subquery could differ
depending on whether duplicates came from explicit select items or from `*`.

### 2. Star-expanded duplicate fields lost exact source identity

When `SELECT *` expanded fields from a row type that already contained duplicate
names, Calcite rebuilt them as ordinary identifiers. Later stages then
re-resolved them by name, which made nested `SELECT *` behave as if it were
doing explicit lookup.

That is why legal nested `SELECT *` queries failed as ambiguous.

### 3. Explicit ambiguity checks were inconsistent across code paths

`ORDER BY` and some alias-resolution paths did not always reject duplicate
visible names. In some places Calcite would behave as if "first match wins".

## Structural fix

The fix followed the architecture described in
`notes/sql-validation/README.md`.

In short:

- validator-visible row types preserve duplicate output names,
- star expansion preserves source-field identity through nesting,
- explicit references are rejected as ambiguous when the visible name is not
  unique,
- and `SqlToRelConverter` still remains free to use internal unique rel-field
  names such as `NUM0`.

## Code areas involved

The main implementation sat in:

- `SqlValidatorImpl`
  - owns star expansion, select-list output shape, identifier typing, and
    `ORDER BY` expansion
- `DelegatingScope`
  - owns identifier qualification in ordinary scope lookup
- `OrderByScope`
  - owns `ORDER BY`-specific explicit lookup behavior
- `SqlToRelConverter`
  - consumes validated field identity and maps it to rel ordinals

For the architectural background of those layers, use the core subsystem note.

## Why the temporary narrower fix was not enough

At one point the patch was intentionally narrowed so that duplicates were
preserved through nested subqueries but collisions from `e.*, d.*` were still
uniquified.

That version was smaller in compatibility impact, but it was still
architecturally inconsistent because:

- it allowed duplicate names from one source row type,
- but silently rewrote duplicate names created by combining multiple star
  sources,
- which meant outer explicit references could still succeed incorrectly

The stronger example was:

```sql
select deptno
from (
  select e.*, d.*
  from emp e join dept d on e.deptno = d.deptno
)
```

If the inner row type is truly allowed to contain duplicate `DEPTNO` fields,
that outer reference must be ambiguous. Keeping `DEPTNO0` in just that path
would preserve an old workaround, not a coherent invariant.

The final solution therefore keeps the broader rule:

- validator-visible duplicate names are preserved consistently
- explicit name lookup remains the place where ambiguity is enforced

## Test strategy and why the downstream test churn was acceptable

The session added focused validator tests first, then converter tests, then
downstream regression updates.

### Core tests

- explicit duplicate names are ambiguous
- `expr, *` duplicates are ambiguous
- `SELECT *` over duplicate-output subqueries succeeds
- nested `SELECT *` over duplicate-output subqueries succeeds
- `SELECT *` over `e.*, d.*` succeeds
- outer `select deptno` and `order by deptno` over `e.*, d.*` are ambiguous

### Downstream tests

JDBC, Babel, and Quidem expectations changed because visible output labels now
preserve duplicates where Calcite previously synthesized suffixes such as
`DEPTNO0`.

Those are real semantic consequences of the validator model change.

Some non-validator tests were also rewritten slightly so they continued to test
planner or rel-to-sql logic rather than failing for newly-correct ambiguity
reasons.

## Quidem row-order lesson

The Quidem resource updates included some row-order changes for queries without
`ORDER BY`.

Important lesson:

- do not assume such reorderings are always just noise
- do not assume they are always caused by the same layer either

In this session, one representative `outer.iq` case was checked in detail:

- raw execution on a plain connection preserved the old order
- the exact Quidem `!use post` connection path returned the new order

So the generated Quidem transcript reflected real behavior in that execution
context. That means row-order-only diffs in Quidem should not be "cleaned up" by
hand unless the actual Quidem execution path is also changed.

## Lessons to carry forward

1. Duplicate output names are a representation question first, not an ambiguity
   question first.
2. `SELECT *` must be treated as positional propagation, not as repeated
   name-based lookup.
3. Explicit ambiguity belongs in scopes and explicit resolution paths, including
   `ORDER BY`.
4. If a bug appears only in nested queries, inspect whether field identity was
   lost during propagation.
5. If a narrower fix preserves one old naming path such as `DEPTNO0`, test
   whether it is still allowing an explicit outer reference that should now be
   ambiguous.
