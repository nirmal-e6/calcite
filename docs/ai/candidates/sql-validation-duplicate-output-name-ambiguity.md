# Candidate Archive: Duplicate Output Names Through `SELECT *`

Keep this in quarantine until it is either promoted into reviewed knowledge,
rewritten, or deleted.

## Session-shaped lessons worth preserving for now

- Duplicate output names are a representation question first, not an ambiguity
  question first.
- `SELECT *` must be treated as positional propagation, not repeated
  name-based lookup.
- Explicit ambiguity belongs in scopes and explicit resolution paths,
  including `ORDER BY`.
- If a bug appears only in nested queries, inspect whether field identity was
  lost during propagation.
- If a narrower fix preserves a synthetic suffix such as `DEPTNO0`, test
  whether that still lets an outer explicit reference succeed when it should be
  ambiguous.
- Row-order-only diffs in Quidem should not be hand-cleaned without checking
  the actual execution path that produced the transcript.
- Related reviewed guide: `docs/ai/knowledge/sql-validation.md`
