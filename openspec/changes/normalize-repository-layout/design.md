# Design: Normalize Repository Layout and Documentation

## Overview
This change creates the documentation and repository-layout foundation for the larger SafeVault refactor. It keeps source code in place while making the project's written map reliable enough for incremental implementation work.

## Target Documentation Layout
- Root directory: keep only entrypoint documents and tool memory, such as `README.md`, `AGENTS.md`, `AI_RULES.md`, `Android_rules.md`, `CLAUDE.md`, and `task.md`.
- `docs/`: canonical project documentation.
- `docs/android/`: Android architecture, UI, build, and platform integration docs.
- `docs/backend/`: cross-repository backend architecture, service ownership, API integration notes, and root-level maintainer guidance.
- `docs/api/`: API contracts, schema notes, synchronization behavior, key-management references.
- `docs/security/`: security architecture, crypto migration, rollback, and sensitive-data handling.
- `docs/operations/`: deployment, migration, rollback, and maintenance runbooks.
- `docs/plans/`: historical or future implementation plans.
- `docs/changelog/`: release and migration changelogs.
- `openspec/`: active and archived spec-driven change records.
- `safevault-backend/docs/`: backend-repository-local operations that must travel with the backend repository if it is split, pushed, deployed, or built independently.

## Backend Documentation Routing
Backend docs must follow this split:

- Use `safevault-backend/docs/` for backend-local deployment, Docker Compose, Spring profiles, Maven commands, environment variables, operational runbooks that assume the backend repository as the working directory, and documents required when the backend is pushed or reviewed independently.
- Use `docs/backend/` for root-project architecture context, backend module/service ownership, Android-to-backend integration guidance, cross-component diagrams, and refactor decisions that are only meaningful in the full SafeVault repository.
- Use `docs/api/` for stable API contracts, endpoint behavior, schema notes, synchronization behavior, and DTO-facing documentation consumed by Android or external clients.
- If a backend document fits more than one location, keep the canonical version where its primary commands and ownership live, then add a short pointer from the other location instead of duplicating content.

## Repository Boundary Policy
`safevault-backend/` is currently a nested Git repository referenced by the root repository as a gitlink. This change must document that status explicitly before any package or source refactor starts.

Acceptable outcomes:
- Keep it as a nested repository and document the two-repo workflow.
- Convert it to a formal submodule in a later, dedicated proposal.
- Merge it into the root repository in a later, dedicated proposal.

This proposal only records the current decision and makes the workflow visible.

## Migration Strategy
1. Build a document inventory with current location, audience, status, and intended target.
2. Mark each document as canonical, historical, duplicate, stale, or generated.
3. Move documents in small batches with link updates.
4. Replace stale root-level documents with concise pointers when keeping history is useful.
5. Preserve OpenSpec archives and current specs.
6. Update `task.md`, `AGENTS.md`, and `openspec/project.md` as the canonical map changes.

## Safety Rules
- No recursive deletion commands.
- No bulk deletion.
- Keep behavioral code untouched.
- If a document contains questionable historical claims about security, crypto, API behavior, or architecture, verify against code before promoting it to canonical status.
- Do not remove generated directories as part of documentation layout work unless the user explicitly asks for that cleanup.
- Generated and build-directory policy is documentation-only in this change: document what should be ignored, preserved, regenerated, or manually cleaned, but do not clean those directories as part of this proposal.

## Verification
- Confirm moved or rewritten docs have valid relative links where practical.
- Confirm no Java, XML, SQL, Gradle, Maven, or resource behavior files changed.
- Confirm root Git and backend Git status separately.
- Record unresolved stale-doc questions in `task.md`.
