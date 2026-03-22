---
name: calcite-workflow-retrospective
description: >-
  Use when repeated workflow friction or repeated corrections suggest the
  workflow layer needs a durable fix. Do not use when the issue is a one-off
  mistake, when the primary deliverable is prompt coverage or a routing
  matrix, or when broad cleanup can proceed without failure analysis. Provide
  the repeated failure pattern, examples of the corrections, and any suspected
  misleading rule or skill. Success is a root-cause report, the smallest
  durable fix location, and optional targeted doc or skill updates.
---

# Calcite Workflow Retrospective

Inspect the current workflow rules and skill text before proposing fixes for
repeated friction.

## Operating style

- Owns: workflow-failure root cause, smallest durable fix selection, and
  targeted workflow updates after repeated friction.
- Scope: `AGENTS.md`, repo skills, `docs/ai/USAGE.md`,
  `docs/ai/MAINTENANCE.md`, and related workflow docs; never touch Calcite
  production code or tests.
- Default: explicit-only and report-first. Apply targeted doc or skill edits
  only when requested or when the fix is clearly low-risk after the report.
  Use `calcite-workflow-cleanup` only after the failure pattern and owning fix
  location are already clear.

## Required inputs

- Repeated failure pattern or repeated manual correction.
- At least two concrete examples or corrections.
- Any suspected misleading rule, skill, or doc.

## Expected outputs

- Root cause of the workflow failure.
- Fix location: `AGENTS.md`, a skill, `docs/ai/USAGE.md`,
  `docs/ai/MAINTENANCE.md`, or nowhere.
- Smallest durable fix and rationale.
- Optional targeted doc or skill updates.

## Typical explicit invocation

- `$calcite-workflow-retrospective analyze repeated workflow friction in recent sessions`
- `$calcite-workflow-retrospective find the smallest durable fix for this repeated correction`
- `$calcite-workflow-retrospective report whether this failure belongs in AGENTS, a skill, or maintenance docs`
