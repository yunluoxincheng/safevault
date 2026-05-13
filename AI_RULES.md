# SafeVault AI Rules

This file is a short companion to `AGENTS.md`. If rules conflict, prefer `AGENTS.md` and `openspec/project.md`.

## General

- Use Java 17 across Android and backend.
- Keep documentation in sync with behavior; update `task.md` after meaningful work.
- Do not batch-delete files or directories.
- Do not revert user changes that are unrelated to the task.
- For architecture, API/schema, security, or large refactor work, use OpenSpec before implementation.

## Android

- Current app is native Android Java + XML + ViewBinding, not Compose.
- Current DI style is manual `ServiceLocator`; do not introduce Hilt without an approved proposal.
- Follow MVVM direction: `ui -> viewmodel -> model/service -> (network|security|crypto|data)`.
- Prefer existing managers/services over adding new global singletons.
- Keep UI code out of cryptographic key lifecycle, persistence, and network retry policy where practical.

## Backend

- Framework: Spring Boot 3.5.9, Maven, Java 17.
- Follow layered direction: `controller -> service -> repository/entity`.
- Controllers handle HTTP boundary, validation, and DTO mapping only.
- Services contain business logic, transactions, auth/security decisions, and notification orchestration.
- Repositories access persistence only.
- Use DTOs for API input/output; never expose JPA entities directly from controllers.
- Use global exception handling consistently.

## Security

- Treat passwords, master passwords, tokens, private keys, verification codes, salts, auth tags, and share payloads as sensitive.
- Do not log or document real secrets.
- Preserve TLS/JWT/KeyStore/biometric/FLAG_SECURE/clipboard protections unless an approved security proposal changes them.

## Testing

- Android: from `android/`, run `./gradlew test`; compile-sensitive changes: `./gradlew :app:assembleDebug`.
- Backend: from `server/`, run `./mvnw test`.
- OpenSpec changes: `openspec validate <change-id> --strict`.
