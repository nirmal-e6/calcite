# Contributor Notes

## Purpose

This directory stores contributor-focused working knowledge that is useful
across repeated Calcite sessions but is too detailed or too internal for the
main project docs.

Keep this directory small. Add notes only when they capture durable knowledge
that is likely to help future work.

Process guidance, branch hygiene, and post-session update rules live in
`AGENTS.md`. This directory is only for the reusable knowledge itself and for
lightweight navigation.

## Current notes

### SQL validation and name resolution

- [sql-validation/README.md](./sql-validation/README.md)
  - Core subsystem guide for validator flow, row types, scopes, ambiguity,
    clause-local front-end boundaries, and debugging strategy.
- [sql-validation/duplicate-output-name-ambiguity.md](./sql-validation/duplicate-output-name-ambiguity.md)
  - Issue archive for the duplicate-output-name ambiguity fix through
    `SELECT *`.
- [sql-validation/grouped-expression-equivalence-typed-null.md](./sql-validation/grouped-expression-equivalence-typed-null.md)
  - Issue archive for grouped-expression equivalence when validator paths
    diverge on typed-`NULL` representation.
- [sql-validation/pivot-measure-expressions.md](./sql-validation/pivot-measure-expressions.md)
  - Issue archive for richer `PIVOT` measure expressions, clause-local
    parser/validator/`sql2rel` boundaries, and the filtered-aggregate semantic
    model.
