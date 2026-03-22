---
name: calcite-optimization-or-rule-work
description: >-
  Use when the task is a Calcite planner transformation, rule-placement
  decision, or planner-specific bad-plan investigation with planner ownership
  already in scope. Do not use when ownership is still unclear across layers,
  the question is only support status, the change is a callable surface, or the
  job is broad research or PR cleanup. Provide current and desired plan
  behavior, the semantic invariant to preserve, a reproducer query or plan, and
  relevant planner settings or traits. Success is a transformation contract,
  owning planner layer, overlap and risk assessment, implementation decision,
  and focused tests.
---

# Calcite Optimization Or Rule Work

Inspect current planner code, tests, and docs before proposing a rule change.

## Operating style

- Owns: transformation contract, preconditions, rule placement, interaction
  risk, and the narrowest planner-scoped implementation.
- User provides: current vs desired plan behavior, preserved semantics, and
  planner context.
- Default: implementation-when-clear. If the owner is still cross-layer,
  re-route before editing.

## Required inputs

- Current and desired plan behavior.
- Semantic invariant to preserve.
- Reproducer query or plan.
- Relevant planner settings, traits, or cost assumptions.

## Expected outputs

- Transformation contract and preserved invariant.
- Owning planner or rule layer.
- Overlap, termination, or interaction risk notes.
- Decision: implement, refine, bad fit, or escalate.
- Focused positive and negative tests.

## Typical explicit invocation

- `$calcite-optimization-or-rule-work design a rule for this missed transform ...`
- `$calcite-optimization-or-rule-work audit planner ownership for this bad plan ...`
- `$calcite-optimization-or-rule-work decide where this optimization belongs ...`
