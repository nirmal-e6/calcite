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
  for approval, and a more internally consistent doc and skill surface that is
  less verbose, less cluttered, and easier to navigate.
---

# Calcite Workflow Cleanup

Inspect the live workflow layer before editing anything, and keep changes
scoped to docs, skills, and skill metadata.

## Operating style

- Owns: workflow-layer hygiene, including duplication removal, stale-example
  refresh, verbosity reduction, clutter trimming, big-picture restoration, and
  doc/skill alignment.
- Scope: `AGENTS.md`, `.agents/PLANS.md` if present, `.agents/skills/**/SKILL.md`,
  `.agents/skills/**/agents/openai.yaml`, `docs/ai/USAGE.md`,
  `docs/ai/MAINTENANCE.md`, `docs/ai/knowledge/**`, and
  `docs/ai/candidates/**`.
- Default: explicit-only cleanup pass. Apply low-risk workflow-layer edits
  directly; list higher-risk changes separately for approval. Verify short,
  operational, repo-dependent claims in touched workflow files against the
  current repo when that check is cheap and local. Use
  `calcite-workflow-routing-audit` first when the primary question is routing
  quality, use `calcite-workflow-retrospective` first when repeated
  corrections still need a root cause, and use `calcite-knowledge-capture`
  when the primary question is whether deep-dive or lesson docs belong in
  `knowledge/`, `candidates/`, or nowhere.
- Keep cleanup low-risk. Do not turn it into a broad re-audit of subsystem
  deep dives or a hidden research pass.
- For `docs/ai/knowledge/**`, default to hygiene only when the file is already
  in scope, already being edited, or obviously suspicious. Do not reopen every
  deep dive by default just because it contains repo-dependent claims.
- Never touch Calcite production code or tests.

## Cleanup priorities

- Keep durable policy and stable workflow invariants.
- Cut over-verbose wording, repeated caveats, and duplicated guidance unless
  they prevent a real mistake.
- Remove issue-shaped, session-shaped, or over-fitted examples unless they are
  durable policy or the clearest current example.
- Prefer big-picture flow first, then only the detail needed to use the
  workflow safely.

## Required inputs

- Cleanup goal or suspected stale area.
- Any known duplication, dead skill, outdated example, stale quick reference,
  over-verbose area, or over-specific workflow text.
- Approval constraints for higher-risk cleanup, if any.

## Expected outputs

- Low-risk cleanup edits or a no-change verdict.
- Higher-risk change list for approval.
- Stale or dead skill findings.
- Workflow hygiene findings for verbosity, clutter, repeated guidance, or
  over-specific wording when present.
- Light repo-truth check results for touched operational claims when those
  checks were in scope.
- Durable-knowledge versus quarantine corrections where needed.

## Typical explicit invocation

- `$calcite-workflow-cleanup clean up the workflow layer after recent doc churn`
- `$calcite-workflow-cleanup refresh USAGE and MAINTENANCE after a routing audit`
- `$calcite-workflow-cleanup identify dead skills and move unreviewed notes out of knowledge`
- `$calcite-workflow-cleanup trim workflow docs that have become too verbose or issue-shaped`
- `$calcite-workflow-cleanup do a low-risk hygiene pass and verify touched quick-reference claims against the current repo`
