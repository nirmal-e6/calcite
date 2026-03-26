---
name: calcite-commit
description: >-
  Use when you want an explicit local commit in this repo under the current
  workflow conventions. Do not use for pushing, rebasing, squashing, or final
  upstream commit rewrite. Provide the commit scope and desired summary, and on
  `config/*` branches include the workflow operation when it is not obvious.
  Success is the intended files staged, a local subject that matches the branch
  family, and the commit created or a clear stop reason.
---

# Calcite Commit

Inspect the current branch, staged diff, and worktree before creating a local
commit.

## Operating style

- Owns: explicit local staging and commit only.
- Scope: repo-local commits only; never push, rebase, squash, or rewrite the
  final upstream commit for the user.
- Default: explicit-only repo control. On `config/*`, use
  `[workflow/<operation>] <imperative summary>`. On `fix/*`, use a plain
  imperative one-line local subject. On `research/*` and `stress/*`, use
  `[local/research] <imperative summary>` or
  `[local/stress] <imperative summary>`.
- Prefer committing what is already staged. If nothing is staged, stage only
  the obvious current-task diff; stop if the scope is mixed or unclear.
- Local-only. The user owns any later rewrite to `[CALCITE-####] <summary>`
  and any push or upstream handoff.

## Required inputs

- Commit scope or files, if staging help is needed.
- Desired summary.
- Workflow operation when committing on `config/*` if it is not obvious.

## Expected outputs

- Staging decision and scope.
- Final local subject.
- Commit created or stop reason.

## Typical explicit invocation

- `$calcite-commit make a local workflow retrospective commit for these docs`
- `$calcite-commit locally snapshot this fix branch with subject remove redundant uniquify fallback`
- `$calcite-commit create a local stress commit for generated subquery edge cases`
