---
name: calcite-pr-ready
description: >-
  Use when a Calcite change is already understood or implemented and the job is
  to make it ready for upstream review. Do not use to discover root cause,
  decide support status, design a new feature or rule, or archive session
  lessons. Provide the diff, intended invariant or root cause, validation
  commands and results, and any compatibility concerns. Success is a concise
  patch summary, exact validation evidence, reviewer-risk findings, and
  explicit follow-ups.
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

## Required inputs

- Final or near-final diff.
- Intended invariant or root-cause explanation.
- Validation commands and their results.
- Any compatibility, conformance, or rollout concerns.

## Expected outputs

- Concise patch summary.
- Exact validation evidence.
- Missing-test or hygiene findings.
- Reviewer-risk note and explicit follow-ups.

## Typical explicit invocation

- `$calcite-pr-ready audit this patch before upstream review ...`
- `$calcite-pr-ready summarize the diff and missing validation ...`
- `$calcite-pr-ready do a reviewer-risk pass on this Calcite fix ...`
