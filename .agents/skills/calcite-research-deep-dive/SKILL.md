---
name: calcite-research-deep-dive
description: >-
  Use when the deliverable is understanding: an end-to-end map, invariants, and
  tradeoffs for a Calcite area that no narrower skill clearly owns yet. Do not
  use for bounded support checks, concrete bug diagnosis, clear feature or rule
  design, or PR cleanup. Provide the research question, the decision it should
  inform, scope boundaries, and known evidence or unknowns. Success is grounded
  findings, key entry points and invariants, open questions, and a
  recommendation for the next narrower skill or next step.
---

# Calcite Research Deep Dive

Inspect the current code, tests, and docs closest to the question before
generalizing.

## Operating style

- Owns: end-to-end mapping, invariant discovery, evidence gathering, and
  narrowing the task to the right next skill.
- User provides: research question, decision target, scope, and known unknowns.
- Default: analysis-first. Stop at grounded findings unless implementation has
  become clearly owned and justified.

## Required inputs

- Research question.
- Decision or deliverable it should inform.
- Scope boundaries.
- Known evidence, unknowns, or time budget.

## Expected outputs

- Concise findings summary.
- Key entry points, invariants, and tradeoffs.
- Open questions that still matter.
- Recommendation for the next narrower skill or next action.

## Typical explicit invocation

- `$calcite-research-deep-dive map how this Calcite area works end to end ...`
- `$calcite-research-deep-dive compare the likely owners for this behavior ...`
- `$calcite-research-deep-dive gather the invariants around this subsystem ...`
