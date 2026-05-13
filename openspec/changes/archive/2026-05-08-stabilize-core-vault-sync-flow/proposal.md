# Change: Stabilize Core Vault Sync Flow

## Why
SafeVault has grown from a course project into a security-sensitive Android password manager with a Spring Boot backend, local encrypted storage, cloud sync, key management, sharing, WebSocket notifications, and OpenSpec governance. The roadmap recommends stabilizing the primary password-manager loop before adding more advanced sharing, discovery, or packaging work.

The next priority is to prove that the core vault flow is reliable end to end:

`register -> master password setup -> key initialization -> login/unlock -> create password -> local encrypted save -> encrypted cloud sync -> lock/logout -> relogin -> pull encrypted records -> decrypt and display`

Without this stable loop, later work on Android layer boundaries, auth/key lifecycle, CI, sharing, autofill, and project presentation will be built on uncertain behavior.

## What Changes
- Define the canonical register, login, vault initialization, unlock, lock, sync push, sync pull, and relogin-decrypt flow.
- Inventory Android vault, auth, crypto/security, local persistence, and sync classes that participate in the flow.
- Inventory backend auth, private-key, vault sync, token, and persistence endpoints/services that participate in the flow.
- Compare Android API request/response models with backend DTOs and document required alignment.
- Stabilize encrypted vault item CRUD across local Room storage, client-side crypto boundaries, and backend zero-knowledge storage.
- Stabilize sync state and retry/error behavior for encrypted vault records.
- Add focused tests and a manual verification checklist for the core register-login-create-sync-relogin-decrypt path.
- Update relevant documentation so the verified core flow is repeatable by future maintainers.

## Non-Goals
- Do not introduce microservices, a gateway, service discovery, or distributed architecture.
- Do not redesign the Android UI or migrate XML/ViewBinding screens to Compose.
- Do not introduce Hilt, Kotlin, StateFlow, or a broad Android architecture migration.
- Do not redesign the password sharing protocol, friend/contact flows, QR/Bluetooth sharing, or WebSocket notification model.
- Do not weaken encryption, key derivation, AndroidKeyStore, biometric gates, token revocation, screenshot protection, or clipboard cleanup.
- Do not rewrite existing Flyway migrations; any schema change must be append-only and justified by the vault contract.
- Do not use this change as a catch-all for unrelated package moves or documentation cleanup.

## Impact
- Affected specs: `backend-integration`, `auth-refresh`, `project-structure`; a dedicated vault/sync spec may be added or expanded during implementation if no canonical spec currently covers the final contract.
- Affected Android code: `app/src/main/java/com/ttt/safevault/**` areas for auth/login, vault UI/ViewModels, local data, crypto/security managers, sync managers, Retrofit models, and service facades.
- Affected backend code: `safevault-backend/src/main/java/org/ttt/safevaultbackend/**` areas for auth, token refresh/revoke, private keys, vault records, sync services, DTOs, repositories, and tests.
- Affected documentation: roadmap follow-up notes, API/vault sync documentation, security/key lifecycle documentation if gaps are found, and `task.md` during implementation.
- Risk areas: user registration, first vault initialization, master password unlock, token refresh, local encrypted persistence, backend ciphertext storage, sync conflict behavior, relogin decryption, and manual verification reproducibility.
- Verification: Android `.\gradlew.bat test`, Android `.\gradlew.bat :app:assembleDebug`, backend `.\mvnw.cmd test`, and a documented manual end-to-end flow against a local backend.
