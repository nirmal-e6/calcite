# Calcite Mental Model

## Purpose and scope

This doc is the minimal internal map of Calcite's query pipeline. It is for
placing ownership quickly, not for teaching each subsystem in depth.

## Why this matters

The same SQL shape passes through parser, validator, `sql2rel`, planner, and
adapters. Many wrong fixes land in the layer that shows the symptom first
instead of the layer that owns the invariant.

## Core flow

1. Parser admits syntax and produces a `SqlNode` tree.
2. Validator assigns SQL meaning: names, types, scopes, and legality.
3. `sql2rel` lowers validated SQL into logical relational algebra.
4. Planner rewrites equivalent `RelNode` plans and manages traits and
   conventions.
5. Adapters and conventions decide what can be pushed into an engine and where
   converters are needed.

## Boundaries that stay useful

- Parse success does not imply validation success.
- Validator owns SQL-visible meaning. Later rel naming or planning behavior
  does not redefine SQL semantics.
- `sql2rel` is the handoff from SQL semantics to algebra shape.
- Planner rules should preserve semantics, not invent new ones.
- Engine-specific behavior usually belongs in conventions and rules, not in
  generic front-end code.

## Practical ownership heuristics

- Syntax only: parser.
- SQL meaning or legality: validator.
- Wrong logical plan from correct validated SQL: `sql2rel`.
- Wrong rewrite, trait, or convention choice: planner.
- Wrong pushdown or engine-specific operator choice: adapter rules and
  conventions.

## Related deep dives

- For the full subquery and decorrelation pipeline, including `RexSubQuery`,
  subquery-removal rules, `RelDecorrelator`, `TopDownGeneralDecorrelator`, and
  planner program sequencing, see
  `docs/ai/knowledge/subquery-and-decorrelation-pipeline.md`.
