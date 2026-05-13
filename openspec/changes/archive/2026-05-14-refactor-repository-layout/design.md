## Context

SafeVault currently has two Git ownership boundaries: the root repository for the Android client, cross-project documentation, and OpenSpec state, and a nested `safevault-backend/` repository for the Spring Boot backend. The project is preparing to move day-to-day development into WSL, where a single lower-case monorepo layout will make clone/setup commands, IDE opening paths, and future automation easier to reason about.

The target layout is:

```text
safevault/
  android/
  server/
  docs/
  openspec/
  task.md
  README.md
  AGENTS.md
```

The backend will be imported as a snapshot, not as a history-preserving subtree. Its prior history can remain available through the old remote until the owner deletes that repository, but the root SafeVault repository becomes the only active development remote after migration.

## Goals / Non-Goals

**Goals:**

- Move Android project files under `android/`.
- Move backend project files under `server/`.
- Import backend tracked source/configuration files into `server/` without importing the nested `.git` directory, generated outputs, caches, or local dependency stores.
- Keep the root repository as the single source of truth for future development.
- Update documentation, OpenSpec context, build instructions, and path references so maintainers can work from the new layout.
- Resolve whether currently ignored governance files such as `openspec/` and `task.md` become committed root repository files or remain local-only.
- Keep Android and backend builds independently runnable from their runtime directories.
- Preserve application behavior, package names, API contracts, security behavior, and database migrations.

**Non-Goals:**

- Preserve backend commit history inside the root repository.
- Redesign Android packages, backend packages, APIs, database schemas, authentication, encryption, or sync behavior.
- Introduce a root-level unified Gradle/Maven build orchestration layer.
- Delete the remote `safevault-backend` repository as part of local implementation.
- Delete the old local `safevault-backend/` directory with recursive or bulk delete commands.
- Import secrets, private certificates, binary environment files, generated outputs, caches, or local dependency repositories without an explicit review decision.

## Decisions

### Use `android/` and `server/` as lower-case runtime roots

Lower-case names avoid casing mistakes in WSL/Linux, CI scripts, documentation, and IDE path references. `android/` owns the Android Gradle wrapper, settings, root Android build files, and `app/` module. `server/` owns the Spring Boot Maven wrapper, backend source, Docker Compose configuration, backend-local docs, and backend runtime configuration.

Alternative considered: `Android/server`. This keeps a more visible product label for the Android client, but it is less idiomatic for Linux paths and makes case-sensitive environments easier to trip over.

### Use snapshot import for backend

The backend contents will be imported into `server/` as regular files tracked by the root repository. The implementation should copy/import backend tracked files from the backend repository index, not blindly move the entire directory tree. The nested `.git` metadata, build outputs such as `target/`, caches, local dependency repositories, and environment-local files are excluded unless explicitly reviewed and approved as source files.

The old local `safevault-backend/` directory is not recursively deleted by the agent as part of implementation. After `server/` is verified, cleanup is either left as a manual user step or performed only after separate explicit confirmation using safe one-path operations that comply with the repository deletion rules.

Alternative considered: history-preserving merge through subtree/filtering. That would keep backend `git blame` history in the monorepo, but it adds operational complexity that is not worth it for the current project priorities.

Alternative considered: keep backend as a submodule. That preserves separate ownership, but it keeps the two-repository friction the change is meant to remove.

### Keep cross-project governance at the root

`docs/`, `openspec/`, `task.md`, root README/guidance files, and agent/project memory stay at the repository root because they describe the full SafeVault system rather than a single runtime. Backend-local operations documentation remains under `server/docs/` after the move.

Current ignore rules may keep `openspec/` and `task.md` out of root Git tracking. The migration must make an explicit decision: either adjust root ignore/tracking rules so these files become committed monorepo governance, or document that they remain local-only operational guidance. The implementation should not silently rely on ignored governance files while presenting them as committed source-of-truth artifacts.

### Gate tracked sensitive and binary backend files

Before importing backend tracked files, the implementation must inventory tracked files that look like secrets, certificates, keystores, binary configuration, or environment-specific credentials. For example, a tracked `.p12` file requires a decision before import: keep only if it is a safe test/development artifact, replace with an example placeholder and documentation, or rotate/remove it in a separate security cleanup. This proposal does not itself authorize weakening secret handling or committing new private material.

### Keep build wrappers local to runtime roots

Android commands should run from `android/`, and backend commands should run from `server/`. This avoids inventing a root build layer and keeps existing Gradle/Maven behavior close to current project expectations.

## Risks / Trade-offs

- Backend history is not available through root repository `git log` after the snapshot import. Mitigation: document this explicitly and rely on the old backend remote only as an external historical artifact until it is deleted.
- Moving wrappers and build files can break relative paths. Mitigation: update settings/build references in the same implementation slice and verify Android compile/tests from `android/`.
- Snapshot import can accidentally copy nested Git metadata, generated outputs, caches, or local-only files. Mitigation: import tracked backend files intentionally and exclude `.git`, generated outputs, caches, and local dependency repositories by rule.
- Tracked backend binary/sensitive files may be carried into the monorepo without review. Mitigation: perform a pre-import tracked secrets/binary inventory and record the decision for each risky file.
- Governance artifacts may remain ignored while docs imply they are committed. Mitigation: explicitly decide and document whether `openspec/` and `task.md` are tracked source-of-truth files or local-only guidance.
- Documentation may retain stale `app/` or `safevault-backend/` paths. Mitigation: inventory path references with search before and after the move, then update canonical docs and entrypoints.
- IDE project opening paths will change. Mitigation: document that Android Studio should open `android/` and backend IDE/import flows should open `server/`.
- Remote deletion is irreversible. Mitigation: local implementation only retires references; actual remote deletion remains a manual repository-owner action after monorepo verification.

## Migration Plan

1. Confirm both current repositories are clean enough to migrate and record any pre-existing untracked local-only files.
2. Decide whether `openspec/` and `task.md` should be committed governance files or remain local-only under current ignore rules.
3. Inventory backend tracked files for secrets, certificates, keystores, binary configuration, and environment-specific files; record keep/replace/remove/defer decisions before import.
4. Create the `android/` and `server/` runtime roots.
5. Move Android project files into `android/`, preserving file contents and minimizing unrelated formatting changes.
6. Import backend tracked source/configuration files from `safevault-backend/` into `server/` as a snapshot, excluding nested `.git`, generated outputs, caches, local dependency stores, and files rejected by the sensitive/binary review.
7. Leave the old local `safevault-backend/` directory cleanup as a manual or separately confirmed step; do not recursively delete it during implementation.
8. Update build settings, documentation, OpenSpec context/specs, and path references.
9. Verify Android from `android/` with unit tests and debug compile where feasible.
10. Verify backend from `server/` with Maven tests where feasible.
11. Use Git diff/status from the root repository as the authoritative change review.
12. After successful local verification and push, delete or retire the old backend remote manually if desired.

Rollback before commit is to restore from Git using non-destructive review first and only perform any destructive reset/cleanup if the user explicitly requests it. Rollback after commit is to revert the monorepo migration commit from the root repository.

## Open Questions

- Whether root `README.md` should be a concise monorepo entrypoint only, or also include detailed setup instructions for both runtime roots.
- Whether old backend remote deletion should happen immediately after successful push or after one confirmed WSL clone/build cycle.
