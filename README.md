# SafeVault

SafeVault is a course final project for an Android password manager with a separate Spring Boot backend repository nested at `safevault-backend/`.

## Repository Components

- Android client: `app/`
- Backend service: `safevault-backend/` (nested Git repository tracked as gitlink in root)
- Project docs: `docs/`
- Backend-local runbooks: `safevault-backend/docs/`
- OpenSpec changes/specs: `openspec/`

## Documentation Entrypoints

- Documentation index: `docs/README.md`
- Documentation layout baseline: `docs/documentation-layout.md`
- Refactor task log: `task.md`
- Agent collaboration memory: `AGENTS.md`

## Build and Test

- Android unit tests: `./gradlew.bat test`
- Android debug build: `./gradlew.bat :app:assembleDebug`
- Backend tests: from `safevault-backend/`, run `./mvnw.cmd test`

## Notes

- This repository contains historical docs under `docs/plans/legacy-root-docs/`.
- Follow explicit-path cleanup rules; avoid recursive or bulk deletion commands.
