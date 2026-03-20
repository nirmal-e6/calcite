# Apache Calcite instructions

Optimize for:
1. logically correct behavior
2. structurally correct design
3. robust root-cause fixes
4. upstreamable changes
5. smallest patch consistent with the above

Rules:
- Do not preserve a flawed internal design just because it is existing behavior.
- Do not add special cases, exceptions, or query-shape-specific logic unless they are provably the correct abstraction boundary.
- Prefer fixing the semantic/structural layer that owns the invariant.
- A slightly larger patch is preferred over a smaller patch if the larger one removes the root cause and avoids future tech debt.
- Still avoid unrelated refactors; broaden the patch only when needed to make the design correct.
- Keep representation, propagation, grouped-expression equivalence, and explicit lookup/ambiguity as separate concerns.
- Keep parser admission, clause-local AST bookkeeping, validator legality, and sql-to-rel lowering as separate concerns.
- Do not let rel-layer internal naming conventions drive validator SQL semantics.

Branch hygiene:
- Keep reusable Codex knowledge capture on `config/codex`.
- Keep upstreamable source changes on `fix/*` branches.
- Do not mix knowledge-only markdown updates into an upstream fix branch unless they are intentionally part of the PR.

Investigation requirements:
- Inspect existing tests first.
- Read `notes/README.md` and the relevant subsystem note before creating a second documentation structure.
- Prefer extending an existing subsystem note over creating a parallel note tree.
- Identify the invariant that should hold for duplicate output names, star expansion, grouped-expression comparison, and explicit name resolution.
- Determine where that invariant should live:
    - row-type derivation
    - namespace construction
    - grouped-expression comparison
    - scope lookup
    - ambiguity detection
    - ORDER BY / alias resolution
    - clause-local validator rules
    - sql-to-rel lowering
- Explain whether the current bug is due to:
    - loss of information
    - enforcement in the wrong layer
    - inconsistent representation across code paths
    - or a deeper architectural mismatch

Before editing, provide:
1. the correct invariant
2. where the current implementation violates it
3. the architectural layer that should own the rule
4. the root cause
5. the smallest root-cause fix, even if not the smallest textual diff
6. the test classes to extend

After editing, provide:
1. root cause
2. exact structural fix
3. why this is the correct layer for the rule
4. why smaller alternatives would create tech debt or brittle behavior
5. tests added/changed
6. remaining edge cases, if any

Communication and explanation:
- Explain solutions in plain English first, then map them to Calcite classes and methods.
- Preserve technical rigor; do not simplify by hiding architectural distinctions.
- Whenever mentioning a class or method, explain its role in one sentence.
- If the bug is exposed by parser casing or case-insensitive lookup, explain whether parsing/lookup is actually wrong or only revealing a comparison-layer bug.

Knowledge capture:
- Read `notes/sql-validation/README.md` before changing validator/name-resolution behavior.
- After major debugging work:
  1. decide what is durable subsystem knowledge versus session-specific history
  2. update the smallest existing core note that owns the invariant
  3. update the notes index if a new archive is added
  4. archive the specific issue separately under the subsystem directory
  5. keep core notes free of one-off probes, commands, branch history, and vendor-only quirks unless they change the reusable model
  6. do not duplicate the same explanation across multiple files
  7. if a clause-local feature was involved, capture the parser / AST / validator / sql-to-rel boundary explicitly
  8. keep reusable knowledge on `config/codex`, not on `fix/*`
