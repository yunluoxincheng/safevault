# SafeVault

SafeVault is an Android password manager with a Spring Boot backend, organized as a monorepo.

## Repository Layout

```
safevault/
  android/        Android client (Gradle, Java 17)
  server/         Spring Boot backend (Maven, Java 17)
  docs/           Cross-project documentation
  openspec/       OpenSpec specs and change proposals
  README.md
  AGENTS.md
  CLAUDE.md
```

- **Android client**: `android/` — open with Android Studio
- **Backend service**: `server/` — open with IntelliJ IDEA
- **Project docs**: `docs/`
- **Backend-local runbooks**: `server/docs/`
- **OpenSpec changes/specs**: `openspec/`

## Documentation Entrypoints

- Documentation index: `docs/README.md`
- Documentation layout baseline: `docs/documentation-layout.md`
- Refactor task log: `task.md`
- Agent collaboration memory: `AGENTS.md`

## Build and Test

- Android unit tests: from `android/`, run `./gradlew test`
- Android debug build: from `android/`, run `./gradlew :app:assembleDebug`
- Backend tests: from `server/`, run `./mvnw test`
- Backend local dependencies: from `server/`, run `docker compose up -d postgres redis`

## Notes

- This repository contains historical docs under `docs/plans/legacy-root-docs/`.
- The previous nested `safevault-backend/` Git repository is retired. Backend history is available through the old remote at `https://github.com/yunluoxincheng/safevault-backend.git` until manually deleted.
- Follow explicit-path cleanup rules; avoid recursive or bulk deletion commands.
