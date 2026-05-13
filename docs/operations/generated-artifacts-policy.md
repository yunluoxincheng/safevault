# Generated Artifacts and Safe Cleanup Policy

Last updated: 2026-05-13

## Generated Directories

- Android generated outputs:
  - `android/.gradle/`
  - `android/build/`
  - `android/app/build/`
- Backend generated outputs:
  - `server/target/`

## Required Handling

- Treat these directories as build artifacts.
- Recreate them through normal build commands (`gradlew`, `mvnw`) rather than manual reconstruction.
- Do not rely on generated artifacts as long-term source of truth.

## Safe Cleanup Rules

- Cleanup is manual and explicit-path only.
- Delete one file path at a time when needed.
- Never use bulk or recursive deletion commands.
- Prefer documenting cleanup steps rather than performing broad filesystem cleanup in refactor passes.

## Verification Guidance

- Documentation/layout-only changes should not depend on deleting generated directories.
- Before and after refactor batches, verify root Git status.
