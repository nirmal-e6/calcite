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
