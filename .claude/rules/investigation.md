---
globs: ["core/src/**/*.java"]
description: Investigation protocol for bug fixes in the SQL validator, scope resolution, and name resolution layers.
---

Before editing any code for a bug fix, always:

1. Inspect existing tests first
2. Identify the invariant that should hold
3. Determine the architectural layer that should own the rule (row-type derivation, namespace construction, scope lookup, ambiguity detection, ORDER BY / alias resolution)
4. Explain whether the bug is due to: loss of information, enforcement in the wrong layer, inconsistent representation across code paths, or a deeper architectural mismatch
5. State the root cause and the smallest root-cause fix