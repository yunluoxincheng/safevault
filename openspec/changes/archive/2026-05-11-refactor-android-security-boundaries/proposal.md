# Change: Refactor Android Security Boundaries

## Why
SafeVault has completed and archived the core vault sync flow and the Android vault boundary slice. The next phase-two roadmap item is to continue Android architecture boundary convergence around security and biometric flows.

Security-sensitive UI currently touches flows such as master-password unlock, biometric enrollment, biometric unlock, background lock, `SessionGuard`, `SecureKeyStorageManager`, AndroidKeyStore-backed DeviceKey access, and account security settings. These flows are already implemented, but their boundaries should be made easier to reason about before the later `stabilize-auth-key-lifecycle` proposal hardens lifecycle behavior.

This change keeps behavior stable while reducing UI-side security orchestration. Activities and Fragments should remain responsible for Android UI prompts and rendering. ViewModels and focused service/manager boundaries should own security decisions, lock/unlock state, biometric enrollment readiness, retry/error classification, and access to key-storage/session internals.

## What Changes
- Inventory security-sensitive Android UI classes and their direct dependencies on `security`, `crypto`, token/session, and key-storage classes.
- Define acceptable Android platform-boundary exceptions for BiometricPrompt and system UI interaction.
- Introduce or refine a focused boundary such as `SecurityUnlockManager`, `BiometricUnlockManager`, or an existing manager/facade extension for unlock, biometric enrollment, lock state, and security settings orchestration.
- Move biometric enrollment decisions and lock/unlock state handling out of UI classes where practical.
- Hide direct `SecureKeyStorageManager`, `SessionGuard`, AndroidKeyStore, crypto, and token/session implementation access from Activities and Fragments unless the class is explicitly serving as a platform adapter.
- Preserve master-password unlock, biometric unlock, DeviceKey/DataKey behavior, lock timeout behavior, screenshot protection, clipboard protection, token behavior, and core vault behavior.
- Add focused tests or compile checks around migrated ViewModel/service behavior and record manual verification for master-password unlock and biometric unlock.

## Non-Goals
- Do not change cryptographic algorithms, key derivation parameters, AndroidKeyStore key properties, DataKey wrapping format, private-key storage format, or sync payload format.
- Do not redesign auth, registration, backend API contracts, JWT/refresh behavior, or token revocation semantics.
- Do not implement the full `stabilize-auth-key-lifecycle` roadmap item in this change; lifecycle behavior hardening remains a later proposal.
- Do not redesign security/account UI screens or migrate XML/ViewBinding UI to Compose.
- Do not introduce Hilt, Kotlin, StateFlow, Coroutines, or a new app architecture framework.
- Do not weaken `FLAG_SECURE`, biometric gates, lock timeout, clipboard cleanup, sensitive-data clearing, or logging restrictions.
- Do not refactor sharing, autofill, vault CRUD, or backend code except where a small call-site adjustment is required to preserve security boundary consistency.

## Impact
- Affected specs: `project-structure`.
- Affected Android code: security/account/biometric/lock-related UI, ViewModels, and managers under `app/src/main/java/com/ttt/safevault/**`.
- Affected docs: package-boundary docs and `docs/directory-standards.md` only if boundary ownership changes; `task.md` after meaningful implementation steps.
- Risk areas: master-password unlock, biometric enrollment, biometric unlock after restart, background timeout lock, logout lock, screenshot protection, clipboard cleanup, and DataKey access gates.
- Verification: `.\gradlew.bat test`, `.\gradlew.bat :app:assembleDebug`, manual master-password unlock, manual biometric enrollment/unlock, background timeout lock, logout lock, and security settings checks.
