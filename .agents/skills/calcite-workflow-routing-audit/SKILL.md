---
name: calcite-workflow-routing-audit
description: >-
  Use when the job is to audit Calcite skill boundaries, trigger quality, and
  likely routing misfires, including prompt coverage or a routing matrix. Do
  not use when the task is product debugging, routine cleanup without a
  routing question, or repeated-friction root cause analysis. Provide the
  skills or workflow changes under audit and any known ambiguous prompts or
  misroutes. Success is a routing matrix, positive and negative prompt
  coverage per skill, overlap findings, and tightened routing or help text.
---

# Calcite Workflow Routing Audit

Inspect the current repo skill and help surface before changing any routing
text.

## Operating style

- Owns: skill-boundary audit, trigger-quality review, routing matrix creation,
  and low-risk routing-text tightening.
- Scope: all repo skills plus `AGENTS.md`, `docs/ai/USAGE.md`,
  and `docs/ai/MAINTENANCE.md`; never touch Calcite production code or tests.
- Default: explicit-only audit. Inspect current docs and skills first, then
  tighten descriptions, examples, and help text directly when the fix is
  low-risk. Use `calcite-workflow-cleanup` after the routing changes are
  decided if broader alignment work remains.

## Required inputs

- Skills or workflow changes under audit; if omitted, audit the current repo
  skill set.
- Any known misroutes, ambiguous prompts, or stale quick references.

## Expected outputs

- Routing matrix for the audited skills.
- At least 3 positive prompts and 2 negative prompts per skill.
- Overlap, ambiguity, and likely misfire findings.
- Direct routing-text or quick-reference updates when the current wording is
  stale.

## Typical explicit invocation

- `$calcite-workflow-routing-audit audit the current repo skill boundaries`
- `$calcite-workflow-routing-audit generate positive and negative prompt coverage for every repo skill`
- `$calcite-workflow-routing-audit tighten stale routing text after adding a skill`
