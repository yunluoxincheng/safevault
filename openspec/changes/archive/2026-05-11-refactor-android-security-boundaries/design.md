# Design: Refactor Android Security Boundaries

## Overview
This is a behavior-preserving Android boundary refactor. It prepares SafeVault for a later security lifecycle hardening proposal by making security orchestration easier to locate, test, and reason about.

The current architecture already has core security components:

- `SessionGuard` for unlocked state and DataKey access gates.
- `SecureKeyStorageManager` for encrypted DataKey/key material storage and AndroidKeyStore-backed operations.
- `BiometricAuthManager` and related biometric classes for biometric authentication state.
- `BackendService`/`BackendServiceImpl` and manager classes for app-level account/security orchestration.
- UI classes such as account security, login/unlock, key migration, and sensitive vault screens that must interact with Android platform prompts and navigation.

The refactor should not alter the security model. It should clarify which layer makes decisions and which layer merely renders or invokes Android framework prompts.

## Target Dependency Direction
Android dependency direction remains:

`ui -> viewmodel -> model/service -> (network|security|crypto|data)`

Security-specific responsibilities:

- `ui`: render account/security state, trigger user intents, host Android framework UI such as BiometricPrompt, request confirmation/password input, and navigate.
- `viewmodel`: expose security/account state, validation result, operation progress, and user-facing error/success events.
- `service` and `service/manager`: decide whether an operation can run, coordinate unlock/enrollment/lock actions, classify errors, and call security/crypto/network/data boundaries.
- `security`: own `SessionGuard`, biometric policy integration, KeyStore-backed key access, screenshot/clipboard/session policy utilities, and security state primitives.
- `crypto`: own algorithms and key derivation only; it must remain UI-independent.

## Boundary Decisions
### Biometric Prompt Boundary
BiometricPrompt is an Android UI framework concern, so an Activity or Fragment may host the prompt lifecycle. However, UI should not decide whether biometric enrollment is possible by directly inspecting key-storage internals. The eligibility decision should come from a ViewModel/service/manager boundary.

### Unlock Boundary
UI may collect a master password or receive a biometric success callback. It should pass that user intent to a boundary that owns:

- master-password unlock execution
- biometric unlock execution
- DataKey/session population
- auth-required/locked/unlocked state mapping
- user-safe error classification

### Key Storage Boundary
Activities and Fragments must not directly call `SecureKeyStorageManager`, `SessionGuard`, AndroidKeyStore helpers, or low-level crypto classes unless an explicit platform-boundary exception is documented. Service/manager classes should own those calls.

### Security Settings Boundary
Account/security screens may render current settings and collect confirmation input. They should not directly orchestrate biometric enrollment, key-storage cleanup, or lock-state transitions. Those operations should be mediated through a ViewModel/service/manager boundary.

## Migration Strategy
1. Inventory direct dependencies from security-sensitive UI classes to `security`, `crypto`, token/session, key-storage, and backend service internals.
2. Classify each dependency as UI platform hosting, state rendering, migration target, or documented exception.
3. Prefer extending existing managers or ViewModels before introducing a new abstraction.
4. If a new facade is needed, keep it focused on security boundary orchestration rather than becoming a general auth service.
5. Move one flow at a time: biometric enrollment, biometric unlock, master-password unlock state mapping, lock/logout state handling, and account security settings.
6. Remove temporary wrappers after call sites migrate.

## Behavior Preservation
This proposal must preserve:

- DataKey availability only after successful unlock.
- DataKey clearing on lock/logout.
- master-password unlock semantics from the verified core vault flow.
- biometric unlock using AndroidKeyStore-backed DeviceKey material.
- background timeout lock behavior.
- `FLAG_SECURE` on sensitive screens.
- clipboard auto-clear behavior for copied passwords.
- existing token refresh/revoke behavior.
- existing local encrypted vault and cloud sync behavior.

## Spec Relationship
This proposal adds project-structure requirements for Android security boundary refactors. It intentionally does not replace the later `stabilize-auth-key-lifecycle` proposal, which should define or harden lifecycle behavior. If implementation discovers unclear lifecycle semantics, record them and defer behavior changes unless required to preserve existing flows.

## Testing Strategy
- Add or update unit tests for new or migrated ViewModel/service/manager behavior where practical.
- Use fake security manager/backend service dependencies for boundary tests; avoid real secrets.
- Run `.\gradlew.bat test` after logic changes.
- Run `.\gradlew.bat :app:assembleDebug` after Activity/Fragment, resource, ViewBinding, manifest, or navigation-sensitive changes.
- Manually verify master-password unlock, biometric enrollment, biometric unlock after restart, background timeout lock, logout lock, and sensitive-screen protections.

## Security Notes
- Never log master passwords, DataKeys, DeviceKeys, private keys, JWTs, refresh tokens, biometric challenge material, salts, GCM tags, or decrypted vault fields.
- Do not add long-lived storage for plaintext master password or decrypted vault data.
- Do not weaken AndroidKeyStore settings, biometric authentication requirements, session timeout, screenshot prevention, clipboard cleanup, or sensitive-data clearing.
