# ExecPlan Template

Use this template for long, multi-step work.

Treat the plan as a living document. Update it as understanding changes, not
just at the end.

The same format should work for bug fixes, function or operator work,
optimizations or rules, and research tasks.

## Usage Rules

- Keep milestones current.
- Record the routing choice when it is non-obvious: which workflow or skill
  owns the task, and which nearby alternatives were rejected.
- Record the exact validation commands you actually ran.
- Log meaningful decisions when tradeoffs are resolved.
- Separate confirmed discoveries from open questions.
- End with concrete outcomes, not just activity.

## Template

```md
# ExecPlan: <short task name>

Last Updated: YYYY-MM-DD
Status: planned | in_progress | blocked | done

## Goal

<What this work is trying to achieve.>

## Scope / Non-Goals

- In scope: <items>
- Out of scope: <items>

## Routing Choice

- Primary workflow / skill: <name>
- Nearby alternatives rejected: <name + short reason>

## Milestones

- [ ] M1: <milestone name>
  Target evidence: <what proves it is complete>
- [ ] M2: <milestone name>
  Target evidence: <what proves it is complete>
- [ ] M3: <milestone name>
  Target evidence: <what proves it is complete>

## Validation Commands

- `command here`
  Purpose: <why this command matters>
  Result: pending | pass | fail

## Decision Log

- YYYY-MM-DD: <decision made>
  Reason: <why this decision was chosen>

## Discoveries

- YYYY-MM-DD: <confirmed finding>
  Evidence: <tests, code paths, plans, docs, or experiments>

## Risks / Open Questions

- <risk or unresolved question>
- <what would change the plan>

## Outcomes

- <what was completed>
- <what remains>
- <recommended next step>
```

# ExecPlan: Calcite skill routing audit

Last Updated: 2026-03-22
Status: done

## Goal

Audit the Calcite agent contract, workflow docs, and repo-scoped skills for
routing quality and overlap; tighten ambiguous trigger text with minimal
structural change; and validate the result with concrete prompt coverage.

## Scope / Non-Goals

- In scope: `AGENTS.md`, `.agents/PLANS.md`, `docs/ai/workflows/*.md`,
  `.agents/skills/*/SKILL.md`, and a prompt routing matrix for the updated
  skills.
- Out of scope: new skill categories, workflow architecture changes, helper
  scripts, or broad documentation refactors unrelated to routing clarity.

## Routing Choice

- Primary workflow / skill: `skill-creator`
- Nearby alternatives rejected: `calcite-research-deep-dive`, because this
  task is updating skill-routing metadata and workflow boundaries rather than
  studying a Calcite subsystem.

## Milestones

- [x] M1: Audit the current routing surface and identify overlap or mismatch
  points
  Target evidence: concrete overlap notes covering AGENTS, workflows, and all
  repo skills.
- [x] M2: Patch the routing text with minimal structural change
  Target evidence: updated contract, workflow docs, and skill files with
  tighter trigger boundaries.
- [x] M3: Validate the result with positive and negative prompt coverage
  Target evidence: final routing matrix with prompt-to-skill reasoning and
  misfire notes.

## Validation Commands

- `rg --files AGENTS.md .agents docs/ai/workflows .agents/skills`
  Purpose: confirm the exact routing surface under audit.
  Result: pass
- `sed -n '1,260p' AGENTS.md`
  Purpose: inspect the repo-level agent contract before editing.
  Result: pass
- `sed -n '1,260p' .agents/PLANS.md`
  Purpose: inspect the plan template and prior skill creation plan before
  editing.
  Result: pass
- `find docs/ai/workflows -maxdepth 2 -type f | sort`
  Purpose: confirm the canonical workflow set under `docs/ai/workflows`.
  Result: pass
- `find .agents/skills -maxdepth 2 -name SKILL.md | sort`
  Purpose: confirm the repo-scoped Calcite skills being audited.
  Result: pass
- `rg -n "^## Do Not Use When" docs/ai/workflows/*.md`
  Purpose: confirm every canonical workflow doc now declares routing
  exclusions.
  Result: pass
- `rg -n "^description:|^## Do Not Use|^## Handoff / Overlap" .agents/skills/*/SKILL.md`
  Purpose: confirm every repo skill keeps aligned frontmatter routing text plus
  explicit overlap boundaries.
  Result: pass

## Decision Log

- 2026-03-22: Keep the current seven-skill architecture unless a boundary is
  clearly broken.
  Reason: the observed risk is overlap and vague routing language, not a
  missing category.
- 2026-03-22: Tighten routing around the "primary deliverable" rather than by
  adding more categories.
  Reason: most misfires came from prompts that blended support checks, bug
  diagnosis, and research, not from missing workflows.
- 2026-03-22: Standardize query-support classifications across the workflow doc
  and the skill.
  Reason: mismatched status labels would cause avoidable routing drift and
  inconsistent outputs.

## Discoveries

- 2026-03-22: The largest overlap risk is between support triage, bug
  diagnosis, and open-ended research.
  Evidence: side-by-side reading of `docs/ai/workflows/*.md` and
  `.agents/skills/*/SKILL.md`.
- 2026-03-22: Some workflow outputs and skill outputs use different
  classifications for similar tasks.
  Evidence: initial comparison of the workflow docs against the skill
  frontmatter and checklists.
- 2026-03-22: Planner work was most likely to misroute when prompts described a
  "bad plan" without saying whether the planner was already known to own the
  issue.
  Evidence: side-by-side comparison of bug-diagnosis and optimization/rule
  routing language.

## Risks / Open Questions

- Skill routing depends on frontmatter descriptions, so wording changes must be
  precise and concise enough to improve trigger quality without collapsing
  distinctions.
- The repo contains newly added untracked docs and skill files; edits must stay
  narrowly scoped to the audited routing surface.

## Outcomes

- Added repo-level routing guidance to `AGENTS.md` and routing-choice guidance
  to the ExecPlan template.
- Added explicit `Do Not Use When` sections to all canonical workflow docs.
- Tightened every repo skill frontmatter description around the primary
  deliverable and aligned overlap handoffs with the canonical workflows.
- Verified the updated routing surface with section-presence checks and a final
  prompt matrix.

# ExecPlan: Subquery and decorrelation deep dive

Last Updated: 2026-03-23
Status: done

## Goal

Create a durable internal doc that maps Calcite subquery handling and
decorrelation end to end, and turn the findings into concrete guidance for
debugging and composing custom planner programs.

## Scope / Non-Goals

- In scope: `docs/ai/knowledge` docs covering parser/validator boundaries,
  `SqlToRelConverter`, subquery-removal rules, both decorrelators, planner
  program sequencing, and physical fallback behavior.
- Out of scope: code changes to subquery handling, new planner rules, or issue
  specific diagnosis.

## Routing Choice

- Primary workflow / skill: `calcite-research-deep-dive`
- Nearby alternatives rejected: `calcite-query-support-check`, because the
  deliverable is an end-to-end mental model rather than a verdict on one SQL
  shape; `calcite-knowledge-capture` is used after the research to place the
  doc in the reviewed knowledge layer.

## Milestones

- [x] M1: Ground the subquery and decorrelation pipeline in current code and
  tests
  Target evidence: confirmed entry points, config knobs, rule programs, and
  representative tests for legacy and top-down paths.
- [x] M2: Write the reviewed knowledge doc and wire it into existing knowledge
  docs
  Target evidence: new deep-dive markdown plus updated `README.md` and
  `calcite-mental-model.md`.
- [x] M3: Validate the write-up and classify it into the maintained knowledge
  layer
  Target evidence: targeted repo checks plus an explicit decision to keep the
  result in `docs/ai/knowledge`.

## Validation Commands

- `rg -n "subquery|decorrelat|SubQuery|RelDecorrelator|TopDownGeneralDecorrelator|Programs\\.subQuery|Programs\\.decorrelate" core testkit site docs -g '!**/build/**'`
  Purpose: map the current implementation and evidence surface before writing.
  Result: pass
- `sed -n '230,370p' core/src/main/java/org/apache/calcite/tools/Programs.java`
  Purpose: confirm the built-in subquery and decorrelate program shapes.
  Result: pass
- `sed -n '1260,1525p' core/src/main/java/org/apache/calcite/sql2rel/SqlToRelConverter.java`
  Purpose: confirm default subquery replacement and eager-expansion behavior.
  Result: pass
- `sed -n '130,320p' core/src/main/java/org/apache/calcite/sql2rel/RelDecorrelator.java`
  Purpose: confirm the legacy decorrelator entry point and phase structure.
  Result: pass
- `sed -n '90,120p' core/src/main/java/org/apache/calcite/sql2rel/TopDownGeneralDecorrelator.java`
  Purpose: confirm the intended top-down usage notes.
  Result: pass
- `git diff --check -- .agents/PLANS.md docs/ai/knowledge/README.md docs/ai/knowledge/calcite-mental-model.md docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify the doc edits are clean and free of whitespace issues.
  Result: pass

## Decision Log

- 2026-03-23: Put the new write-up in `docs/ai/knowledge`.
  Reason: the findings are cross-cutting and stable enough to serve as durable
  orientation, not a candidate note or issue diary.
- 2026-03-23: Distinguish converter-driven decorrelation from
  program-driven subquery removal plus decorrelation.
  Reason: current Calcite uses both paths, and treating them as one pipeline
  would make the guidance inaccurate for `PlannerImpl.rel()` and converter
  tests.
- 2026-03-23: Keep the result as reviewed knowledge instead of a candidate note.
  Reason: the findings are cross-cutting, code-grounded, and likely to be
  reused for debugging and onboarding.

## Discoveries

- 2026-03-23: `Programs.standard()` uses a fixed stage order of subquery
  removal, decorrelation, measure rewrite, trim, main optimization, and calc.
  Evidence: `core/src/main/java/org/apache/calcite/tools/Programs.java`.
- 2026-03-23: `PlannerImpl.rel()` directly runs decorrelation after
  `convertQuery` and `flattenTypes`, while `FrameworkConfig.ConfigBuilder`
  starts with no planner programs.
  Evidence: `core/src/main/java/org/apache/calcite/prepare/PlannerImpl.java`
  and `core/src/main/java/org/apache/calcite/tools/Frameworks.java`.
- 2026-03-23: Top-down decorrelation prefers mark-correlate rewrites for
  filter and project, while join subquery handling is still more cautious.
  Evidence: `TopDownGeneralDecorrelator` usage notes and
  `RelOptRulesTest` top-down cases.

## Risks / Open Questions

- The new doc must stay precise about which statements describe default
  behavior, which describe recommended embedding guidance, and which describe
  the experimental top-down path.
- `TopDownGeneralDecorrelator` class comments lag behind current wiring in some
  modules, so the write-up must call out the mismatch without overstating
  support.

## Outcomes

- Added `docs/ai/knowledge/subquery-and-decorrelation-pipeline.md` as the
  reviewed deep dive for this area.
- Updated the reviewed knowledge index and linked the deep dive from
  `calcite-mental-model.md`.
- Classified the result as durable reviewed knowledge under
  `docs/ai/knowledge`, satisfying the knowledge-capture step for this session.

# ExecPlan: Subquery deep dive primer extension

Last Updated: 2026-03-23
Status: done

## Goal

Extend the subquery and decorrelation deep dive with a compact SQL/operator
primer, an executor support audit, and a grounded edge-case map for
correlation ownership gaps.

## Scope / Non-Goals

- In scope: `docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  sections covering SQL constructs, rel operators, helper aggregates,
  executor-facing surfaces, and known pipeline gaps such as bad
  `variablesSet`.
- Out of scope: planner code changes, test changes, or a separate companion
  cheat-sheet file.

## Routing Choice

- Primary workflow / skill: `calcite-research-deep-dive`
- Nearby alternatives rejected: `calcite-query-support-check`, because the
  deliverable is a reusable reference and debugging guide rather than a
  verdict on one SQL shape; `calcite-knowledge-capture` stayed satisfied by
  extending the reviewed knowledge doc in place.

## Milestones

- [x] M1: Reconfirm the current operator and helper-aggregate surface in code
  and tests
  Target evidence: current references for `LEFT_MARK`,
  `ConditionalCorrelate`, `Collect`, `SINGLE_VALUE`, `LITERAL_AGG(true)`, and
  `variablesSet`-related failures.
- [x] M2: Extend the doc with the primer, rel-tree reading guidance, executor
  audit, and gap map
  Target evidence: new `SQL + operator primer` and `Pipeline edge cases and
  current gaps` sections in the existing deep dive.
- [x] M3: Validate formatting and section placement
  Target evidence: section-presence checks plus clean `git diff --check`
  output.

## Validation Commands

- `rg -n "LEFT_MARK|ConditionalCorrelate|SINGLE_VALUE|Collect|literal_agg|LiteralAgg|AGG_TRUE|MIN\\(true\\)|UNIQUE|NOT IN|SOME\\(|ANY\\(|ARRAY_QUERY|MULTISET_QUERY|MAP_QUERY|MarkToSemiOrAntiJoinRule|variablesSet|RexFieldAccess|CorrelationId" core/src/main/java core/src/test testkit/src/main/java docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: confirm the concrete operator and gap surface before extending the
  doc.
  Result: pass
- `sed -n '1240,1305p' core/src/main/java/org/apache/calcite/rel/rules/SubQueryRemoveRule.java`
  Purpose: confirm the current `LEFT_MARK` rewrite path and its inputs.
  Result: pass
- `rg -n "MARK_TO_SEMI_OR_ANTI_JOIN_RULE|LEFT_MARK|LogicalConditionalCorrelate|EnumerableConditionalCorrelate|LITERAL_AGG\\(true\\)|GROUPING\\(" core/src/test/resources/sql/sub-query.iq core/src/test/resources/sql/new-decorr.iq core/src/test/java/org/apache/calcite/test/enumerable core/src/test/java/org/apache/calcite/test/RelOptRulesTest.java`
  Purpose: confirm the runtime and test evidence for mark joins, helper
  aggregates, and quantified-subquery helpers.
  Result: pass
- `rg -n "^## SQL \\+ operator primer|^### Shared sample data|^### SQL constructs cheat sheet|^### Rel operators and join types you will see|^### Executor support audit|^## Pipeline edge cases and current gaps" docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify the new sections are present in the expected place.
  Result: pass
- `git diff --check -- .agents/PLANS.md docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify the doc edits are clean and free of whitespace issues.
  Result: pass

## Decision Log

- 2026-03-23: Keep the primer inside the existing deep dive rather than
  creating a second markdown file.
  Reason: the new material is reference support for the same pipeline, and a
  second file would split the debugging story unnecessarily.
- 2026-03-23: Limit the operator inventory to surfaces evidenced in current
  code and tests.
  Reason: the user's executor-audit goal needs a trustworthy list of actual
  emitted operators, not a generic SQL encyclopedia.
- 2026-03-23: Use one shared sample dataset for the cheat sheet.
  Reason: this keeps null, duplicate, and empty-RHS semantics comparable
  across `EXISTS`, `IN`, `NOT IN`, `SOME`, `UNIQUE`, and collection
  constructors.

## Discoveries

- 2026-03-23: Current subquery-removal and test evidence includes
  `LEFT_MARK`, `LogicalConditionalCorrelate`, `Collect`, `SINGLE_VALUE`, and
  `LITERAL_AGG(true)` as real surfaces users may need to read or execute.
  Evidence: `SubQueryRemoveRule.java`, `sub-query.iq`,
  `EnumerableCorrelateTest.java`, and `new-decorr.iq`.
- 2026-03-23: `GROUPING(...)` and marker aggregates appear in more elaborate
  quantified-subquery rewrites, so the primer needed a helper-expression
  section, not only join-type descriptions.
  Evidence: `sub-query.iq` quantified-subquery plans and
  `RelOptRulesTest.java`.
- 2026-03-23: The most important correlation-ownership debugging distinction
  is between normal correlation inside `RexSubQuery.rel` before rewrite and a
  true post-rewrite ownership gap such as bad `variablesSet`.
  Evidence: `RelDecorrelator` behavior plus `[CALCITE-7434]` coverage in
  `new-decorr.iq`.

## Risks / Open Questions

- The illustrative rel fragments are intentionally simplified, so readers
  still need to expect extra `Project` or `Calc` nodes in real plans.
- Backend support needs remain planner-path-dependent; a backend that accepts
  stable legacy plans may still fail on the experimental top-down mark path.

## Outcomes

- Extended the deep dive with a compact primer for SQL constructs,
  subquery-specific rel operators, helper aggregates, and executor-facing
  surfaces.
- Added a practical edge-case section that answers the `variablesSet`
  question directly and maps common symptoms to likely owners and actions.
- Kept the material in reviewed knowledge so future debugging and executor
  audits can start from one maintained reference.

# ExecPlan: DataFusion execution spec for residual subquery surfaces

Last Updated: 2026-03-23
Status: done

## Goal

Extend the subquery and decorrelation deep dive with a DataFusion-oriented
execution spec for the Calcite surfaces that can still survive subquery
removal or decorrelation and are not directly executable in DataFusion today.

## Scope / Non-Goals

- In scope: residual Calcite surfaces such as `RexSubQuery`,
  `LogicalCorrelate`, `LogicalConditionalCorrelate`, `Collect`,
  `SINGLE_VALUE`, `LITERAL_AGG`, and `SUM0`, plus direct-map notes for
  `SEMI`, `ANTI`, and `LEFT_MARK`.
- Out of scope: Calcite planner code changes, DataFusion code changes, or a
  full design for one specific executor codebase.

## Routing Choice

- Primary workflow / skill: `calcite-research-deep-dive`
- Nearby alternatives rejected: `calcite-optimization-or-rule-work`, because
  this task is documentation and execution-contract research, not planner-rule
  design; `calcite-knowledge-capture` remained satisfied by updating the
  reviewed deep-dive doc in place.

## Milestones

- [x] M1: Reconfirm the exact residual Calcite surfaces and their runtime
  semantics
  Target evidence: current Calcite classes and tests for `Correlate`,
  `ConditionalCorrelate`, `Collect`, `SINGLE_VALUE`, `LITERAL_AGG`, and
  `SUM0`.
- [x] M2: Reconfirm DataFusion's current direct support and hard gaps
  Target evidence: current DataFusion logical and physical planner code for
  subqueries, join types, mark joins, and extension planning.
- [x] M3: Extend the deep dive with an executor-ready spec and validate the
  docs-only change
  Target evidence: new DataFusion execution-spec section plus clean
  `git diff --check`.

## Validation Commands

- `rg -n "Correlate|ConditionalCorrelate|SINGLE_VALUE|LITERAL_AGG|SUM0|Collect" core/src/main/java core/src/test/java testkit/src/main/java -g '!**/build/**'`
  Purpose: confirm the Calcite residual surfaces and their owning classes.
  Result: pass
- `sed -n '1,260p' core/src/main/java/org/apache/calcite/rel/core/Correlate.java`
  Purpose: confirm `Correlate` join-type and row-production semantics.
  Result: pass
- `sed -n '1,260p' core/src/main/java/org/apache/calcite/rel/core/ConditionalCorrelate.java`
  Purpose: confirm that `ConditionalCorrelate` is Calcite's conditional
  `LEFT_MARK` apply operator.
  Result: pass
- `sed -n '1,220p' core/src/main/java/org/apache/calcite/rel/core/Collect.java`
  Purpose: confirm `Collect` output-shape and collection-constructor mapping.
  Result: pass
- `sed -n '1,120p' core/src/main/java/org/apache/calcite/sql/fun/SqlSingleValueAggFunction.java`
  Purpose: confirm `SINGLE_VALUE` semantics.
  Result: pass
- `sed -n '1,120p' core/src/main/java/org/apache/calcite/sql/fun/SqlLiteralAggFunction.java`
  Purpose: confirm `LITERAL_AGG` returns its literal pre-operand.
  Result: pass
- `sed -n '1,140p' core/src/main/java/org/apache/calcite/sql/fun/SqlSumEmptyIsZeroAggFunction.java`
  Purpose: confirm `SUM0` semantics.
  Result: pass
- `rg -n "LogicalPlan::Subquery\\(_\\) => todo!\\(|Physical plan does not support logical expression|OuterReferenceColumn|LeftMark|LeftSemi|LeftAnti|null_aware" /Users/nirmalgovindaraj/other-repos/datafusion/datafusion/core/src /Users/nirmalgovindaraj/other-repos/datafusion/datafusion/expr/src /Users/nirmalgovindaraj/other-repos/datafusion/datafusion/physical-expr/src /Users/nirmalgovindaraj/other-repos/datafusion/datafusion/physical-plan/src -g '!**/target/**'`
  Purpose: confirm DataFusion's native support boundary and remaining gaps.
  Result: pass
- `git diff --check -- .agents/PLANS.md docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify the docs-only edits are clean and free of whitespace issues.
  Result: pass

## Decision Log

- 2026-03-23: Put the execution contract in the existing deep-dive doc rather
  than creating a separate DataFusion note.
  Reason: the support story is part of the same subquery/decorrelation
  debugging flow.
- 2026-03-23: Separate decorrelated `LEFT_MARK` joins from correlated
  `ConditionalCorrelate`.
  Reason: DataFusion can execute native mark joins, but it does not have a
  native correlated apply operator.
- 2026-03-23: Treat raw `RexSubQuery` as a translation-time normalization
  problem rather than recommending direct DataFusion raw-subquery execution.
  Reason: current DataFusion logical and physical planning do not directly
  execute those surfaces.

## Discoveries

- 2026-03-23: DataFusion already has native logical and physical support for
  `LeftSemi`, `LeftAnti`, and `LeftMark`, including null-aware anti join
  support for `NOT IN`-style cases, but its mark column is currently a
  non-nullable boolean.
  Evidence: `datafusion/expr/src/logical_plan/plan.rs`,
  `datafusion/expr/src/logical_plan/builder.rs`,
  `datafusion/optimizer/src/decorrelate_predicate_subquery.rs`, and
  `datafusion/physical-plan/src/joins/hash_join/exec.rs`.
- 2026-03-23: DataFusion still does not directly execute raw subquery logical
  plans or expressions.
  Evidence: `datafusion/core/src/physical_planner.rs` and
  `datafusion/physical-expr/src/planner.rs`.
- 2026-03-23: `LITERAL_AGG` is best treated as a structural helper, not as a
  must-have runtime aggregate.
  Evidence: `SqlLiteralAggFunction.java` plus current subquery rewrite plans.

## Risks / Open Questions

- DataFusion's current null-aware anti join support is narrower than the full
  space of Calcite `NOT IN` rewrites, so executor teams still need a fallback
  path for more general cases.
- `MAP_QUERY_CONSTRUCTOR` duplicate-key behavior is not encoded directly in
  Calcite's `Collect` node, so executor teams must choose and document a
  runtime policy.

## Outcomes

- Added a DataFusion-specific execution-spec section to
  `docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`.
- Split the guidance into direct mappings, custom apply operators, raw
  subquery normalization, and helper-aggregate lowering rules.
- Updated the debugging checklist so DataFusion-based executor work points to
  the new execution contract.

# ExecPlan: Executor robustness bridge nodes and custom pipeline guidance

Last Updated: 2026-03-23
Status: done

## Goal

Extend the subquery/decorrelation deep dive with a concrete bridge-node layer
for executor robustness and with stronger guidance for custom multi-stage
optimizer pipelines around subquery removal and decorrelation.

## Scope / Non-Goals

- In scope: conceptual apply-bridge nodes for raw `RexSubQuery`, executor
  fallback guidance, and rule/program sequencing recommendations for custom
  logical pipelines.
- Out of scope: Calcite planner code changes, DataFusion code changes, or a
  mandated one-size-fits-all optimizer stack.

## Routing Choice

- Primary workflow / skill: `calcite-research-deep-dive`
- Nearby alternatives rejected: `calcite-optimization-or-rule-work`, because
  the deliverable is still documentation and sequencing guidance rather than a
  planner patch.

## Milestones

- [x] M1: Reconfirm current built-in subquery/decorrelation sequencing and
  nearby rule interactions
  Target evidence: `Programs.subQuery()`, `Programs.decorrelate()`,
  `RelDecorrelator`, `TopDownGeneralDecorrelator`, and relevant rule tests.
- [x] M2: Add executor bridge-node specs and custom-pipeline recipes to the
  deep-dive doc
  Target evidence: new bridge-node section plus expanded custom-program
  guidance in `subquery-and-decorrelation-pipeline.md`.
- [x] M3: Validate the docs-only changes
  Target evidence: clean `git diff --check`.

## Validation Commands

- `sed -n '230,360p' core/src/main/java/org/apache/calcite/tools/Programs.java`
  Purpose: confirm built-in subquery and decorrelate program composition.
  Result: pass
- `sed -n '340,520p' core/src/main/java/org/apache/calcite/sql2rel/RelDecorrelator.java`
  Purpose: confirm legacy decorrelator internal pre/post cleanup.
  Result: pass
- `sed -n '1,320p' core/src/main/java/org/apache/calcite/sql2rel/TopDownGeneralDecorrelator.java`
  Purpose: confirm top-down usage notes and internal pre/post cleanup.
  Result: pass
- `sed -n '440,490p' testkit/src/main/java/org/apache/calcite/test/RelOptFixture.java`
  Purpose: confirm the stable test helper rule set for subquery removal.
  Result: pass
- `sed -n '10470,10520p' core/src/test/java/org/apache/calcite/test/RelOptRulesTest.java`
  Purpose: confirm current join-subquery limitations and top-down rule usage.
  Result: pass
- `sed -n '12000,12320p' core/src/test/java/org/apache/calcite/test/RelOptRulesTest.java`
  Purpose: confirm current top-down mark-correlate test patterns and project
  merge behavior around correlation.
  Result: pass
- `git diff --check -- .agents/PLANS.md docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify the docs-only edits are clean and free of whitespace issues.
  Result: pass

## Decision Log

- 2026-03-23: Recommend a bridge apply layer rather than direct raw
  `RexSubQuery` execution.
  Reason: it gives a uniform explicit contract across scalar, existence,
  quantified, and collection subqueries.
- 2026-03-23: Recommend subquery removal plus decorrelation as an early
  dedicated normalization stage, not the absolute first stage and not late in
  a broad optimizer.
  Reason: a small pre-normalization pass is useful, but broad structural
  rewrites before subquery removal mainly make ownership harder.
- 2026-03-23: Recommend avoiding broad join and transpose rules before
  `Programs.subQuery()`.
  Reason: built-in Calcite does not use them there, and they can push
  subqueries into harder `JOIN` ownership cases.

## Discoveries

- 2026-03-23: `Programs.subQuery()` stays intentionally tiny: only the
  subquery-removal rules plus `PROJECT_OVER_SUM_TO_SUM0_RULE`.
  Evidence: `Programs.java`.
- 2026-03-23: Both decorrelators already run targeted internal cleanup, so
  duplicating those same rules immediately around `Programs.decorrelate()` is
  usually redundant.
  Evidence: `RelDecorrelator.java` and `TopDownGeneralDecorrelator.java`.
- 2026-03-23: Current top-down tests consistently pair mark-correlate rules
  with lightweight `PROJECT_MERGE` / `PROJECT_REMOVE` cleanup, not with broad
  join reordering.
  Evidence: `RelOptRulesTest.java`.

## Risks / Open Questions

- The bridge-node names are conceptual; executor teams still need to map them
  onto their own logical-plan abstractions.
- Some embedders may still choose to trim later than the standard-like
  recommendation if later custom logical stages explicitly depend on the
  untrimmed shape.

## Outcomes

- Added a concrete bridge-node layer for raw subqueries to
  `docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`.
- Expanded the custom-program guidance with explicit safe pre-rules, rules to
  avoid before the subquery stage, and stable versus top-down recipes.
- Made the doc more directly actionable for executor and planner teams
  designing a multi-stage pipeline.

# ExecPlan: Repo-scoped Calcite skills

Last Updated: 2026-03-22
Status: done

## Goal

Create repo-scoped Codex skills under `.agents/skills` that route to the
current Calcite workflows in `docs/ai/workflows` without freezing stale
subsystem facts.

## Scope / Non-Goals

- In scope: seven focused `SKILL.md` files, routing-oriented descriptions,
  explicit overlap boundaries, and a verification pass.
- Out of scope: helper scripts, skill UI metadata, or changes to the workflow
  docs themselves.

## Milestones

- [x] M1: Confirm canonical workflow sources and current `docs/ai` layout
  Target evidence: matching workflow docs found and read.
- [x] M2: Create seven repo-scoped skill folders and `SKILL.md` files
  Target evidence: `.agents/skills/*/SKILL.md` exists for every requested
  skill.
- [x] M3: Verify routing content and summarize overlap risks
  Target evidence: required sections present and summary output prepared.

## Validation Commands

- `find .agents/skills -maxdepth 2 -name SKILL.md | sort`
  Purpose: confirm all requested skill files exist.
  Result: pass
- `rg -n "Canonical Source|## Description|## Do Not Use|## Output Checklist|## Handoff / Overlap" .agents/skills/*/SKILL.md`
  Purpose: confirm every skill contains the required routing and handoff
  structure.
  Result: pass
- `rg -n "docs/ai/workflows/" .agents/skills/*/SKILL.md`
  Purpose: confirm every skill points to a canonical workflow document.
  Result: pass
- `rg -n "Read current repo truth first" .agents/skills/*/SKILL.md`
  Purpose: confirm every skill starts from current code, tests, and docs
  instead of stale Calcite facts.
  Result: pass

## Decision Log

- 2026-03-22: Create only `SKILL.md` files in this pass.
  Reason: the request is for repo-scoped skills, and no UI metadata or scripts
  are required to make them usable.
- 2026-03-22: Treat `docs/ai/workflows/*.md` as canonical and keep the skills
  thin.
  Reason: avoid duplicating or freezing Calcite task guidance in multiple
  places.

## Discoveries

- 2026-03-22: `.agents/PLANS.md` contained only the ExecPlan template.
  Evidence: initial file inspection.
- 2026-03-22: `docs/ai/workflows` already contains one workflow doc for each
  requested skill.
  Evidence: repository search under `docs/ai/workflows`.
- 2026-03-22: `docs/ai/candidates` and `docs/ai/knowledge` already exist.
  Evidence: directory inspection under `docs/ai`.

## Risks / Open Questions

- `.agents` writes needed escalated filesystem access in this environment.
- The main design risk is overlap between diagnosis, support triage, and
  research skills; each skill must declare explicit handoff boundaries.

## Outcomes

- Created seven focused repo-scoped skill folders under `.agents/skills`, each
  with a routing-oriented `SKILL.md`.
- Verified that each skill includes a canonical workflow reference, a current
  repo truth gate, explicit negative examples, an output checklist, and a
  handoff section.
- Recommended next step: use the new skills in practice and tighten any routing
  language only if real misfires appear.

# ExecPlan: Calcite workflow maintenance layer

Last Updated: 2026-03-22
Status: done

## Goal

Create a workflow-maintenance layer for this repo: add a maintenance policy
doc, create four explicit-only maintenance skills with UI metadata, and
refresh the concise help docs so the workflow surface stays discoverable and
internally consistent.

## Scope / Non-Goals

- In scope: `docs/ai/MAINTENANCE.md`, `docs/ai/USAGE.md`, `docs/ai/README.md`,
  `.agents/PLANS.md`, and new maintenance skills under `.agents/skills/`.
- Out of scope: Calcite production code, tests, new workflow playbooks, or a
  broad redesign of the repo workflow architecture.

## Routing Choice

- Primary workflow / skill: `skill-creator`
- Nearby alternatives rejected: `calcite-research-deep-dive`, because this
  task is creating and aligning repo workflow skills rather than studying a
  Calcite subsystem.

## Milestones

- [x] M1: Add the maintenance policy doc and refresh the help docs
  Target evidence: `docs/ai/MAINTENANCE.md` exists and `USAGE.md` plus
  `README.md` reflect the new maintenance layer.
- [x] M2: Create the four maintenance skills and UI metadata
  Target evidence: every `calcite-workflow-*` skill has `SKILL.md` and
  `agents/openai.yaml`.
- [x] M3: Validate the maintenance layer and summarize overlap risks
  Target evidence: targeted verification commands pass and a final risk summary
  is prepared.

## Validation Commands

- `rg --files .agents docs/ai | sort`
  Purpose: confirm the maintenance doc and skill files exist.
  Result: pass
- `find .agents/skills/calcite-workflow-* -maxdepth 3 -type f | sort`
  Purpose: confirm each maintenance skill has both `SKILL.md` and
  `agents/openai.yaml`.
  Result: pass
- `rg -n "display_name:|short_description:|default_prompt:|allow_implicit_invocation: false" .agents/skills/calcite-workflow-*/agents/openai.yaml`
  Purpose: confirm the UI metadata fields match the requested policy.
  Result: pass
- `rg -n "^name: calcite-workflow-|^description:|^# |^## Operating style|^## Required inputs|^## Expected outputs|^## Typical explicit invocation" .agents/skills/calcite-workflow-*/SKILL.md`
  Purpose: confirm the new skills follow the repo skill structure.
  Result: pass
- `rg -n "MAINTENANCE.md|explicit-only|knowledge|candidates|retrospective|routing-audit|cleanup|knowledge-capture" docs/ai/USAGE.md docs/ai/README.md docs/ai/MAINTENANCE.md`
  Purpose: confirm the doc layer encodes the maintenance rules and cadence.
  Result: pass

## Decision Log

- 2026-03-22: Keep `SKILL.md` canonical and use `docs/ai/USAGE.md` plus
  `docs/ai/MAINTENANCE.md` as the help and policy layer.
  Reason: this keeps behavior in the skills while preserving concise
  discoverability in docs.
- 2026-03-22: Make all maintenance skills explicit-only with
  `allow_implicit_invocation: false`.
  Reason: these are meta-maintenance tools and should not be injected into
  normal Calcite task routing by default.

## Discoveries

- 2026-03-22: `docs/ai/USAGE.md` still says no maintenance doc exists.
  Evidence: direct read of `docs/ai/USAGE.md`.
- 2026-03-22: The repo currently has no repo-local `agents/openai.yaml` files.
  Evidence: file search under `.agents/skills/`.

## Risks / Open Questions

- The main overlap risk is between `calcite-workflow-cleanup`,
  `calcite-workflow-routing-audit`, and `calcite-workflow-retrospective`; the
  wording must keep cleanup, audit, and repeated-friction root cause separate.

## Outcomes

- Added `docs/ai/MAINTENANCE.md` as the workflow-layer maintenance policy and
  cadence doc.
- Refreshed `docs/ai/USAGE.md` and `docs/ai/README.md` so the maintenance
  layer is discoverable and no longer described as missing.
- Created four explicit-only maintenance skills, each with `SKILL.md` and
  `agents/openai.yaml`.
- Validated the new skill structure, UI metadata, and maintenance doc rules
  with targeted repository checks.

# ExecPlan: Workflow layer audit refresh

Last Updated: 2026-03-22
Status: done

## Goal

Audit the full Codex workflow layer for routing quality, drift, and
maintainability; tighten low-risk boundary text; and produce prompt coverage
plus a routing matrix for every skill.

## Scope / Non-Goals

- In scope: `AGENTS.md`, `docs/ai/USAGE.md`, `docs/ai/MAINTENANCE.md`,
  `docs/ai/workflows/**`, `docs/ai/knowledge/**`, `docs/ai/candidates/**`,
  `.agents/skills/**/SKILL.md`, and `.agents/skills/**/agents/openai.yaml`.
- Out of scope: Calcite production code, tests, new workflow architecture, or
  broader documentation refactors unrelated to routing quality.

## Routing Choice

- Primary workflow / skill: `calcite-workflow-routing-audit`
- Nearby alternatives rejected: `calcite-workflow-cleanup`, because cleanup is
  a follow-on tool once routing or duplication problems are already known;
  `calcite-workflow-retrospective`, because the current job is a full-layer
  audit rather than root-causing one repeated failure pattern.

## Milestones

- [x] M1: Inspect the current workflow layer and identify overlap, drift, and
  duplication
  Target evidence: side-by-side notes across AGENTS, docs, skills, and
  metadata.
- [x] M2: Apply minimal durable edits to boundary text and quick-reference docs
  Target evidence: updated skills and docs with tighter routing language.
- [x] M3: Validate the updated layer and prepare prompt coverage plus the final
  routing matrix
  Target evidence: targeted verification commands pass and the prompt matrix is
  complete.

## Validation Commands

- `rg --files AGENTS.md docs/ai .agents/skills | sort`
  Purpose: confirm the audited workflow surface.
  Result: pass
- `sed -n '1,320p' .agents/PLANS.md`
  Purpose: inspect existing workflow maintenance plans before adding a new one.
  Result: pass
- `for f in docs/ai/workflows/*.md; do sed -n '1,220p' "$f"; done`
  Purpose: inspect all canonical workflow docs for routing drift.
  Result: pass
- `for f in .agents/skills/*/SKILL.md; do sed -n '1,220p' "$f"; done`
  Purpose: inspect every repo skill contract and examples.
  Result: pass
- `for f in .agents/skills/*/agents/openai.yaml; do sed -n '1,120p' "$f"; done`
  Purpose: inspect repo skill metadata for maintenance-layer drift.
  Result: pass
- `sed -n '1,260p' docs/ai/USAGE.md`
  Purpose: verify the quick-reference layer after trimming duplicated
  maintenance guidance.
  Result: pass
- `sed -n '1,260p' docs/ai/MAINTENANCE.md`
  Purpose: verify maintenance policy and boundary notes after the audit edits.
  Result: pass
- `for f in .agents/skills/calcite-workflow-*/SKILL.md; do sed -n '1,220p' "$f"; done`
  Purpose: verify the maintenance-skill boundary wording and examples after the
  audit edits.
  Result: pass

## Decision Log

- 2026-03-22: Preserve the current eleven-skill architecture unless a boundary
  is clearly broken.
  Reason: the current issue is overlap and duplication, not a missing category.
- 2026-03-22: Focus edits on the maintenance layer first.
  Reason: the Calcite task skills are already reasonably separated; the new
  maintenance skills carry the highest overlap risk.

## Discoveries

- 2026-03-22: `docs/ai/USAGE.md` duplicates maintenance policy and repeats one
  implementation flow that already exists in the "After-the-work flow" section.
  Evidence: direct read of `docs/ai/USAGE.md`.
- 2026-03-22: The strongest current overlap is `workflow-cleanup` versus
  `workflow-routing-audit`, with a secondary overlap against
  `workflow-retrospective`.
  Evidence: side-by-side read of the maintenance skill descriptions and
  examples.

## Risks / Open Questions

- Maintenance-skill boundaries depend heavily on frontmatter descriptions, so
  small wording changes need to be precise.
- The repo currently has `agents/openai.yaml` only for maintenance skills; that
  is acceptable for now but could become a discoverability gap if UI metadata
  is expected for all skills later.

## Outcomes

- Tightened the maintenance-skill boundaries so help, routing audit, cleanup,
  and retrospective now declare distinct primary deliverables.
- Trimmed duplicated maintenance guidance from `docs/ai/USAGE.md` and moved
  the finer-grained maintenance boundaries into `docs/ai/MAINTENANCE.md`.
- Confirmed there is nothing in `docs/ai/knowledge/` that should be demoted in
  the current repo state.
- Prepared the full prompt coverage and routing matrix for all repo skills.

# ExecPlan: Calcite workflow sync skill

Last Updated: 2026-03-22
Status: done

## Goal

Create an explicit-only `calcite-workflow-sync` skill that keeps the workflow
layer internally consistent after workflow-level changes, and add the minimal
help/policy hints needed so the new skill is discoverable.

## Scope / Non-Goals

- In scope: `AGENTS.md`, `docs/ai/USAGE.md`, `docs/ai/MAINTENANCE.md`,
  `.agents/skills/calcite-workflow-sync/**`, and this ExecPlan.
- Out of scope: Calcite production code, tests, new workflow architecture, or
  broad rewrites of existing maintenance skills.

## Routing Choice

- Primary workflow / skill: `calcite-workflow-routing-audit`
- Nearby alternatives rejected: `calcite-workflow-cleanup`, because the main
  job is creating a new workflow skill and keeping routing/help docs aligned;
  `calcite-workflow-retrospective`, because there is no repeated-friction root
  cause to analyze first.

## Milestones

- [x] M1: Inspect the current workflow layer and existing maintenance-skill
  boundaries
  Target evidence: current AGENTS, maintenance docs, workflow docs, skills,
  and metadata were read before editing.
- [x] M2: Create the new workflow-sync skill and minimal companion doc updates
  Target evidence: the new skill files exist and the quick-reference docs
  mention the new skill.
- [x] M3: Validate the new skill surface and prepare the requested file output
  Target evidence: targeted verification passes and the final printed skill
  files plus scope list are ready.

## Validation Commands

- `sed -n '1,260p' AGENTS.md`
  Purpose: inspect current repo-level routing hints before editing.
  Result: pass
- `sed -n '1,260p' docs/ai/USAGE.md`
  Purpose: inspect the quick-reference layer before editing.
  Result: pass
- `sed -n '1,260p' docs/ai/MAINTENANCE.md`
  Purpose: inspect the maintenance policy before editing.
  Result: pass
- `for f in docs/ai/workflows/*.md; do sed -n '1,220p' "$f"; done`
  Purpose: confirm the canonical workflow docs that the new skill should align
  with.
  Result: pass
- `for f in .agents/skills/*/SKILL.md; do sed -n '1,220p' "$f"; done`
  Purpose: inspect existing repo skill contracts and reuse the current style.
  Result: pass
- `for f in .agents/skills/*/agents/openai.yaml; do sed -n '1,120p' "$f"; done`
  Purpose: inspect the current repo skill metadata pattern.
  Result: pass
- `sed -n '1,220p' .agents/skills/calcite-workflow-sync/SKILL.md`
  Purpose: verify the new skill contract and scope.
  Result: pass
- `sed -n '1,120p' .agents/skills/calcite-workflow-sync/agents/openai.yaml`
  Purpose: verify the new explicit-only UI metadata.
  Result: pass
- `rg -n "calcite-workflow-sync|allow_implicit_invocation: false|short_description:|default_prompt:" AGENTS.md docs/ai/USAGE.md docs/ai/MAINTENANCE.md .agents/skills/calcite-workflow-sync/SKILL.md .agents/skills/calcite-workflow-sync/agents/openai.yaml`
  Purpose: confirm the new skill is surfaced in the routing/help layer.
  Result: pass

## Decision Log

- 2026-03-22: Keep `calcite-workflow-sync` explicit-only and narrowly scoped to
  workflow-layer consistency after workflow changes.
  Reason: that keeps it distinct from help, cleanup, routing audit, and
  retrospective.
- 2026-03-22: Update `USAGE.md` and `MAINTENANCE.md` when adding the skill.
  Reason: otherwise the workflow layer would become stale immediately.

## Discoveries

- 2026-03-22: The existing maintenance layer has help, cleanup, routing audit,
  and retrospective, but no focused sync step after workflow-level changes.
  Evidence: direct inspection of `docs/ai/USAGE.md`,
  `docs/ai/MAINTENANCE.md`, and `.agents/skills/calcite-workflow-*/SKILL.md`.

## Risks / Open Questions

- `calcite-workflow-sync` overlaps slightly with `calcite-workflow-cleanup`;
  the skill text must keep "post-change consistency sync" separate from
  broader cleanup.

## Outcomes

- Created `.agents/skills/calcite-workflow-sync/` with `SKILL.md` and
  `agents/openai.yaml`.
- Added the minimal discoverability hints for the new skill to `AGENTS.md`,
  `docs/ai/USAGE.md`, and `docs/ai/MAINTENANCE.md`.
- Verified the new skill is explicit-only and that the help/policy layer now
  references it.

# ExecPlan: Calcite foundational docs bootstrap

Last Updated: 2026-03-23
Status: done

## Goal

Bootstrap the smallest high-signal foundational docs layer in `docs/ai` from
the current Calcite codebase, tests, and repo docs.

## Scope / Non-Goals

- In scope: reviewed knowledge docs, candidate-note cleanup, and
  classification of existing `docs/ai` knowledge-like content.
- Out of scope: Calcite production code and tests, skill routing docs,
  workflow help docs, or a broad architecture rewrite.

## Routing Choice

- Primary workflow / skill: `calcite-research-deep-dive` followed by
  `calcite-knowledge-capture`
- Nearby alternatives rejected: `calcite-workflow-sync`, because this task is
  about Calcite knowledge content rather than workflow-layer sync.

## Milestones

- [x] M1: Inventory current knowledge-like docs and decide whether
  architecture docs are justified
  Target evidence: explicit keep, refine, and discard decisions plus an
  architecture decision.
- [x] M2: Implement the minimal reviewed docs tree
  Target evidence: new reviewed docs plus refreshed knowledge and candidate
  README files.
- [x] M3: Verify the final docs layer and summarize removals
  Target evidence: final file list, concise changelog, and a foundational
  purpose table.

## Validation Commands

- `rg --files AGENTS.md docs/ai .agents/skills`
  Purpose: inventory current AI-layer docs and skills.
  Result: pass
- `sed -n '1,260p' docs/ai/knowledge/*.md`
  Purpose: inspect the reviewed knowledge baseline.
  Result: pass
- `sed -n '1,260p' docs/ai/candidates/*.md`
  Purpose: classify current candidate notes before editing.
  Result: pass
- `sed -n '1,220p' README.md`
  Purpose: confirm what the main repo docs already cover.
  Result: pass
- `sed -n '1,220p' site/_docs/algebra.md`
  Purpose: confirm public planner and algebra docs already exist.
  Result: pass
- `find docs/ai -maxdepth 3 -type f | sort`
  Purpose: verify the final docs tree after cleanup.
  Result: pass
- `test ! -d docs/ai/architecture`
  Purpose: verify that no architecture directory was created.
  Result: pass
- `git diff -- docs/ai .agents/PLANS.md`
  Purpose: review the exact docs-only diff before handoff.
  Result: pass

## Decision Log

- 2026-03-23: Do not create `docs/ai/architecture/`.
  Reason: the repo already has durable public algebra and planner docs; the
  missing internal gap is a compact pipeline map and test-fixture map.
- 2026-03-23: Drop the current candidate archive notes instead of keeping a
  second archive layer.
  Reason: their durable parts are already covered or can be generalized into
  the reviewed docs.
- 2026-03-23: Remove the validator-specific reviewed doc from the foundational
  layer.
  Reason: one deeper layer doc without matching docs for other layers makes the
  base inconsistent and easier to bloat.

## Discoveries

- 2026-03-23: Existing `docs/ai` content is workflow-heavy and has only one
  reviewed subsystem doc.
  Evidence: inventory of `docs/ai/**`.
- 2026-03-23: The most expensive repo facts to repeatedly rediscover are layer
  boundaries and which test surface matches each layer.
  Evidence: review of `PlannerImpl`, `SqlValidator`, `SqlToRelConverter`,
  planner package docs, and test fixtures.

## Risks / Open Questions

- The new docs must stay broad enough to be reusable without duplicating the
  public `site/_docs` material.
- The reviewed docs should mention representative entrypoints without turning
  into a package catalog.

## Outcomes

- Added the reviewed docs `docs/ai/knowledge/calcite-mental-model.md` and
  `docs/ai/knowledge/testing-and-fixtures.md`.
- Trimmed the reviewed docs further and removed the validator-specific reviewed
  doc to keep the base consistently cross-cutting.
- Removed the three session-shaped validator candidate archives instead of
  keeping redundant bug-shaped notes.
- Verified the final `docs/ai` tree has no architecture directory and only the
  intended foundational reviewed docs.

# ExecPlan: Subquery deep-dive doc structural cleanup

Last Updated: 2026-03-23
Status: done

## Goal

Improve the reading order of
`docs/ai/knowledge/subquery-and-decorrelation-pipeline.md` without changing
the underlying technical guidance.

## Scope / Non-Goals

- In scope: section ordering, transitions, and grouping of closely related
  planner-program guidance.
- Out of scope: new planner claims, new executor contracts, or Calcite code
  changes.

## Validation Commands

- `rg -n '^## |^### ' docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify the updated heading flow.
  Result: pass
- `git diff --check -- .agents/PLANS.md docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify docs-only formatting after the structural cleanup.
  Result: pass

## Outcomes

- Added a short reading-order guide near the top of the doc.
- Collapsed caller-specific behavior and custom-program guidance into one
  operational section.
- Moved the `RuleCollection` sequencing guidance next to the custom-program
  discussion so program-composition advice is contiguous.

# ExecPlan: DataFusion direct-bridge architecture clarification

Last Updated: 2026-03-23
Status: done

## Goal

Clarify the recommended long-term architecture for teams that serialize
Calcite plans directly to DataFusion physical plans and want robust subquery
and correlation handling.

## Scope / Non-Goals

- In scope: document the ownership boundary between Calcite, the adapter
  layer, and DataFusion execution.
- Out of scope: DataFusion code changes or a new Calcite planner recipe.

## Evidence

- DataFusion logical references are name/qualifier based via
  `datafusion_common::Column` and `DFSchema`.
- DataFusion physical planning resolves logical named columns to ordinals.
- DataFusion's physical planner still does not directly execute
  `LogicalPlan::Subquery(_)`, and its physical expression planner still
  rejects raw subquery and outer-reference expressions.

## Validation Commands

- `git diff --check -- .agents/PLANS.md docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify docs-only formatting after the architecture clarification.
  Result: pass

## Outcomes

- Added a concrete `direct physical bridge` architecture subsection to the
  DataFusion part of the deep-dive doc.
- Made the recommendation explicit: keep Calcite as semantic owner, normalize
  to explicit positional bridge nodes, lower supported shapes directly, and
  execute residual apply/correlate surfaces with custom DataFusion extension
  execs.

# ExecPlan: Decorrelation costing and top-down migration guidance

Last Updated: 2026-03-23
Status: done

## Goal

Extend the subquery/decorrelation deep-dive doc with:

- an explicit statement of how current Calcite relates decorrelation to cost
- a concrete migration plan for evaluating and potentially adopting
  `TopDownGeneralDecorrelator`

## Scope / Non-Goals

- In scope: current Calcite behavior, rollout guidance, fallback guidance, and
  readiness signals for teams with a custom executor.
- Out of scope: planner code changes or a custom cost-based decorrelation
  implementation.

## Evidence

- `Programs.standard()` runs subquery removal and decorrelation before the main
  cost-based planner pass.
- `forceDecorrelate` is a boolean gate, not a cost-based chooser.
- `Correlate` has row-count and self-cost behavior if preserved.
- `TopDownGeneralDecorrelator` remains opt-in and still documents a hybrid
  stability path for join subqueries.
- `new-decorr.iq` and related tests still encode the main top-down stress
  areas to watch during rollout.

## Validation Commands

- `git diff --check -- .agents/PLANS.md docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`
  Purpose: verify docs-only formatting after adding the costing and migration
  sections.
  Result: pass

## Outcomes

- Added a `Decorrelation and costing` section to the deep-dive doc.
- Added a concrete `TopDownGeneralDecorrelator` migration subsection with
  rollout stages, readiness signals, and fallback guidance.
