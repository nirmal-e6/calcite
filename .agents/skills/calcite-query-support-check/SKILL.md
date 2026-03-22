---
name: calcite-query-support-check
description: >-
  Use when the question is whether a SQL shape works in current Calcite and
  which stage fails first. Do not use when the behavior is already known to be
  wrong and needs root-cause debugging, the goal is feature design or
  implementation, or the task is broad research or PR cleanup. Provide the
  exact SQL, expected outcome, and relevant dialect, conformance, Babel, fun,
  operator-table, or runtime context. Success is a stage-by-stage verdict,
  first failing stage, support classification, and a minimal test or harness
  suggestion.
---

# Calcite Query Support Check

Inspect current parser, validator, sql2rel, planner, and test coverage before
calling something supported or unsupported.

## Operating style

- Owns: support verdict, first failing stage, and the recommended next step.
- User provides: exact SQL text, expected outcome, and the context that could
  change parser, validator, or runtime behavior.
- Default: analysis-first. Do not slide into implementation unless the user
  changes the job.

## Required inputs

- Exact SQL text.
- Expected outcome.
- Relevant dialect, conformance, parser, Babel, fun, operator-table, or
  runtime context.

## Expected outputs

- Stage-by-stage verdict.
- First failing stage.
- Support classification.
- Minimal test shape or harness suggestion.

## Typical explicit invocation

- `$calcite-query-support-check does this SQL work today? ...`
- `$calcite-query-support-check classify the first failing stage for ...`
- `$calcite-query-support-check check whether this needs Babel or conformance ...`
