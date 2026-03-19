---
description: Patch priorities and rules for all changes. Patches must be upstreamable to Apache Calcite.
---

## Priorities (in order)

1. Logically correct behavior
2. Structurally correct design
3. Robust root-cause fixes
4. Upstreamable changes
5. Smallest patch consistent with the above

## Rules

- Do not preserve a flawed internal design just because it is existing behavior.
- Do not add special cases, exceptions, or query-shape-specific logic unless they are provably the correct abstraction boundary.
- Prefer fixing the semantic/structural layer that owns the invariant.
- A slightly larger patch is preferred over a smaller one if it removes the root cause and avoids future tech debt.
- Avoid unrelated refactors; broaden the patch only when needed to make the design correct.
- Keep representation, propagation, and explicit lookup/ambiguity as separate concerns.
- Do not let rel-layer internal naming conventions drive validator SQL semantics.