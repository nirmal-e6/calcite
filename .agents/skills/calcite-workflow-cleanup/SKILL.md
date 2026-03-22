---
name: calcite-workflow-cleanup
description: >-
  Use when the job is to clean up and align Calcite's workflow layer itself
  after the target docs or skills are already clear. Do not use when the
  primary deliverable is prompt coverage or a routing matrix, when repeated
  workflow friction still needs root-cause analysis, or when the request is to
  modify Calcite production code or tests. Provide the cleanup goal, any
  suspected stale docs or skills, and any approval constraints. Success is a
  concise set of low-risk workflow-layer edits, a list of higher-risk changes
  for approval, and a more internally consistent doc and skill surface.
---

# Calcite Workflow Cleanup

Inspect the live workflow layer before editing anything, and keep changes
scoped to docs, skills, and skill metadata.

## Operating style

- Owns: workflow-layer cleanup, duplication removal, stale-example refresh,
  and doc/skill alignment.
- Scope: `AGENTS.md`, `.agents/PLANS.md` if present, `.agents/skills/**/SKILL.md`,
  `.agents/skills/**/agents/openai.yaml`, `docs/ai/USAGE.md`,
  `docs/ai/MAINTENANCE.md`, `docs/ai/knowledge/**`, and
  `docs/ai/candidates/**`.
- Default: explicit-only cleanup pass. Apply low-risk workflow-layer edits
  directly; list higher-risk changes separately for approval. Use
  `calcite-workflow-routing-audit` first when the primary question is routing
  quality, and use `calcite-workflow-retrospective` first when repeated
  corrections still need a root cause.
- Never touch Calcite production code or tests.

## Required inputs

- Cleanup goal or suspected stale area.
- Any known duplication, dead skill, outdated example, or stale quick
  reference.
- Approval constraints for higher-risk cleanup, if any.

## Expected outputs

- Low-risk cleanup edits or a no-change verdict.
- Higher-risk change list for approval.
- Stale or dead skill findings.
- Durable-knowledge versus quarantine corrections where needed.

## Typical explicit invocation

- `$calcite-workflow-cleanup clean up the workflow layer after recent doc churn`
- `$calcite-workflow-cleanup refresh USAGE and MAINTENANCE after a routing audit`
- `$calcite-workflow-cleanup identify dead skills and move unreviewed notes out of knowledge`
