# Calcite Codex Usage

## Core idea

- Use an explicit Calcite skill first.
- Keep `AGENTS.md` for repo-wide rules and `SKILL.md` as the canonical task
  contract.
- Use `docs/ai/USAGE.md` as the concise human quick-reference.
- Inspect current code, tests, and docs in this repo before assuming Calcite
  behavior.

## Most common flows

- Does this SQL work today? Use `$calcite-query-support-check`.
- Something is wrong or regressed. Use `$calcite-bug-root-cause` and inventory
  adjacent affected surfaces before editing.
- Add or change a function, operator, or callable SQL surface. Use
  `$calcite-function-or-operator-work`.
- Add or change a planner rule or optimization. Use
  `$calcite-optimization-or-rule-work`.
- Need an end-to-end map before deciding. Use `$calcite-research-deep-dive`.
- Patch is understood and needs upstream review hardening on touched surfaces
  and direct fallout. Use `$calcite-pr-ready`.
- Session produced reusable lessons. Use `$calcite-knowledge-capture`.
- Add or materially change a repo skill. Use
  `$skill-creator -> $calcite-workflow-sync`, then
  `$calcite-workflow-routing-audit` if boundaries or descriptions changed
  materially.

## Minimal prompt style

- Start with the skill name.
- Give the exact SQL, repro, diff, or research question.
- Add the context that changes behavior: dialect, conformance, Babel, planner
  settings, runtime path, or target tests.
- For `$calcite-pr-ready`, mention touched behavior surfaces and changed
  expectations or goldens when known.
- For style or formatting questions, prefer `./gradlew style`; IntelliJ code
  style is only partial help.

Examples:

- `$calcite-query-support-check does this SQL work under Babel? ...`
- `$calcite-bug-root-cause diagnose this validator regression; repro: ...`
- `$calcite-pr-ready audit this patch, touched surfaces, and validation set ...`
- `$skill-creator create or update the repo skill calcite-foo under .agents/skills/calcite-foo/, then $calcite-workflow-sync to refresh help and maintenance docs; if boundaries changed materially, recommend $calcite-workflow-routing-audit`

## When to use Plan mode

- Use Plan mode for large, ambiguous, or multi-step work where you want a
  decision-complete plan before edits.
- Stay in normal mode for most focused skill invocations.

## Skill quick reference

| Skill | Use for | Bring | Default |
| --- | --- | --- | --- |
| `$calcite-query-support-check` | support verdict and first failing stage | exact SQL and context | analysis-first |
| `$calcite-bug-root-cause` | wrong behavior or regression diagnosis plus adjacent-surface inventory | repro plus expected vs actual | analysis-first |
| `$calcite-function-or-operator-work` | function, operator, or callable feature work | target SQL and semantics | implementation-when-clear |
| `$calcite-optimization-or-rule-work` | planner transform or rule placement | current vs desired plan behavior | implementation-when-clear |
| `$calcite-research-deep-dive` | end-to-end understanding and invariants | research question and scope | analysis-first |
| `$calcite-pr-ready` | upstream review hardening on touched surfaces | diff plus validation evidence and changed expectations | analysis-first |
| `$calcite-knowledge-capture` | route lessons into docs/ai | findings plus evidence | analysis-first |

## Maintenance quick reference

| Skill | Use for | Default |
| --- | --- | --- |
| `$calcite-workflow-help` | concise help for the current workflow layer | analysis-first |
| `$calcite-workflow-cleanup` | low-risk workflow doc and skill cleanup | direct low-risk edits |
| `$calcite-workflow-sync` | sync workflow docs after workflow changes | direct low-risk sync |
| `$calcite-workflow-routing-audit` | skill-boundary and prompt-quality audit | audit-then-tighten |
| `$calcite-workflow-retrospective` | repeated workflow-friction root cause | report-first |

## After-the-work flow

- Usual chain: `bug-root-cause -> pr-ready -> knowledge-capture`
- Use `pr-ready` only when the patch is already understood or implemented.
- For larger patches, `pr-ready` should review touched behavior surfaces in
  surface buckets instead of line-by-line inventory.
- New or edited comments should follow nearby Calcite style and explain
  invariants, rationale, or non-obvious behavior instead of restating code.
- Before calling a non-trivial upstream patch ready for handoff, end with
  `./gradlew clean build` unless the user explicitly scoped validation
  differently.
- Use `knowledge-capture` only for lessons worth keeping beyond the immediate
  patch.

## Maintenance

- `docs/ai/MAINTENANCE.md` owns maintenance policy and cadence.
- `docs/ai/USAGE.md` stays the concise quick-reference and common-flow layer.
- Maintenance skills are explicit-only meta skills. Inspect current repo files
  first and do not touch Calcite production code or tests.
- Use `$calcite-workflow-help` for a short summary of the current setup.
- Standard skill-authoring flow:
  `$skill-creator -> $calcite-workflow-sync -> optional calcite-workflow-routing-audit`.
- Use `$calcite-workflow-sync` after adding, removing, renaming, or
  materially changing a skill or workflow-level doc.
- Use `$calcite-workflow-routing-audit` when the primary deliverable is prompt
  coverage, a routing matrix, or boundary tightening.
- Use `$calcite-workflow-cleanup` when the primary deliverable is low-risk
  workflow-layer alignment after the cleanup target is already clear.
- Use `$calcite-workflow-retrospective` when the same workflow mistake or
  correction has happened more than once.
