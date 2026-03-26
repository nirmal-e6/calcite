# Calcite Codex Usage

## Core idea

- Use an explicit Calcite skill first.
- Keep `AGENTS.md` for repo-wide rules and `SKILL.md` as the canonical task
  contract.
- Use `docs/ai/USAGE.md` as the concise human quick-reference.
- Inspect current code, tests, and docs in this repo before assuming Calcite
  behavior.

## Most common flows

- When normal non-workflow implementation begins from `config/codex`, Codex
  may auto-start a local `fix/`, `research/`, or `stress/` branch from
  `main` via `$calcite-branch`.
- Need to collapse exploratory worktrees or side branches back to one clean
  final branch before local handoff. Use `$calcite-branch finalize`.
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
- Need a quick summary of the current workflow layer or common skill flows. Use
  `$calcite-workflow-help`.
- Want a local commit without pushing or upstream rewrite. Use
  `$calcite-commit`.
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
- For `$calcite-branch`, explicit invocation is still useful when you want to
  control branch timing or naming; specify whether you want to create, switch,
  or finalize, and give the branch kind, slug, target branch, or base override
  as needed.
- For `$calcite-pr-ready`, mention touched behavior surfaces and changed
  expectations or goldens when known.
- For `$calcite-commit`, give the local summary, and on `config/*` branches add
  the workflow operation when it is not obvious.
- For style or formatting questions, prefer `./gradlew style`; IntelliJ code
  style is only partial help.

Examples:

- `$calcite-query-support-check does this SQL work under Babel? ...`
- `$calcite-branch create fix/pivot-aggregate-expressions from main`
- `$calcite-branch finalize fix/pivot-aggregate-expressions and clean safe temporary worktrees`
- `$calcite-bug-root-cause diagnose this validator regression; repro: ...`
- `$calcite-pr-ready audit this patch, touched surfaces, and validation set ...`
- `$calcite-commit make a local workflow cleanup commit for these docs`
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

## Repo controls quick reference

| Skill | Use for | Default |
| --- | --- | --- |
| `$calcite-branch` | auto-start, create, switch, or explicitly finalize local `fix/`, `research/`, or `stress/` branches | auto-start from `config/codex` when edits begin; finalize stays explicit |
| `$calcite-commit` | create a local commit on the current branch | explicit-only, local-only, no push |

## Maintenance quick reference

| Skill | Use for | Default |
| --- | --- | --- |
| `$calcite-workflow-help` | concise help for the current workflow layer | analysis-first |
| `$calcite-workflow-cleanup` | low-risk workflow hygiene: concision, de-cluttering, stale quick references, and light repo-truth checks | direct low-risk edits |
| `$calcite-workflow-sync` | sync workflow docs after workflow changes | direct low-risk sync |
| `$calcite-workflow-routing-audit` | skill-boundary and prompt-quality audit | audit-then-tighten |
| `$calcite-workflow-retrospective` | repeated workflow-friction root cause | report-first |

## After-the-work flow

- Normal local Calcite flow:
  `config/codex -> task skill -> implicit $calcite-branch start if edits begin -> implementation -> $calcite-pr-ready -> explicit $calcite-branch finalize -> explicit $calcite-commit`
- Workflow-layer maintenance stays on `config/codex`; use
  `$calcite-commit` when you want a local workflow snapshot.
- Research and stress work usually branches from `main`, then uses
  explicit `$calcite-branch finalize` before the final explicit
  `$calcite-commit`, plus any user-managed push or upstream rewrite.
- Usual chain for normal Calcite work:
  `$calcite-bug-root-cause -> implicit branch start if needed -> implementation -> $calcite-pr-ready -> explicit branch finalize -> explicit commit -> $calcite-knowledge-capture`
- Use `$calcite-pr-ready` only when the patch is already understood or implemented.
- `$calcite-pr-ready` may report that the patch is not fully handoff-ready yet if extra
  temporary worktrees or side branches still need finalization.
- For larger patches, `$calcite-pr-ready` should review touched behavior surfaces in
  surface buckets instead of line-by-line inventory.
- New or edited comments should follow nearby Calcite style and explain
  invariants, rationale, or non-obvious behavior instead of restating code.
- Use `.agents/PLANS.md` only for active or recently handed-off multi-step
  work; after durable outcomes are promoted to `knowledge/`, quarantined in
  `candidates/`, or discarded, retire the finished ExecPlan.
- Before calling a non-trivial upstream patch ready for handoff, end with
  `./gradlew clean build` unless the user explicitly scoped validation
  differently.
- Before local handoff on normal Calcite work, aim for `git status` clean on
  one surviving branch, with temporary exploratory worktrees or side branches
  removed unless intentionally preserved.
- Auto-branch start should happen only when implementation is actually about to
  begin from `config/codex`, not during pure analysis or workflow maintenance.
- `calcite-commit` is local-only. Final upstream push and any later rewrite to
  `[CALCITE-####] <summary>` stay user-controlled.
- Use `$calcite-knowledge-capture` only for lessons worth keeping beyond the immediate
  patch.

## Maintenance

- `docs/ai/MAINTENANCE.md` owns maintenance policy and cadence.
- `docs/ai/USAGE.md` stays the concise quick-reference and common-flow layer.
- Use `$calcite-workflow-help` for a short summary of the current setup.
- Standard skill-authoring flow:
  `$skill-creator -> $calcite-workflow-sync -> optional $calcite-workflow-routing-audit`.
- Use `$calcite-workflow-sync` after adding, removing, renaming, or
  materially changing a skill or workflow-level doc.
- Use `$calcite-workflow-routing-audit` when the primary deliverable is prompt
  coverage, a routing matrix, or boundary tightening.
- Use `$calcite-workflow-cleanup` when the primary deliverable is low-risk
  workflow hygiene once the cleanup target is already clear.
- Use `$calcite-workflow-retrospective` when the same workflow mistake or
  correction has happened more than once.
