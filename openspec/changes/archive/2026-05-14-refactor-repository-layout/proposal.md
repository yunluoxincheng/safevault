## Why

SafeVault is moving toward WSL-based development, but the current repository layout mixes Android project files, cross-project documentation, OpenSpec state, and a nested backend Git repository at the same root. This makes clone/setup instructions, IDE opening paths, Git ownership, and future automation harder than necessary.

## What Changes

- **BREAKING**: Reorganize the repository into a monorepo-style layout with `android/` for the Android client and `server/` for the Spring Boot backend.
- **BREAKING**: Convert the backend from a nested independent Git repository into a snapshot import managed by the root SafeVault repository.
- Move the existing Android project files, including the `app/` module and Android Gradle wrapper/build files, under `android/`.
- Move the existing `safevault-backend/` contents under `server/` without preserving backend commit history inside the root repository.
- Keep cross-project files such as `docs/`, `openspec/`, `task.md`, root `README.md`, and agent/project guidance at the repository root.
- Update documentation, OpenSpec context, Git guidance, build commands, and path references to the new layout.
- Decide whether root governance files currently ignored by Git, including `openspec/` and `task.md`, should become committed monorepo source-of-truth files or remain local-only guidance.
- Add a pre-import review gate for tracked backend secrets, certificates, binary configuration, and environment-specific files before copying them into `server/`.
- Treat the old backend remote repository as retired after migration; it may be deleted or left as an external historical artifact, but future development should use the root SafeVault repository.
- Preserve runtime behavior, package names, API contracts, database migrations, and security semantics.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `project-structure`: Replace the nested backend repository policy with a root-managed monorepo layout policy, define the new canonical runtime directories, and update backend-local documentation routing from `safevault-backend/docs/` to `server/docs/`.

## Impact

- Affected paths: root Gradle files, `app/`, `safevault-backend/`, `docs/`, `openspec/project.md`, `openspec/specs/project-structure/spec.md`, `task.md`, root README/guidance files, and any scripts or documentation that reference old paths.
- Affected workflows: Android Studio/IntelliJ project opening, Android Gradle commands, backend Maven commands, Git status checks, clone/setup instructions, and WSL copy/development guidance.
- Not affected: Android package namespace, backend Java package namespace, REST/WebSocket contracts, database schema/migrations, cryptographic behavior, auth/session behavior, and runtime feature behavior.
