# Reviewed Knowledge

## Purpose and scope

This directory holds the smallest reviewed docs layer for Calcite. Keep only
cross-cutting, stable guidance that helps with first-pass orientation.

## Why this matters

Calcite is broad. A small base reduces repeated orientation work without
turning `docs/ai` into a second code manual.

## Current docs

- `calcite-mental-model.md`: the main query pipeline and ownership boundaries.
- `subquery-and-decorrelation-pipeline.md`: the end-to-end map for subquery
  lowering, rule-driven rewrite, both decorrelators, and recommended planner
  program usage.
- `testing-and-fixtures.md`: the main test surfaces and when to start with each.

## Keep out

- issue diaries
- layer-specific deep dives unless they are clearly worth the maintenance cost
- workflow instructions that belong in `AGENTS.md` or skill docs
