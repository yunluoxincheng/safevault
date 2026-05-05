# CLAUDE.md

Claude Code agents should use `AGENTS.md` as the main project memory for this repository.

## SafeVault Snapshot

- Android app: `app/`, Java 17, XML layouts, ViewBinding, MVVM direction.
- Backend: `safevault-backend/`, Spring Boot 3.5.9, Java 17, Maven, PostgreSQL, Redis.
- Security-sensitive data includes passwords, master passwords, tokens, private keys, verification codes, salts, auth tags, and share payloads.

## Required Workflow

- Read `AGENTS.md` and `task.md` before making structural changes.
- Update `task.md` after meaningful exploration, planning, edits, or verification.
- Use the current OpenSpec skill files in `.codex/skills/openspec-*` for proposals, apply work, and archiving.
- Large refactors, package migrations, API/schema changes, and security architecture changes require an OpenSpec proposal before implementation.

## Hard Constraints

- Do not batch-delete files or directories.
- Do not use recursive deletion commands.
- Do not revert user changes unless explicitly asked.
- Check both the root Git repository and the nested `safevault-backend/` Git repository before committing.
