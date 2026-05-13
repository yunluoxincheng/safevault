## 1. Inventory
- [x] 1.1 Map Android classes involved in registration, login, vault initialization, unlock, lock/logout, vault CRUD, local encryption, and sync.
- [x] 1.2 Map backend controllers, services, DTOs, repositories, entities, security helpers, and Flyway migrations involved in auth, private keys, vault records, and sync.
- [x] 1.3 Map Android Retrofit/API models to backend request and response DTOs.
- [x] 1.4 Document current key lifecycle checkpoints for registration, first login, unlock, lock, logout, and relogin.
- [x] 1.5 Record current gaps, suspected contract mismatches, and high-risk files in `task.md`.

## 2. Core Contract
- [x] 2.1 Define the canonical register-login-vault-init-unlock flow.
- [x] 2.2 Define encrypted vault item create/read/update/delete behavior.
- [x] 2.3 Define sync push and sync pull request/response contracts.
- [x] 2.4 Define sync state model for idle, syncing, synced, failed, auth-expired, conflict, and decryption-failed states.
- [x] 2.5 Define retry and user-facing error behavior for network, auth, validation, conflict, malformed payload, and decryption failures.

## 3. Android Flow Stabilization
- [x] 3.1 Ensure registration and first login lead to complete vault/key initialization or a clear recoverable error.
- [x] 3.2 Ensure unlocked vault state is explicit and required before decrypted password data is displayed.
- [x] 3.3 Stabilize password item creation with local encrypted save.
- [x] 3.4 Stabilize sync push of encrypted vault records through existing service/network boundaries.
- [x] 3.5 Stabilize sync pull and client-side decryption after relogin.
- [x] 3.6 Ensure lock/logout prevents access to decrypted vault data until the next unlock.
- [x] 3.7 Keep Activity/Fragment changes focused; defer broad vault UI boundary cleanup to a later `refactor-android-vault-boundaries` change unless required for correctness.

## 4. Backend Flow Stabilization
- [x] 4.1 Ensure auth and token refresh/revoke behavior supports the canonical vault flow.
- [x] 4.2 Ensure private-key or key-metadata endpoints provide the state Android needs without exposing private material.
- [x] 4.3 Ensure vault record endpoints accept and return encrypted payloads and required sync metadata only.
- [x] 4.4 Ensure backend services own transaction and authorization logic for vault sync.
- [x] 4.5 Ensure DTOs do not expose entity internals or plaintext secret fields.
- [x] 4.6 Add append-only Flyway migration only if the agreed vault contract requires schema evolution.

## 5. Tests
- [x] 5.1 Add or update Android unit tests for ViewModel/service behavior touched by the flow.
- [x] 5.2 Add or update Android tests for sync state and error classification where practical.
- [x] 5.3 Add or update backend tests for auth/key/vault service and controller contract behavior.
- [x] 5.4 Add regression coverage for relogin and encrypted record pull/decrypt behavior where practical.
- [x] 5.5 Ensure test data uses synthetic non-secret values only.

## 6. Documentation
- [x] 6.1 Document the verified core vault flow in the appropriate `docs/` or `docs/api/` location.
- [x] 6.2 Update security/key lifecycle documentation if the implementation clarifies initialization or unlock behavior.
- [x] 6.3 Add a manual verification checklist for local backend testing.
- [x] 6.4 Keep `task.md` updated after each important exploration, decision, code change, and verification result.

## 7. Verification
- [x] 7.1 Run Android unit tests with `.\gradlew.bat test`.
- [x] 7.2 Run Android debug compile with `.\gradlew.bat :app:assembleDebug`.
- [x] 7.3 Run backend tests from `safevault-backend/` with `.\mvnw.cmd test`.
- [x] 7.4 Manually verify register -> login -> create password -> sync -> lock/logout -> relogin -> pull -> decrypt/display.
- [x] 7.5 Confirm backend stores ciphertext only for vault password data.
- [x] 7.6 Confirm no secrets are logged, documented, or emitted in test output.
- [x] 7.7 Check root Git status and nested backend Git status before final reporting.
