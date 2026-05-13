# Design: Stabilize Core Vault Sync Flow

## Overview
This change makes the core password-manager loop explicit, testable, and repeatable. It should stabilize behavior before broader roadmap items such as Android vault boundary refactors, auth/key lifecycle hardening, CI, sharing protocol improvements, autofill polish, or project showcase documentation.

The work should preserve the current Java/XML Android app, Spring Boot backend, and zero-knowledge direction. The backend stores encrypted vault payloads and metadata needed for sync; plaintext password fields and private key material must remain client-controlled and must not appear in logs, API examples, tests, or documentation output.

## Canonical Flow
The target flow for this change is:

1. Register a user account.
2. Establish or verify the master password flow.
3. Initialize required local and backend-visible key metadata.
4. Log in and reach an unlocked vault state.
5. Create a password item.
6. Encrypt and save the item locally.
7. Push only encrypted vault data to the backend.
8. Lock or log out.
9. Log in again.
10. Pull encrypted vault records from the backend.
11. Decrypt records on the client and display the created item.

Implementation may split these into smaller slices, but each slice should preserve the end-to-end target and record any intentionally retained limitations.

## Current Architecture Constraints
Android dependency direction remains:

`ui -> viewmodel -> model/service -> (network|security|crypto|data)`

Backend dependency direction remains:

`controller -> service -> repository/entity`

This proposal may expose places where the current implementation still violates those directions, especially around vault UI orchestration or backend service cohesion. Fix only the boundary issues required to stabilize the core flow. Broader structural cleanup should remain in later roadmap changes such as `refactor-android-vault-boundaries`.

## Contract Areas
### Registration and Login
- Registration must create enough account state for first login and vault initialization.
- Login must persist access and refresh tokens according to existing `auth-refresh` behavior.
- Authentication failures must propagate to UI without swallowing retryable or terminal errors.
- Logout/token revoke behavior must not leave the app able to read decrypted vault data without unlock.

### Vault Initialization
- The implementation must define when DataKey, PasswordKey, DeviceKey, public keys, salts, and backend key metadata are created or verified.
- Repeated login after successful registration must not fail because initialization is partial.
- Initialization checks must distinguish missing local state from missing remote state and provide actionable failure behavior.

### Local Vault CRUD
- Local persistence must store encrypted sensitive vault fields.
- DAO or database access should remain behind repository/service/manager boundaries.
- Decrypted data may exist only in controlled unlocked flows and should be cleared or made inaccessible after lock/logout.

### Cloud Sync
- Sync endpoints and DTOs must be aligned between Android Retrofit models and backend request/response DTOs.
- Push must send ciphertext and metadata only, not plaintext secrets.
- Pull must return enough encrypted record data for client-side decryption and display.
- Retry behavior must be documented for authentication failure, network failure, conflict, malformed payload, and decryption failure.

### Relogin and Decryption
- A user must be able to relogin after lock/logout and recover the created vault item from synchronized encrypted data.
- Decryption failure must not corrupt local or remote records.
- Failure messages must not expose secrets, keys, tokens, salts, tags, or plaintext password fields.

## Implementation Strategy
1. Start with a read-only inventory of Android and backend classes/endpoints involved in the canonical flow.
2. Build a contract matrix that maps Android API models to backend DTOs and identifies gaps.
3. Stabilize the smallest broken flow segment first, preferring targeted fixes over broad refactors.
4. Add tests around corrected service/ViewModel behavior and backend service/controller contracts where practical.
5. Keep manual verification current after each meaningful flow improvement.
6. Update documentation only where it helps future maintainers repeat the verified flow.

## Data and Security Rules
- Never log master passwords, plaintext password entries, private keys, derived keys, JWTs, refresh tokens, verification codes, salts, GCM tags, or share packets.
- Do not weaken AndroidKeyStore, Argon2, AES-GCM, biometric, token refresh/revoke, `FLAG_SECURE`, clipboard auto-clear, or release security constraints.
- Backend DTOs and entities must not expose plaintext vault data.
- Flyway migrations are append-only; old migrations must not be edited.
- Test fixtures must use fake or synthetic secrets and avoid reusable real credentials.

## Verification Strategy
- Run `.\gradlew.bat test` after meaningful Android logic changes.
- Run `.\gradlew.bat :app:assembleDebug` after compile-sensitive Android changes.
- Run `.\mvnw.cmd test` from `safevault-backend/` after backend changes.
- Manually verify the canonical flow against a local backend and record exact pass/fail notes.
- Check both root Git status and nested `safevault-backend/` Git status before final implementation reporting.
