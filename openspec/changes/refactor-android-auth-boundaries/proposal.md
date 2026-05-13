## Why

SafeVault has completed Android vault boundary and security boundary refactoring. The next phase-two roadmap slice is to converge the auth/login/register boundary. LoginActivity and RegisterActivity still directly instantiate `AuthSessionManager`, `BackendService`, `BiometricAuthHelper`, and `BiometricAuthManager`, and directly call into `ServiceLocator`. AuthViewModel directly imports `TokenManager`, `SecureKeyStorageManager`, `Argon2KeyDerivationManager`, and `RetrofitClient`. These violations make auth flows harder to reason about, increase the risk of security regressions, and must be cleaned up before the later `stabilize-auth-key-lifecycle` proposal hardens key lifecycle behavior.

## What Changes

- Inventory auth-related UI classes (`LoginActivity`, `RegisterActivity`) and their direct dependencies on `security`, `crypto`, `network`, token/session, and key-storage classes.
- Define acceptable Android platform-boundary exceptions for system UI interaction (BiometricPrompt, permissions).
- Ensure LoginActivity and RegisterActivity only render UI, wire user interaction, and host platform adapters; all auth orchestration moves into ViewModels or focused managers.
- Hide direct access to `TokenManager`, `SecureKeyStorageManager`, `Argon2KeyDerivationManager`, `RetrofitClient`, `ServiceLocator`, and `BiometricAuthHelper` from Activities.
- Ensure AuthViewModel does not directly import `TokenManager`, `SecureKeyStorageManager`, `Argon2KeyDerivationManager`, or `RetrofitClient`; route through BackendService or existing manager facades.
- Preserve registration flow, email login, master-password login, biometric unlock, token refresh, session management, and all core vault behavior.
- Add focused tests or compile checks around migrated behavior and record manual verification for login and registration flows.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `project-structure`: Update Android auth/login/register boundary ownership rules to reflect ViewModel/manager responsibility for auth orchestration and Activity/Fragment restriction to UI rendering and platform adapters.

## Impact

- Affected specs: `project-structure`.
- Affected Android code: `LoginActivity`, `RegisterActivity`, `AuthViewModel`, `LoginViewModel`, `AuthSessionManager`, `BackendService`/`BackendServiceImpl`, and related classes under `android/app/src/main/java/com/ttt/safevault/**`.
- Risk areas: registration flow, email login, master-password login, biometric unlock after restart, token save/refresh, session initialization, and first-login vault setup.
- Verification: `.\gradlew.bat test`, `.\gradlew.bat :app:assembleDebug`, manual registration, manual login, manual biometric unlock, and manual logout/login cycle.
