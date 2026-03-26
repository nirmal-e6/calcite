# AI Support Docs

This directory contains concise human-facing docs for the repo workflow layer.

The goal is to capture procedure and decision gates, not frozen subsystem
trivia or session history.

## Layout

- `USAGE.md`: quick-reference for common skill flows and prompt shape.
- `MAINTENANCE.md`: policy and cadence for workflow-layer upkeep.
- `knowledge/`: reviewed, generalized, reusable lessons.
- `candidates/`: quarantine for unreviewed or session-derived notes.

## Promotion Rules

- Keep `SKILL.md` canonical for skill behavior.
- Keep `USAGE.md` concise and task-oriented.
- Keep `MAINTENANCE.md` focused on maintenance policy and triggers.
- Put only reviewed and generalized lessons in `knowledge/`.
- Put anything unreviewed, issue-specific, or session-derived in `candidates/`
  until it is either promoted, rewritten, or deleted.
- After durable outcomes are promoted, quarantined, or discarded, retire the
  corresponding ExecPlan from `.agents/PLANS.md`.

## Long-Running Work

For active or recently handed-off multi-step work, use the living ExecPlan
template in [`../../.agents/PLANS.md`](../../.agents/PLANS.md). Do not use it
as a long-term session archive.
