---
name: calcite-knowledge-capture
description: >-
  Use when a Calcite session produced lessons and the job is to route them into
  reviewed knowledge, candidate notes, or discard decisions. Do not use for PR
  summaries, unresolved research, or storing issue diaries by default. Provide
  the findings, supporting evidence, and any existing docs that may already own
  the lesson. Success is a per-item classification, destination, rewrite or
  merge decision, and clear rationale for promote, hold, or discard.
---

# Calcite Knowledge Capture

Inspect current docs before creating or promoting anything.

## Operating style

- Owns: classifying session findings, choosing the right destination, and
  keeping reviewed knowledge separate from candidate notes.
- User provides: the findings, evidence, and any draft note or existing doc to
  compare against.
- Default: analysis-first. Edit docs only after the classification is clear.

## Required inputs

- Session findings worth preserving.
- Evidence from code, tests, docs, or experiments.
- Existing `docs/ai` content that may already own the lesson.

## Expected outputs

- Classification for each item: reviewed knowledge, candidate note, or discard.
- Destination file or directory.
- Rewrite, merge, promote, hold, or delete decision with rationale.

## Typical explicit invocation

- `$calcite-knowledge-capture classify these validator lessons ...`
- `$calcite-knowledge-capture decide what from this bug-fix session is durable ...`
- `$calcite-knowledge-capture merge or quarantine these notes ...`
