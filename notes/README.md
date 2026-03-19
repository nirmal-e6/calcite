# Contributor Notes

## Purpose

This directory stores contributor-focused working knowledge that is useful
across repeated Calcite sessions but is too detailed or too internal for the
main project docs.

Use these notes to:

- understand subsystem architecture before editing,
- reuse debugging strategies and invariants,
- and archive issue-specific lessons separately from core subsystem guidance.

Keep this directory small. Add notes only when they capture durable knowledge
that is likely to help future work.

## Current notes

### SQL validation and name resolution

- [sql-validation/README.md](./sql-validation/README.md)
  - Core subsystem guide for validator flow, row types, scopes, ambiguity, and
    debugging strategy.
- [sql-validation/duplicate-output-name-ambiguity.md](./sql-validation/duplicate-output-name-ambiguity.md)
  - Issue archive for the duplicate-output-name ambiguity fix through
    `SELECT *`.

## Maintenance rule

When a session produces reusable knowledge:

1. update the subsystem guide with durable invariants,
2. archive the specific issue separately,
3. avoid filling the subsystem guide with one-off commands or branch history.

