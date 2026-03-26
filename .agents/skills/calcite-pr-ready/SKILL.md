---
name: calcite-pr-ready
description: >-
  Use when a Calcite change is already understood or implemented and the job is
  to make it ready for upstream review. Do not use to discover root cause,
  decide support status, design a new feature or rule, or archive session
  lessons. Provide the diff, intended invariant or root cause, validation
  commands and results, and any compatibility concerns. Success is a concise
  patch summary, exact validation evidence, touched-surface reviewer-risk
  findings, repo-hygiene status for final handoff, and explicit follow-ups.
---

# Calcite PR Ready

Inspect the current diff, changed tests, and current repo rules before calling
something ready.

## Operating style

- Owns: review-readiness, validation coverage, hygiene checks, reviewer-risk
  reduction, and concise patch narrative.
- User provides: the diff, why the change is correct, and what validation has
  already run.
- Default: analysis-first. This skill audits and tightens; it does not replace
  diagnosis or design.
- Scope the audit to the patch's touched files, touched behavior surfaces, and
  direct fallout. Do not turn `pr-ready` into unrelated cleanup of nearby code.
- For larger patches, report by touched behavior surface buckets rather than a
  line-by-line inventory.
- Use repo-owned style signals first: `./gradlew style`, Checkstyle, and the
  surrounding Calcite code. IntelliJ formatting is advisory only.
- Report leftover temporary worktrees or side branches that still block a clean
  single-branch handoff, but do not mutate git topology yourself.

## Required inputs

- Final or near-final diff.
- Intended invariant or root-cause explanation.
- Validation commands and their results.
- Any compatibility, conformance, or rollout concerns.

## Expected outputs

- Concise patch summary.
- Exact validation evidence.
- Touched-surface review findings for dead code, fake hooks, unnecessary
  fallback paths, and symptom patches introduced by the patch.
- Touched-surface hygiene findings for no-op churn, verbose names or comments,
  redundant tests, and IDE/static-analysis issues introduced or exposed by the
  patch.
- Review of new or edited comments touched by the patch: keep comments that
  explain invariants, rationale, or non-obvious behavior, and remove comments
  that merely narrate the code.
- Review of changed `iq`, XML golden, `failFilter`, runtime-output, and other
  expectation-style tests touched by the patch or its direct fallout.
- Repo-hygiene report for final handoff: intended surviving branch, leftover
  temporary worktrees or side branches, and whether `$calcite-branch finalize`
  is still required before the patch is truly handoff-ready.
- Reviewer-risk note and explicit follow-ups.
- For larger patches, a surface-bucket review where each bucket states the
  intended invariant, key evidence, suspicious or under-explained deltas, and
  any deferred follow-up.

## Typical explicit invocation

- `$calcite-pr-ready audit this patch before upstream review ...`
- `$calcite-pr-ready summarize the diff and missing validation ...`
- `$calcite-pr-ready do a reviewer-risk pass on this Calcite fix ...`
