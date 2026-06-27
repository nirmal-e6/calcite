# RelMdPredicates Project Expression Handoff

Date: 2026-06-27
Branch: `fix/relmd-project-expression-predicates`
Base: `upstream/main` at `8c1ac3640`
Commit: `2a7ea33ce [CALCITE] Pull up predicates for deterministic project expressions`

## Summary

This branch contains a narrow upstreamable Calcite change in `RelMdPredicates`.
Project predicate metadata now pulls predicates through exact deterministic
projected expressions, not only direct input references.

The motivating pattern is:

```sql
select cast(deptno as bigint) as deptno_big
from emp
where cast(deptno as bigint) > 7
```

Before this change, pulled-up predicates above the project were empty because
`CAST(deptno AS BIGINT)` was not a direct projected input reference. After this
change, Calcite can expose the equivalent predicate:

```text
deptno_big > 7
```

This matters to `JoinPushTransitivePredicatesRule` because predicates inferred
through joins depend on `RelMdPredicates` metadata.

## Root Cause

`RelMdPredicates#getPredicates(Project, RelMetadataQuery)` only maintained an
equivalence map from input refs to projected output refs. It deliberately did
not map projected calls such as `CAST($7):BIGINT` to the output field that
contains the exact same call.

That is sound but incomplete. When an input predicate contains the same
deterministic expression that the project exposes, Calcite can safely pull the
predicate up by replacing that expression with the project output ref.

## Final Invariant

For `Project`, a pulled-up input predicate may be rewritten in terms of a
project output expression when all of these hold:

- the projected expression is deterministic
- the projected expression is a `RexCall`
- the input predicate contains the exact same `RexCall`
- every input ref needed by the predicate is also mappable through the project
- expansion from duplicate aliases remains bounded

The change does not try algebraic equivalence. It only handles exact Rex
expression identity/equality.

## Non-Goals

- No algebraic simplification such as recognizing `a + 1` inside `a + 2`.
- No inference through non-deterministic expressions.
- No changes to join rules, filter rules, type coercion, parser, validator, or
  SQL-to-rel conversion.
- No product-specific flags or E6 planner flow changes.
- No attempt to push the resulting predicate below the project; this branch only
  exposes the predicate in metadata so existing rules can consume it.

## Implementation

Touched files:

- `core/src/main/java/org/apache/calcite/rel/metadata/RelMdPredicates.java`
- `core/src/test/java/org/apache/calcite/test/RelMdPredicatesTest.java`
- `core/src/test/java/org/apache/calcite/test/RelOptRulesTest.java`
- `core/src/test/resources/org/apache/calcite/test/RelOptRulesTest.xml`

Implementation details:

- Builds a separate expression-equivalence map for deterministic projected
  `RexCall`s.
- Keeps existing input-ref equivalence handling unchanged.
- Rewrites pulled-up input predicates by replacing exact projected expressions
  with project output refs.
- Allows duplicate aliases but caps expansion with
  `MAX_PROJECT_EXPRESSION_PREDICATE_EXPANSIONS = 32`.

## Tests Added

`RelMdPredicatesTest#testPullUpPredicatesFromProjectExpression`

- Builds a rel tree with:
  - filter: `CAST(DEPTNO AS BIGINT) > 7`
  - project: `CAST(DEPTNO AS BIGINT)`
- Verifies pulled-up predicates become `[>($0, 7)]`.
- Red check before implementation failed as expected with `was "[]"`.

`RelOptRulesTest#testTransitiveInferenceProjectExpression`

- Uses a left join where the right side projects `CAST(deptno AS BIGINT)`.
- Verifies `JOIN_PUSH_TRANSITIVE_PREDICATES` infers:
  - right-side `LogicalFilter(condition=[>($0, 7)])`
  - above the right project containing `DEPTNO_BIG=[CAST($7):BIGINT NOT NULL]`

## Validation Run

Passed:

```bash
./gradlew -q :core:checkstyleMain :core:checkstyleTest
```

Result: exit code 0.

Passed:

```bash
./gradlew -q :core:test \
  --tests org.apache.calcite.test.RelMdPredicatesTest \
  --tests org.apache.calcite.test.RelOptRulesTest.testTransitiveInferenceProjectExpression
```

Result:

```text
21 completed, 0 failed, 0 skipped
```

Not run yet:

```bash
./gradlew clean build
```

Recommendation: run full clean build before opening an Apache Calcite PR.

## Impact Map

Changed mechanism:

- `BuiltInMetadata.Predicates` implementation for `Project`.

Dependent surfaces:

- Pulled-up predicate metadata.
- Rules that consume pulled-up predicates, especially
  `JoinPushTransitivePredicatesRule`.
- Plan-output XML for affected rule tests.

Preserved invariants:

- Constants projected by a project are still handled as before.
- Direct input-ref projections still use the existing equivalence map.
- Non-deterministic project expressions are ignored.
- Predicates are only emitted when the expression replacement is exact.
- Existing weakening behavior in `projectPredicate` remains unchanged.

Nearby surfaces inspected but intentionally not changed:

- Join predicate inference internals.
- `FilterProjectTransposeRule`.
- `JoinPushTransitivePredicatesRule`.
- Type coercion and cast semantics.
- SQL parser/validator paths.

## Reviewer Risks

Primary risk: predicate expansion can create many equivalent predicates when a
project contains duplicate aliases of the same expression. This is mitigated by
the explicit expansion cap.

Semantic risk is low because the implementation only substitutes exact
deterministic projected expressions with their output refs.

Potential reviewer question: why this belongs in metadata rather than a rule.
Answer: the missing fact is project predicate metadata. Existing rules already
consume `RelMdPredicates`; adding a special-case rule would duplicate the same
semantic contract at a symptom layer.

## Suggested PR Title

```text
[CALCITE-XXXX] Pull up predicates for deterministic project expressions
```

## Suggested PR Body

```markdown
RelMdPredicates currently pulls predicates through Project only when the
predicate references input columns that are projected directly. If a Project
exposes a deterministic expression, and an input predicate contains the same
expression, Calcite does not pull up the equivalent predicate on the projected
output.

For example, a filter below Project such as:

  CAST(DEPTNO AS BIGINT) > 7

is not pulled up through:

  Project(CAST(DEPTNO AS BIGINT) AS DEPTNO_BIG)

as:

  DEPTNO_BIG > 7

This limits rules that depend on pulled-up predicates, such as
JoinPushTransitivePredicatesRule, from inferring predicates across joins whose
keys are projected expressions.

This patch extends Project predicate metadata to map exact deterministic
projected RexCall expressions to their output fields. It keeps the existing
direct input-ref behavior, requires exact expression matches, skips
non-deterministic expressions, and caps expansion when duplicate aliases could
create many equivalent predicates.

Tests cover:
- direct RelMdPredicates behavior for a projected CAST expression
- JoinPushTransitivePredicatesRule inferring a predicate through a projected
  CAST join key
```

## Later-Session Checklist

1. Rebase on latest `upstream/main`.
2. Run `./gradlew clean build`.
3. Replace `[CALCITE-XXXX]` after filing/choosing the JIRA issue.
4. Open PR from `nirmal-e6:fix/relmd-project-expression-predicates` to
   `apache:main`.
