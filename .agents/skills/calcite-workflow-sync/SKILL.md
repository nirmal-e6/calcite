---
name: calcite-workflow-sync
description: >-
  Use when a skill was added, removed, renamed, or materially changed, when a
  workflow-level doc changed, or when AGENTS skill-routing hints changed and
  the job is to keep the workflow layer internally consistent. Do not use when
  only normal task knowledge changed, when only Calcite production code or
  tests changed, or when the primary deliverable is prompt coverage or a
  routing matrix. Provide the workflow-level change, affected files if known,
  and any intended routing or policy shift. Success is refreshed workflow help
  and maintenance docs, any small AGENTS routing-hint update that is actually
  needed, stale-reference findings, and a concise changelog-style summary.
---

# Calcite Workflow Sync

Inspect current workflow docs, skill contracts, and skill metadata before
editing anything.

## Operating style

- Owns: post-change workflow sync so skills, help, maintenance docs, and small
  routing hints stay consistent with the current repo state.
- Scope: `AGENTS.md`, `docs/ai/USAGE.md`, `docs/ai/MAINTENANCE.md`,
  `.agents/skills/**/SKILL.md`, and `.agents/skills/**/agents/openai.yaml`;
  never touch Calcite production code or tests.
- Default: explicit-only sync pass. Apply low-risk workflow-layer edits
  directly, print a concise changelog-style summary, and recommend
  `$calcite-workflow-routing-audit` if routing boundaries changed materially.
  Common pairing: run this right after `$skill-creator` creates or materially
  updates a repo skill.

## Required inputs

- The workflow-level change or current diff to sync.
- Affected skill names, renamed files, or changed workflow docs, if known.
- Any intended routing, policy, or cadence change, if known.

## Outputs / success criteria

- `docs/ai/USAGE.md` matches the current skills and common multi-skill flows.
- `docs/ai/MAINTENANCE.md` matches the current maintenance policy and cadence.
- `AGENTS.md` gets only a small routing-hint update if one is actually needed.
- Stale references, dead examples, renamed skills, contradictions, and
  duplicated workflow guidance are fixed or explicitly flagged.
- A concise changelog-style summary of updates is printed.
- A recommendation to run `$calcite-workflow-routing-audit` is made when
  routing boundaries changed materially.

## Typical explicit invocation

- `$calcite-workflow-sync sync the workflow layer after $skill-creator updated calcite-foo`
- `$calcite-workflow-sync sync the workflow layer after adding calcite-workflow-sync`
- `$calcite-workflow-sync refresh help and maintenance docs after renaming a skill`
- `$calcite-workflow-sync align AGENTS, USAGE, and maintenance docs after workflow-level routing changes`
