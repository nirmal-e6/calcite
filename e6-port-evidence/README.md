# Planner Port Evidence On Calcite 1.39

This branch records an experiment where planner-owned shaded Calcite changes
from `e6-query-optimizer` were replayed incrementally onto clean Calcite
`1.39.0` and then checked against Calcite's existing unit tests.

The goal was not to prove that every planner change is "wrong" in its native
context. The goal was to answer a narrower question:

- if we replay our shaded Calcite deltas onto a clean Calcite base, do
  Calcite's own tests expose real regressions, hidden dependencies, and
  incomplete backports that our current planner tests do not catch directly?

The answer was yes.

## Branch And Scope

- Base:
  - `calcite-1.39.0` / `repro-1.39`
- Evidence branch:
  - `evidence/group-by-function-alias-ut-proof`
- Planner-side mirror branch:
  - `~/e6-repos/e6-query-optimizer`
  - `evidence/calcite-group-by-regression-evidence`
- Planner-side mirror test:
  - [CalciteGroupByRegressionEvidenceTest.java](/Users/nirmalgovindaraj/e6-repos/e6-query-optimizer/src/test/java/io/e6x/assertplans/CalciteGroupByRegressionEvidenceTest.java)

## Observed Failure Counts

These were the observed `core` failure counts as planner-originated changes
were replayed one by one and checked against Calcite's existing test suite.

- `19c68e02b` `Port planner GROUP BY alias rewrite into validator`
  - `27` failures
- `3cec7b767` `Port planner alias expansion for WHERE and JOIN ON`
  - `35` failures
- `2a750df0d` `Port planner GROUP BY and HAVING expander follow-ups`
  - `37` failures
- `667cd4201` `Port planner AggChecker window-validation changes`
  - `41` failures
- `85946833b` `Port Character padding change from planner`
  - `55` failures
- `aa5764bac` `Port RexSimplify from planner`
  - `84` failures
- `7d521f3c9` `Port within_group fixes from planner`
  - `102` failures

So the experiment evolved from:

- `27 -> 35 -> 37 -> 41 -> 55 -> 84 -> 102`

## Failure Families

### 1. GROUP BY alias rewrite and dropped-group-expression failures

Introduced by:

- `19c68e02b`

Representative failures:

- `SqlValidatorTest.testCoercionCast`
- `SqlValidatorTest.testAggregateInGroupByFails`
- stream/window auxiliary group function tests
- `LatticeSuggesterTest`
- `MaterializedViewRelOptRulesTest.testJoinAggregateMaterializationAggregateFuncs15`

What happened:

- grouped function expressions were rewritten onto select-list alias paths
- later helper logic reduced `AS(expr, alias)` to just the alias identifier
- aggregate/window/group-function expressions were also dropped from rebuilt
  `GROUP BY` lists before normal validator checks ran

Why this was bad:

- some valid grouped-expression shapes were reinterpreted as unresolved column
  lookups such as `ADULT_OR_CHILD`, `BY_YEAR`, `n12`
- some invalid queries such as `GROUP BY sum(empno)` stopped failing and were
  planned anyway
- stream auxiliary functions such as `TUMBLE_END`, `HOP_START`,
  `SESSION_START` lost the matching group-function context they depend on

Planner-side mirror:

- invalid aggregate in `GROUP BY` is accepted and planned
- alias/qualify families degrade into `Column ... not found` rather than the
  correct semantic validator error

### 2. Alias expansion in `WHERE` and `JOIN ON`

Introduced by:

- `3cec7b767`

Representative failures:

- `SqlAdvisorTest` completion failures:
  - `testPartialIdentifier`
  - `testFromWhere`
  - `testWhereList`
  - `testOnCondition`
  - `testSubQuery`

What happened:

- unresolved identifiers in `WHERE` / `JOIN ON` started getting rewritten
  through select-list aliases instead of staying as unresolved names in normal
  scope analysis

Why this was bad:

- editor/completion scope could no longer see the same table/column candidates
- incomplete SQL started going through alias rewrite logic that was never meant
  for completion/hint generation

### 3. GROUP BY / HAVING expander follow-ups

Introduced by:

- `2a750df0d`

Representative failures:

- `SqlValidatorTest.testQualifyNegative`
- `SqlValidatorTest.testAliasInGroupBy`

What happened:

- later alias-expansion follow-ups pushed more semantic misuse cases into plain
  identifier lookup errors

Why this was bad:

- instead of failing for the correct reason, Calcite started producing
  `Column 'SUMDEPTNO' not found` or `Column 'C' not found` for queries that
  should have failed with a semantic aggregate/window validator error

### 4. Window/grouped validation relaxations

Introduced by:

- `667cd4201`

Representative failures:

- grouped-window validation family
- `testAggregateFunctionInOver`
- `testWindowClause`
- related `OVER(...)` validator cases

What happened:

- `AggChecker` / grouped-window validation behavior changed

Why this was bad:

- grouped/windowed queries that should have failed started validating
- some earlier grouped-window invariants were weakened too broadly

Planner-side mirror:

- representative nested-aggregate/window family reproduced as a planner crash
  (`RexInputRef index 1 out of range 0..0`)

### 5. Fixed-width `CHAR` padding / normalization regressions

Introduced by:

- `85946833b`

Representative failures:

- `JdbcTest.testTrimLiteral`
- `JdbcTest.testValuesCompositeRenamed`
- `PlannerTest.testView`
- `RelToSqlConverterTest.testValues`
- `CoreQuidem` diffs in `pivot.iq`, `outer.iq`, `join.iq`, `cast.iq`

What happened:

- `SqlToRelConverter.convertLiteral(...)` stopped normalizing fixed-width
  `CHAR(n)` values in typed `VALUES` contexts

Why this was bad:

- some failures were plan/canonicalization diffs
- but at least one was a real behavioral regression:
  - clean Calcite returns row `X=1; Y=a  ` for
    `with t(x, y) as (values (1, 'a'), (2, 'abc')) select * from t where y = 'a'`
  - the patched branch returned no row

Important planner caveat:

- this family did not transfer 1:1 into the planner harness because the tested
  planner path derived `VARCHAR(3)` rather than fixed-width `CHAR(3)` for the
  comparable `VALUES` case
- the Calcite-side bug is still real, but it is not the best planner-side
  mirror

### 6. `RexSimplify` cast/literal canonicalization regressions

Introduced by:

- `aa5764bac`

Representative failures:

- `RexProgramTest.testSimplifyCastLiteral2`
- `RexProgramTest.testSimplifyCastLiteral3`
- `TypeCoercionConverterTest.testBuiltinFunctionCoercion`
- `TypeCoercionConverterTest.testInsertUnionQuerySourceCoercion`
- `TypeCoercionConverterTest.testInsertValuesQuerySourceCoercion`
- `RelOptRulesTest.testCastRemove`
- `RelOptRulesTest.testUnionToValuesByInList*`
- `RelToSqlConverterTest.testSelectWhereIn2`
- `RelToSqlConverterTest.testSelectWhereIn3`
- `CalciteSqlOperatorTest` and `SqlOperatorUnparseTest` `CAST` / `SAFE_CAST` / `TRY_CAST`

What happened:

- numeric literal casts stopped simplifying to Calcite's canonical literal form
- generic coercion/cast paths started retaining explicit `CAST(...)`
- boolean-to-character casting was changed to lowercase `true` / `false`

Why this was bad:

- many rule, rel2sql, and type-coercion tests expect canonical literals rather
  than residual cast wrappers
- a large cross-cutting family of plan and unparse regressions appeared

Expected vs problematic in this family:

- expected / intentional:
  - lowercase `true` / `false` for boolean-to-character casting was explicitly
    added for Databricks semantics
  - if we port that behavior ungated into pure Calcite, these diffs are
    expected
- problematic:
  - the broader numeric-cast and type-coercion fallout was not just a formatting
    change; it altered canonicalization assumptions across simplification,
    rel2sql, and rule tests

Planner-side mirror:

- planner now shows:
  - retained `CAST(1):VARCHAR(...)` instead of a canonical string literal
  - retained coercion cast in `1 || 'a'`
  - lowercase `_UTF-8'true'` in a plan for nested boolean-to-char casts

### 7. `WITHIN_GROUP` / `OVER` mixed-version dependency regressions

Introduced by:

- `7d521f3c9`

Representative failures:

- `JdbcTest.testWinAgg`
- `JdbcTest.testWinAgg2`
- `JdbcTest.testSumOverPossiblyEmptyWindow`
- `JdbcTest.testWinAggScalarNonNullPhysType`
- `SqlToRelConverterTest.testOverAvg`
- `SqlToRelConverterTest.testOverAvg2`
- `SqlToRelConverterTest.testSelectOverDistinct`
- `SqlHintsConverterTest.testWindowHints`
- `CoreQuidem sql/winagg.iq`

What happened:

- the visible `WITHIN_GROUP` logic was ported on top of a later upstream
  `SqlOverOperator` / `SqlOperatorBinding` semantic contract
- specifically, this replay also pulled in the newer `hasEmptyGroup()` style of
  `OVER(...)` type inference from upstream `CALCITE-7134`

Why this was bad:

- ordinary window aggregate return-type/nullability behavior changed
- Calcite's datatype-preservation assertions started failing across many
  non-`WITHIN_GROUP` queries

This is the most important subtlety in the experiment:

- this was not just "the WITHIN_GROUP fix is bad"
- it was a hidden prerequisite-stack problem
- a later Calcite semantic change was mixed into a `1.39` replay without the
  rest of its supporting upstream stack

Planner-side mirror:

- the exact Calcite type-drift repro did not transfer cleanly
- but the same named-window family now fails harder in planner with:
  - `SqlIdentifier cannot be cast to class org.apache.calcite.sql.SqlWindow`

## What We Did Wrong

This experiment exposed several categories of mistakes in how the replay was
done.

### 1. We treated shaded deltas as isolated patches

We often took the local code we saw in planner and replayed it as though it
were a self-contained change.

That was not always true.

Some changes were:

- genuine planner-local policy
- later Calcite backports
- partial copies of a broader upstream semantic stack
- local fixes that already relied on later companion fixes in planner

### 2. We mixed planner-local behavior with later upstream Calcite evolution

The clearest example is the `WITHIN_GROUP` / `OVER` family:

- `1.39` still used `getGroupCount()` in `SqlOverOperator`
- newer Calcite `main` uses `hasEmptyGroup()`
- our planner already carries the newer shape

So replaying only the visible local `WITHIN_GROUP` logic onto `1.39` while also
bringing along the later `hasEmptyGroup()` contract created failures that were
really dependency-stack failures.

### 3. We ungated dialect-specific behavior in pure Calcite

For example:

- lowercase `true` / `false` in nested boolean-to-char casts

That is useful evidence of divergence, but it is not the same as proving an
unintended bug. If a change is intentionally dialect-specific, replaying it
ungated in pure Calcite will create expected diffs.

### 4. We relied on manual copy/paste archaeology instead of provenance

The experiment repeatedly showed that:

- one local file diff was often not enough
- a change depended on earlier commits, later follow-ups, or broader upstream
  context

Git history would have made those dependencies much more visible than a shaded
copy in a product repo.

## What This Experiment Proves

### 1. Calcite's existing tests are valuable for our shaded changes

They caught:

- real planner-visible semantic bugs
- incorrect validator behavior
- wrong-result risks
- canonicalization regressions
- hidden prerequisite-stack problems

### 2. Planner end-to-end plan tests are not enough by themselves

Planner-side tests were still useful, and we mirrored several representative
families there, but they did not expose everything:

- some issues appeared only in Calcite's lower-level unit tests
- some planner paths already had later compensating fixes
- some families, such as the fixed-width `CHAR` issue, did not transfer 1:1 to
  our planner harness because the type/conformance path was different

### 3. Managed fork workflows are better than opaque copy/shade archaeology

This experiment is strong evidence for a managed fork model because it shows:

- local ownership exists already
- replaying changes without provenance is error-prone
- hidden dependency stacks are common
- later upstream semantic changes can be mixed in accidentally

The problem is not only "we had bugs".

The deeper problem is:

- we do not currently have a clean way to classify which changes are:
  - planner-local policy
  - upstream backports
  - dialect behavior
  - partial semantic stacks

A managed fork with real commit history makes that classification much easier.

## Practical Takeaways

- Use Calcite UTs as part of validating shaded Calcite changes.
- Treat every shaded delta as one of:
  - local policy
  - upstream backport
  - dependency-stack replay
- Do not port main-branch snippets onto `1.39` without checking the rest of the
  prerequisite stack.
- Keep expected dialect-specific diffs separate from genuine unintended
  regressions.
- Prefer Git-backed provenance over manual copy/shade archaeology whenever
  possible.
