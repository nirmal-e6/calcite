---
name: calcite-bug-root-cause
description: >-
  Use when current Calcite behavior looks wrong or regressed and the first job
  is to prove the owning layer before patching. Do not use when the question
  is only support status, a new feature or planner-rule design, broad
  subsystem research, or PR cleanup. Provide a repro or failing query/test,
  expected vs actual behavior, and relevant dialect, conformance, or runtime
  context. Success is a minimized repro, owning-layer classification, root
  cause, plausible fix points, chosen fix point, and targeted validation.
---

# Calcite Bug Root Cause

Inspect current code, tests, and docs before concluding anything.

## Operating style

- Owns: reproducer reduction, invariant statement, owning-layer classification,
  fix-point selection, and test/validation scope.
- User provides: a failing SQL shape, test, stack trace, plan, or other
  concrete symptom plus expected behavior.
- Default: analysis-first. Implement only after the owner and fix shape are
  clear.

## Required inputs

- Exact or near-exact repro.
- Expected behavior and actual behavior.
- Relevant context such as dialect, conformance, Babel, parser settings, or
  execution path.

## Expected outputs

- Minimized repro.
- Owning-layer classification.
- Root cause and rejected symptom-patch alternatives.
- Chosen fix point and targeted validation.

## Typical explicit invocation

- `$calcite-bug-root-cause diagnose why this query regressed; repro: ...`
- `$calcite-bug-root-cause root-cause this validator failure from test ...`
- `$calcite-bug-root-cause find the owning layer for this wrong-result case ...`
