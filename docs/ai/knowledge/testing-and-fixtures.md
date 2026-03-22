# Testing And Fixtures

## Purpose and scope

This doc maps the main test surfaces to the layers they own. Use it to choose
the first useful harness, not to catalog every fixture.

## Why this matters

Starting with the nearest owning test keeps failures small and avoids changing
goldens or planner expectations before the semantic owner is clear.

## Main test surfaces

- Parser tests: syntax admission and conformance-gated parsing.
- Validator tests: name resolution, types, row shape, and legality.
- `SqlToRelConverterTest`: lowering from validated SQL to logical rel.
- `RelOptRulesTest`: rule behavior, traits, and plan rewrites.
- Operator and broader SQL harnesses: callable behavior that spans multiple
  layers.

## Practical rules

- Start with the nearest layer and move outward only when that layer is already
  correct.
- Keep parser and validator conformance aligned when a test depends on dialect
  behavior.
- Treat `withExpand`, `withDecorrelate`, and planner knobs as ownership clues:
  they change different stages of the pipeline.
- Treat DiffRepository `_actual` output as evidence to inspect, not something
  to accept blindly.
