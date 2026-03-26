---
name: calcite-branch
description: >-
  Use when non-workflow Calcite work needs to leave `config/codex` for a local
  `fix/`, `research/`, or `stress/` branch rooted at `main`, or when that work
  later needs explicit finalization back to one clean surviving branch after
  exploratory worktrees or side branches. Implicit invocation is allowed only
  for start-of-work branching as implementation begins from `config/codex`.
  Finalize remains explicit. Do not use for workflow-layer changes that stay on
  `config/codex`, or for rebasing, pushing, or upstream branch management.
  Provide the branch action, branch kind or target branch, and any explicit
  base override. Success is a clean branch decision, the correct base or
  surviving branch, and a safe local branch/worktree state.
---

# Calcite Branch

Inspect the current branch, worktree state, and target base or surviving
branch before changing anything.

## Operating style

- Owns: local branch lifecycle for normal work branches, including start,
  switch, and finalize.
- Scope: repo-local branching and worktree cleanup only; no push, rebase,
  cherry-pick, or upstream branch management.
- Implicit policy: auto-start branching is allowed only when normal
  non-workflow work is about to move from analysis into real edits while still
  on `config/codex`. Finalize is explicit-only.
- Default: keep workflow-layer maintenance on `config/codex`; create `fix/`,
  `research/`, and `stress/` branches from `main` unless the user explicitly
  overrides the base. Finalize normal work onto one surviving branch with a
  clean `git status`.
- Do not auto-branch while still only analyzing, while branch intent is
  ambiguous, or when already on a suitable non-workflow work branch.
- When finalizing, remove temporary worktrees or side branches only when their
  state is already represented on the surviving branch. Stop instead of
  deleting anything ambiguous, dirty, or unique.
- Stop if the worktree is dirty in a way that would mix unrelated work into
  the branch action.

## Required inputs

- Branch action: create, switch, or finalize. Implicit invocation may use only
  create or switch for start-of-work branching.
- Branch kind when creating: `fix`, `research`, or `stress`.
- Short slug when creating.
- Target surviving branch when finalizing.
- Optional explicit base override.
- Optional list of temporary worktrees or side branches expected to be
  removed.

## Expected outputs

- Current branch and worktree check.
- Chosen base branch or surviving branch.
- Worktree inventory when finalizing.
- Branch name and whether it was created, switched, or finalized.
- Stop reason if branching or cleanup would mix unrelated or ambiguous state.

## Implicit-use guardrails

- Allowed implicitly only when:
  - the task is normal non-workflow Calcite work
  - the current branch is `config/codex`
  - implementation is about to begin
  - no suitable `fix/`, `research/`, or `stress/` branch is already active
- Not allowed implicitly for:
  - workflow-layer maintenance
  - pure analysis or review-only sessions
  - finalize cleanup
  - ambiguous branch choice or dirty mixed state

## Typical explicit invocation

- `$calcite-branch create fix/ambiguous-duplicate-columns from main`
- `$calcite-branch create stress/subquery-edge-cases from main`
- `$calcite-branch switch to fix/subquery-remove-right-side-multi-correlation`
- `$calcite-branch finalize fix/ambiguous-duplicate-columns and remove safe temporary worktrees`
