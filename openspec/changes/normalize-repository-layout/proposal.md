# Change: Normalize Repository Layout and Documentation

## Why
SafeVault currently has useful documentation spread across root Markdown files, `docs/`, backend docs, OpenSpec records, and assistant memory files. Some historical documents are stale, duplicated, or encoding-damaged in terminal output. Before large code movement starts, maintainers need a canonical documentation layout and repository boundary policy so future refactor work does not create more drift.

## What Changes
- Define canonical homes for root project docs, Android docs, backend docs, API docs, security docs, operations docs, plans, changelogs, and assistant memory.
- Define a strict routing rule between root-level backend documentation under `docs/backend/` and backend-repository-local documentation under `safevault-backend/docs/`.
- Create an inventory-driven migration plan for stale, duplicate, or misplaced documents.
- Clarify whether `safevault-backend/` remains a nested Git repository and how the root repository should reference it.
- Document generated/build directories and refactor-safe cleanup rules.
- Update existing indexes and memory files after documentation moves.

## Non-Goals
- Do not change Android or backend runtime behavior.
- Do not move Java/XML/SQL source files as part of this change.
- Do not delete documents in bulk. Any removals must follow the repository rule of one explicit path at a time.
- Do not decide API, schema, crypto, or UI behavior changes here.

## Impact
- Affected specs: `project-structure`
- Affected files: root Markdown docs, `docs/**`, `safevault-backend/docs/**`, `openspec/project.md`, `AGENTS.md`, `task.md`
- Verification: documentation inventory review, link/reference checks where practical, root and backend Git status checks
