# Workflow Maintenance

## Core Rules

- Any workflow-layer change must leave the workflow layer internally
  consistent.
- `SKILL.md` is canonical for skill behavior.
- `docs/ai/USAGE.md` is the concise human quick-reference.
- `docs/ai/MAINTENANCE.md` is the maintenance policy.
- These maintenance skills are explicit-only meta skills. Invoke them by name
  when maintaining the workflow layer.
- Inspect current repo files before summarizing or editing anything.
- Do not touch Calcite production code or tests from maintenance work.
- Keep the workflow layer concise and operational.
- Workflow-layer maintenance stays on `config/codex`.
- `calcite-branch` and `calcite-commit` are explicit repo controls, not
  maintenance skills.
- `.agents/PLANS.md` is for active or recently handed-off multi-step work, not
  retained session history.
- `docs/ai/knowledge/` contains only reviewed, generalized knowledge.
- `docs/ai/candidates/` is a quarantine area for unreviewed or
  session-derived notes.

## Maintenance Skills

- `$calcite-workflow-help`: summarize the current workflow setup, common repo
  controls, explicit invocations, implicit branch-start behavior, and common
  multi-skill flows.
- `$calcite-workflow-sync`: sync the workflow layer after workflow-level
  changes so skills, help, and maintenance docs match current repo state.
- `$calcite-workflow-routing-audit`: audit skill boundaries, trigger quality,
  and likely routing misfires.
- `$calcite-workflow-retrospective`: root-cause repeated workflow friction and
  choose the smallest durable fix.
- `$calcite-workflow-cleanup`: clean up and align the workflow layer once the
  cleanup target is already clear.

## Standard Flow

- To add or materially change a repo skill, use
  `$skill-creator -> $calcite-workflow-sync`.
- If the skill change also changed boundaries or descriptions materially, then
  run `$calcite-workflow-routing-audit`.
- For workflow-layer changes, stay on `config/codex`.
- `calcite-branch` may auto-start non-workflow local branches from `main` when
  implementation begins on `config/codex`, and later explicitly finalize
  normal work back to one clean surviving branch.
- Use `$calcite-commit` only when you want an explicit local snapshot; do not
  turn it into an automatic final step.

## Mandatory Triggers

- After adding, removing, renaming, or materially changing a skill: run
  `$calcite-workflow-sync`.
- After changing a workflow-level doc or AGENTS skill-routing hints: run
  `$calcite-workflow-sync`.
- After changing skill boundaries or descriptions: run
  `$calcite-workflow-routing-audit`.
- After repeated friction: run `$calcite-workflow-retrospective`.
- Every 3-5 substantial sessions or before a new burst of work: run
  `$calcite-workflow-cleanup`.
- During workflow cleanup, trim completed ExecPlans whose outcomes are already
  captured elsewhere.
- After substantial normal work: run `$calcite-knowledge-capture`.

## Cadence

- Once manual maintenance is stable, optional automation may run cleanup on a
  schedule.
