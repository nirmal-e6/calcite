# Apache Calcite Agent Contract

Keep this file short and operational. Keep reusable procedure details in
`SKILL.md` and `docs/ai/*`, not here.

## Repo Expectations

- Derive Calcite behavior from current code, tests, and docs in this repo, not
  memory.
- Prefer the smallest robust upstreamable fix. Do not ship symptom patches or
  query-shape-specific hacks when the invariant belongs elsewhere.
- Follow nearby Calcite comment style. Add comments for invariants, rationale,
  and non-obvious behavior; do not add comments that merely narrate the code.
- Any workflow-layer change must leave the workflow layer internally
  consistent.
- Search for nearby tests first and follow existing Calcite test idioms,
  fixtures, and golden files.
- For large, ambiguous, or multi-step work, keep an ExecPlan current in
  `.agents/PLANS.md` while the work is active, then retire it after durable
  outcomes are routed or discarded.
- Workflow-layer maintenance stays on `config/codex`. Use work branches from
  `main` for non-workflow Calcite tasks.
- For non-trivial upstream work, start from a JIRA issue and use the
  `[CALCITE-####] <summary>` subject for the final upstream commit. Local
  snapshot commits may use simpler local subjects.
- Before calling normal Calcite work fully handoff-ready, end on one clean
  surviving branch and remove temporary exploratory worktrees or side branches
  unless they are explicitly preserved.

## Skill Usage Rules

- Prefer explicit user invocation with `$skill-name`. Use
  `SKILL.md` as canonical skill behavior, `docs/ai/USAGE.md` as the concise
  human quick-reference, and `docs/ai/MAINTENANCE.md` as maintenance policy.
- Codex may use `$calcite-branch` implicitly to start non-workflow local work
  branches from `main` when implementation begins from `config/codex`. Keep
  workflow-layer edits on `config/codex`. Finalize back to one clean surviving
  branch remains explicit.
- Use `$calcite-commit` when you want an explicit local commit. It does not
  push, and it does not rewrite the final upstream commit subject for you.
- If the task is bug diagnosis or a regression, use `$calcite-bug-root-cause`
  before editing, identify the owning layer first, and inventory adjacent
  affected surfaces.
- If the task is a support, syntax, or SQL-pattern check, use
  `$calcite-query-support-check`.
- If the task adds or changes an external function, operator, callable surface,
  or closely related syntax, use `$calcite-function-or-operator-work` before
  implementation.
- If the task is planner optimization, rule design, or planner-owned bad-plan
  work, use `$calcite-optimization-or-rule-work` before editing.
- If the primary deliverable is understanding, invariants, or tradeoffs, use
  `$calcite-research-deep-dive`.
- Before upstream PR handoff, run `$calcite-pr-ready` for a reviewer-risk pass
  scoped to touched surfaces and direct fallout.
- After substantial normal work, run `$calcite-knowledge-capture`.
- If repeated friction or repeated correction occurs, run
  `$calcite-workflow-retrospective`.
- To create or materially change a repo skill, use `$skill-creator` and then
  `$calcite-workflow-sync`.
- After adding, removing, renaming, or materially changing a skill, run
  `$calcite-workflow-sync`.
- After changing skill boundaries or descriptions, run
  `$calcite-workflow-routing-audit`.
- Periodically run `$calcite-workflow-cleanup` to keep the workflow layer
  aligned.

## Concise Repo Map

- Parser admission: `core/src/main/codegen/templates/Parser.jj`
- Validator and name resolution: `core/src/main/java/org/apache/calcite/sql/validate/`
- SQL-to-Rel and decorrelation: `core/src/main/java/org/apache/calcite/sql2rel/`
- Planner and rules: `core/src/main/java/org/apache/calcite/plan/`,
  `core/src/main/java/org/apache/calcite/rel/rules/`
- Parser tests: `testkit/src/main/java/org/apache/calcite/sql/parser/`,
  `core/src/test/java/org/apache/calcite/sql/parser/`
- Validator, SQL-to-Rel, and planner tests:
  `core/src/test/java/org/apache/calcite/test/SqlValidatorTest.java`,
  `core/src/test/java/org/apache/calcite/test/SqlToRelConverterTest.java`,
  `core/src/test/resources/org/apache/calcite/test/SqlToRelConverterTest.xml`,
  `core/src/test/java/org/apache/calcite/test/RelOptRulesTest.java`,
  `core/src/test/resources/org/apache/calcite/test/RelOptRulesTest.xml`
- End-to-end fixtures: `testkit/src/main/java/org/apache/calcite/test/`,
  `testkit/src/main/java/org/apache/calcite/sql/test/`

## Build, Test, And Validation

- Start with the smallest relevant validation for the change.
- Add or update targeted tests first in the nearest existing test class or
  fixture.
- Use `./gradlew style` as Calcite's repo-owned formatter and style entry
  point; IntelliJ code style is only partial help and is not authoritative.
- Before calling a non-trivial upstream patch PR-ready, `./gradlew clean build`
  should be green unless the user explicitly scoped validation differently.
- Explain unexpected failures before changing expectations, goldens, or test
  outputs.
- Common commands: `./gradlew build`, `./gradlew test`, `./gradlew check`,
  `./gradlew style`,
  `./gradlew -PenableCheckerframework :linq4j:classes :core:classes`,
  `./gradlew -PenableErrorprone classes`

## Done Means

- The chosen skill matched the job, and any handoff to another skill was
  explicit.
- The owning layer is identified and the fix lives there.
- The patch is reviewable, upstreamable, free of unrelated churn, and any
  changed expectations, goldens, or test outputs are explained.
- Normal Calcite work ends on one clean surviving branch, and leftover
  exploratory worktrees or side branches are removed or explicitly justified.
- For split or bucketed campaigns, every selected bucket reached a terminal
  state before the parent task was called done.
- Targeted validation ran at the right scope and unexpected fallout was
  explained.
- Durable lessons were routed through `$calcite-knowledge-capture` when the
  session produced them.
