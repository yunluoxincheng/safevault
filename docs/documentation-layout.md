# SafeVault Documentation Layout Baseline

Last updated: 2026-05-13

## 1. Canonical Documentation Homes

- Root (`/`): project entrypoints and collaborator memory only.
- `docs/`: canonical project documentation for maintainers.
- `docs/android/`: Android architecture, build, and platform integration docs.
- `docs/backend/`: cross-repository backend architecture and ownership docs.
- `docs/api/`: stable API contracts, endpoint behavior, schema notes, sync semantics.
- `docs/security/`: security architecture and crypto migration/rollback guidance.
- `docs/operations/`: deployment, migration, rollback, and maintenance runbooks.
- `docs/plans/`: implementation plans, historical decisions, and archived planning notes.
- `docs/changelog/`: release and migration changelogs.
- `server/docs/`: backend-local operations that must stay with backend working directory commands.
- `openspec/`: proposed and archived specification-driven changes.

## 2. Root Markdown Inventory (Audience and Freshness)

| File | Audience | Status | Action |
| --- | --- | --- | --- |
| `README.md` | New contributors | Active | Keep at root as entrypoint |
| `AGENTS.md` | AI collaborators | Active | Keep at root; synchronize with `task.md` |
| `AI_RULES.md` | AI collaborators | Active | Keep at root |
| `Android_rules.md` | AI collaborators | Active | Keep at root |
| `CLAUDE.md` | AI collaborators | Active | Keep at root |
| `task.md` | Refactor tracking | Active | Keep at root |
| `implementation_plan.md` | Historical plan | Removed from root | Canonical copy is `docs/plans/legacy-root-docs/implementation_plan.md` |
| `JAVA_17_CHANGES_SUMMARY.md` | Historical migration notes | Removed from root | Canonical copy is `docs/plans/legacy-root-docs/JAVA_17_CHANGES_SUMMARY.md` |
| `JAVA_17_UPGRADE.md` | Historical migration notes | Removed from root | Canonical copy is `docs/plans/legacy-root-docs/JAVA_17_UPGRADE.md` |
| `SafeVault 开发文档.md` | Legacy high-level Chinese docs | Removed from root | Canonical copy is `docs/plans/legacy-root-docs/SafeVault 开发文档.md` |
| `SafeVault 开发文档前端.md` | Legacy frontend Chinese docs | Removed from root | Canonical copy is `docs/plans/legacy-root-docs/SafeVault 开发文档前端.md` |
| `SafeVault 开发文档后端.md` | Legacy backend Chinese docs | Removed from root | Canonical copy is `docs/plans/legacy-root-docs/SafeVault 开发文档后端.md` |
| `图标映射清单.md` | Legacy design reference | Removed from root | Canonical copy is `docs/plans/legacy-root-docs/图标映射清单.md` |

## 3. docs/ and Backend docs Inventory Summary

### `docs/`

- Active core docs:
  - `docs/directory-standards.md`
  - `docs/project-structure-reorganization.md`
  - `docs/security-architecture.md`
- API docs: `docs/api/*.md` remain canonical for API/schema/sync contracts.
- Operations docs: rollback and migration runbooks in `docs/operations/`.
- Historical plans: dated implementation notes in `docs/plans/`.
- Legacy root documents: preserved only in `docs/plans/legacy-root-docs/`.
- User docs: `docs/user-guide/`, `docs/faq/`, and `docs/USER-DOCS-INDEX.md`.

### `server/docs/`

- `server/docs/deployment/server-deployment.md`
- `server/docs/modularization-plan.md`
- `server/docs/service-boundaries.md`

These stay backend-local because they assume backend working directory commands and backend repo ownership.

## 4. Backend Documentation Routing Rule

- Place backend-local deployment/build/runtime-operation docs in `server/docs/`.
- Place cross-repository backend architecture/ownership docs in `docs/backend/`.
- Place stable API contracts and schema-facing docs in `docs/api/`.
- If overlap exists, keep one canonical file and place a short pointer in non-canonical locations.

## 5. Monorepo Repository Policy

Current state on 2026-05-13:

- The root SafeVault repository tracks both `android/` and `server/` directly.
- `server/` is not a nested Git repository; root Git is the single source of truth.
- The previous `safevault-backend/` nested Git repository is retired.

Workflow guidance:

- All changes (Android, backend, docs, OpenSpec) commit in the root repository.
- Android commands run from `android/`.
- Backend commands run from `server/`.

## 6. Generated/Build Directory and Safe Cleanup Policy

Generated/build directories currently observed:

- Android: `android/.gradle/`, `android/build/`, `android/app/build/`
- Backend: `server/target/`

Policy:

- Treat these directories as generated artifacts.
- Do not version generated outputs unless intentionally publishing artifacts.
- Regenerate by normal build commands.
- Cleanup must be manual, explicit-path, and non-recursive; never use bulk or recursive deletion commands.

## 7. Open Documentation Debt

- Historical Chinese documents are preserved under `docs/plans/legacy-root-docs/` and should be reviewed before reuse because parts may describe superseded architecture assumptions.
- Root-level compatibility pointers for legacy documents were removed on 2026-05-07 to keep the repository root limited to active entrypoints and collaborator memory.
