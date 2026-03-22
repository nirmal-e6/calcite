# Candidate Archive: Grouped-Expression Equivalence And Typed-`NULL`

Keep this in quarantine until it is either promoted into reviewed knowledge,
rewritten, or deleted.

## Session-shaped lessons worth preserving for now

- Name resolution can be correct while grouped-expression comparison is wrong.
- Parser casing often exposes representation bugs instead of causing them.
- Success that depends on shared mutable `SqlNode` history is not a valid
  semantic invariant.
- Grouped-expression equivalence belongs in the validator comparison layer.
- Do not broaden normalization rules without a pre-fix failing reproducer for
  the new case.

## Narrow verified bug family

- Same-level `GROUP BY alias`
- Case-preserving, case-insensitive identifier rewrite that causes divergence
- Typed-`NULL` representation mismatch within `CASE`
- Related reviewed guide: `docs/ai/knowledge/sql-validation.md`
