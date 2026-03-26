# ExecPlan Template

Use this template for long, multi-step work that is currently active.

Treat the plan as a living document. Update it as understanding changes, not
just at the end.

`.agents/PLANS.md` is not a permanent archive. Keep only active or recently
handed-off work here. Once a plan reaches `done`, route durable outcomes to
`docs/ai/knowledge/`, quarantine still-raw notes in `docs/ai/candidates/`, or
discard them, then remove the completed plan from this file.

The same format should work for bug fixes, function or operator work,
optimizations or rules, and research tasks.

## Usage Rules

- Keep milestones current.
- Record the routing choice when it is non-obvious: which workflow or skill
  owns the task, and which nearby alternatives were rejected.
- Record the exact validation commands you actually ran.
- Log meaningful decisions when tradeoffs are resolved.
- Separate confirmed discoveries from open questions.
- End with concrete outcomes, not just activity.
- After completion, retire the plan instead of keeping a session diary here.

## Template

```md
# ExecPlan: <short task name>

Last Updated: YYYY-MM-DD
Status: planned | in_progress | blocked | done

## Goal

<What this work is trying to achieve.>

## Scope / Non-Goals

- In scope: <items>
- Out of scope: <items>

## Routing Choice

- Primary workflow / skill: <name>
- Nearby alternatives rejected: <name + short reason>

## Milestones

- [ ] M1: <milestone name>
  Target evidence: <what proves it is complete>
- [ ] M2: <milestone name>
  Target evidence: <what proves it is complete>
- [ ] M3: <milestone name>
  Target evidence: <what proves it is complete>

## Validation Commands

- `command here`
  Purpose: <why this command matters>
  Result: pending | pass | fail

## Decision Log

- YYYY-MM-DD: <decision made>
  Reason: <why this decision was chosen>

## Discoveries

- YYYY-MM-DD: <confirmed finding>
  Evidence: <tests, code paths, plans, docs, or experiments>

## Risks / Open Questions

- <risk or unresolved question>
- <what would change the plan>

## Outcomes

- <what was completed>
- <what remains>
- <recommended next step>
- <where durable lessons were routed, if any>
```
