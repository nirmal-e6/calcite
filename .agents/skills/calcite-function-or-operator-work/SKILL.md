---
name: calcite-function-or-operator-work
description: >-
  Use when the task is adding, changing, or auditing a Calcite function,
  operator, callable surface, or closely related syntax and you need the
  semantic contract plus the right extension point. Do not use when the job is
  only current-support triage, debugging an already-established regression,
  planner-rule design, broad research, or PR review. Provide target SQL
  examples, legal and illegal forms, expected type and null behavior, and any
  dialect or conformance scope. Success is a clear feature classification,
  owning layers and extension point, gating notes, implementation decision, and
  focused tests.
---

# Calcite Function Or Operator Work

Inspect current code, tests, and docs before deciding how the feature should
fit.

## Operating style

- Owns: feature classification, semantic contract, owning-layer selection,
  gating decisions, and the narrowest implementation shape.
- User provides: target SQL, intended semantics, illegal forms, and any
  dialect or conformance expectations.
- Default: implementation-when-clear. If semantics or ownership stay unclear,
  narrow the question before editing.

## Required inputs

- Target SQL syntax with a few concrete examples.
- Expected semantics, including illegal cases and errors.
- Expected type behavior, null handling, and scope of support.

## Expected outputs

- Feature classification and semantic contract.
- Owning layers and extension point.
- Gating or compatibility notes.
- Decision: implement now, explore further, or escalate.
- Focused tests and validation.

## Typical explicit invocation

- `$calcite-function-or-operator-work add support for ...`
- `$calcite-function-or-operator-work design the Calcite surface for ...`
- `$calcite-function-or-operator-work audit where this operator should live ...`
