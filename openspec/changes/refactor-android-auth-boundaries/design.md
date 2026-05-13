## Context

SafeVault has completed three Android boundary refactoring slices: `refactor-android-layer-boundaries` (inventory), `refactor-android-vault-boundaries` (vault CRUD/sync), and `refactor-android-security-boundaries` (biometric/lock). The remaining phase-two auth slice covers LoginActivity and RegisterActivity.

Current state of auth-related classes:

- **LoginActivity**: Directly holds `AuthSessionManager`, `BackendService` (from `ServiceLocator`), `BiometricAuthHelper`, and `BiometricAuthManager`. Performs biometric authentication triggering, session-password saving, and init-biometric orchestration inline.
- **RegisterActivity**: Directly holds `AuthSessionManager` and `BackendService` (from `ServiceLocator`). Performs registration result handling and email-save orchestration inline.
- **AuthViewModel**: Directly imports `TokenManager`, `SecureKeyStorageManager`, `Argon2KeyDerivationManager`, and `RetrofitClient`. Contains key-derivation orchestration, token handling, and precheck logic.
- **LoginViewModel**: Imports `BackendService`, `BiometricAuthHelper`, and `BiometricAuthManager`. Contains biometric support checks and unlock completion.

The vault and security boundary refactors have already demonstrated that moving orchestration behind ViewModel/manager boundaries while keeping BiometricPrompt hosting in Activities is safe and testable. This change applies the same pattern to the auth entry points.

## Goals / Non-Goals

**Goals:**

- Remove direct `ServiceLocator` access from LoginActivity and RegisterActivity; inject `BackendService` through `ViewModelFactory`.
- Remove direct `AuthSessionManager` instantiation from LoginActivity and RegisterActivity; route through existing manager facades or ViewModels.
- Remove direct `BiometricAuthHelper` instantiation from LoginActivity; retain `BiometricAuthManager` only as the documented BiometricPrompt platform-boundary adapter, while moving biometric eligibility, trigger decisions, and result handling into LoginViewModel.
- Remove direct `TokenManager`, `SecureKeyStorageManager`, `Argon2KeyDerivationManager`, and `RetrofitClient` imports from AuthViewModel; route through `BackendService` or existing manager facades.
- Preserve all existing auth behavior: registration, email login, master-password login, biometric unlock, token save/refresh, session initialization, and vault setup.
- Add focused unit tests for migrated ViewModel behavior.

**Non-Goals:**

- Do not redesign auth, registration, or backend API contracts.
- Do not change encryption algorithms, key derivation parameters, token refresh semantics, or JWT behavior.
- Do not implement the full `stabilize-auth-key-lifecycle` roadmap item; lifecycle hardening remains a later proposal.
- Do not redesign login/register UI screens or migrate to Compose.
- Do not introduce Hilt, Kotlin, StateFlow, Coroutines, or a new app architecture framework.
- Do not weaken any security gates: FLAG_SECURE, biometric checks, token revocation, lock enforcement, clipboard cleanup, or sensitive-data clearing.
- Do not refactor sharing, autofill, vault CRUD, or backend code except where a small call-site adjustment is required for auth boundary consistency.

## Decisions

### D1: ViewModelFactory injection for BackendService

**Decision**: LoginActivity and RegisterActivity obtain `BackendService` indirectly through their ViewModels (which ViewModelFactory constructs with BackendService), instead of calling `ServiceLocator.getInstance().getBackendService()` directly. Activities do not hold BackendService fields.

**Rationale**: ViewModelFactory already provides BackendService to AuthViewModel and LoginViewModel. Activities obtain service access by calling methods on their ViewModels, not by holding BackendService references themselves. This avoids Activity-level ServiceLocator coupling while keeping BackendService injection in ViewModelFactory where it already exists.

**Alternative**: Keep ServiceLocator access in Activities. Rejected because it violates the target dependency direction and makes Activities harder to test.

### D2: LoginViewModel owns biometric auth orchestration

**Decision**: LoginViewModel owns biometric eligibility check, trigger decision, and unlock completion. LoginActivity retains `BiometricAuthManager` as an explicit platform-boundary adapter because `BiometricAuthManager.authenticate()` requires a `FragmentActivity` to host the BiometricPrompt. The boundary split is:

- **LoginViewModel**: decides *whether* biometric is available, *when* to trigger it (auto-trigger, button visibility), and *what happens after* a successful or failed biometric result (session mutation, DataKey access, error classification).
- **LoginActivity**: holds `BiometricAuthManager`, calls `authenticate()` when LoginViewModel signals it should (via LiveData event), receives the platform callback, and forwards the result back to LoginViewModel. This is the same platform-adapter pattern used in AccountSecurityFragment → AccountSecurityViewModel.

LoginActivity does **not** decide eligibility, does **not** interpret AuthError/AuthCallback results, and does **not** perform session mutation on its own. BiometricAuthHelper instantiation remains internal to BiometricAuthManager and is not visible to LoginActivity.

**Rationale**: Consistent with the security boundary refactor pattern where AccountSecurityFragment delegates to AccountSecurityViewModel. The Activity is the BiometricPrompt host but does not decide enrollment eligibility, key-access logic, or session mutation. Keeping BiometricAuthManager in the Activity is necessary because the Android BiometricPrompt framework requires a FragmentActivity lifecycle.

**Alternative**: Create a separate AuthBiometricManager. Rejected because `BiometricAuthManager` already exists and LoginViewModel can coordinate through it and BackendService, reducing indirection.

### D3: AuthViewModel uses BackendService facade instead of low-level imports

**Decision**: AuthViewModel stops directly importing `TokenManager`, `SecureKeyStorageManager`, `Argon2KeyDerivationManager`, and `RetrofitClient`. These are accessed through `BackendService` or through new methods added to BackendService if needed.

**Rationale**: AuthViewModel should be a ViewModel-level orchestrator, not a network/crypto-aware class. BackendService already provides auth-related methods. Where AuthViewModel needs functionality currently buried behind direct TokenManager/SecureKeyStorageManager calls, BackendService gains a focused facade method.

**Alternative**: Create a dedicated AuthService or AuthOrchestrator. Rejected because BackendService already serves as the service-layer facade and a new class would add indirection without clear benefit at this project scale.

### D4: RegisterActivity delegates to AuthViewModel

**Decision**: RegisterActivity's registration result handling, email-save logic, and post-registration navigation are driven by AuthViewModel state, not inline Activity code. RegisterActivity only observes LiveData and renders UI.

**Rationale**: Follows the established pattern where UI observes state and does not orchestrate business flows. RegisterActivity already uses AuthViewModel but still has inline backend-service calls and AuthSessionManager direct access for post-registration steps.

### D5: AuthSessionManager instantiation removed from Activities

**Decision**: LoginActivity and RegisterActivity no longer instantiate `AuthSessionManager` directly. Session management is handled through ViewModels and BackendService.

**Rationale**: AuthSessionManager is a service-layer concern. Activities should not construct service objects. ViewModelFactory or BackendService owns the lifecycle of session managers.

## Risks / Trade-offs

- **Risk**: AuthViewModel's key-derivation and token-handling logic is complex; routing through BackendService may require adding several new facade methods. → **Mitigation**: Add methods incrementally, only for call sites being migrated. Do not add speculative methods.

- **Risk**: Biometric unlock flow spans Activity (prompt host) and LoginViewModel (orchestration), which could be fragile if callback wiring is wrong. → **Mitigation**: Follow the exact pattern proven in AccountSecurityFragment → AccountSecurityViewModel. Add tests for LoginViewModel biometric completion paths.

- **Risk**: Registration flow has multiple backend-call stages (register, verify email, init keys). Moving orchestration into AuthViewModel could expand its responsibility. → **Mitigation**: Keep registration orchestration in AuthViewModel since it already owns it; only remove low-level dependency imports.

- **Trade-off**: BackendService gains more auth-related facade methods, slightly increasing its interface size. This is acceptable because the alternative (new service class) adds indirection at a project scale that does not justify it.
