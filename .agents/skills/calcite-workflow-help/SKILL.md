---
name: calcite-workflow-help
description: >-
  Use when the job is a concise help summary of the current Codex workflow
  setup in this repo, including common repo controls, explicit invocations,
  implicit branch-start behavior, and multi-skill flows. Do not use when the
  primary job is to edit the workflow layer, audit routing quality, or do
  actual Calcite diagnosis, implementation, research, or support checking.
  Provide the workflow area, task shape, or skill set you want summarized.
  Success is a concise help-style summary of the current skills, canonical
  docs, repo controls, explicit invocations, and common handoff flows.
---

# Calcite Workflow Help

Inspect current repo workflow docs and skill files before summarizing
anything.

## Operating style

- Owns: concise help for the current workflow layer, including canonical docs,
  common repo controls, explicit invocations, implicit branch-start behavior,
  and common multi-skill flows.
- Scope: repo workflow docs and skills only; do not diagnose Calcite behavior
  or touch production code or tests.
- Inspect: `AGENTS.md`, `docs/ai/USAGE.md`, `docs/ai/MAINTENANCE.md`,
  and `.agents/skills/**/SKILL.md`.
- Default: explicit-only maintenance help. Keep the response brief and
  operational. If the user wants workflow-layer edits, hand off to
  `calcite-workflow-cleanup` or `calcite-workflow-routing-audit`.

## Required inputs

- The workflow area, task shape, or request to summarize the current setup.
- Any specific skill, doc, or flow the user wants highlighted.

## Expected outputs

- Concise summary of the current workflow setup.
- Most common repo controls and explicit invocations.
- Common multi-skill flows and handoffs.
- Pointers to the owning docs or skills.

## Typical explicit invocation

- `$calcite-workflow-help summarize the current Calcite workflow setup`
- `$calcite-workflow-help show the common explicit skill invocations`
- `$calcite-workflow-help give me the quick-reference flow for support checks versus bug diagnosis`
- `$calcite-workflow-help show the standard flow for creating or updating a repo skill`
