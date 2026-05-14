## 1. Inventory

- [x] 1.1 List all direct dependencies of LoginActivity (imports, field types, ServiceLocator calls)
- [x] 1.2 List all direct dependencies of RegisterActivity (imports, field types, ServiceLocator calls)
- [x] 1.3 List all direct dependencies of AuthViewModel (imports, especially TokenManager, SecureKeyStorageManager, Argon2KeyDerivationManager, RetrofitClient)
- [x] 1.4 List all direct dependencies of LoginViewModel (imports, especially BiometricAuthHelper, BiometricAuthManager)
- [x] 1.5 Document current auth flow: register → verify → login → session init → biometric setup → unlock

## 2. AuthViewModel Boundary Cleanup

- [x] 2.1 Identify AuthViewModel methods that directly use TokenManager; plan BackendService facade methods
- [x] 2.2 Identify AuthViewModel methods that directly use SecureKeyStorageManager; plan BackendService facade methods
- [x] 2.3 Identify AuthViewModel methods that directly use Argon2KeyDerivationManager; plan BackendService facade methods
- [x] 2.4 Identify AuthViewModel methods that directly use RetrofitClient; plan BackendService facade methods
- [x] 2.5 Add necessary facade methods to BackendService interface and BackendServiceImpl
- [x] 2.6 Refactor AuthViewModel to use BackendService facades instead of low-level imports
- [x] 2.7 Remove low-level imports (TokenManager, SecureKeyStorageManager, Argon2KeyDerivationManager, RetrofitClient) from AuthViewModel
- [x] 2.8 FakeBackendService stubs updated for new BackendService methods; focused AuthViewModel facade routing tests not added (current test infra lacks Application-scoped ViewModel support)

## 3. LoginViewModel Biometric Orchestration

- [x] 3.1 Move biometric eligibility check from LoginActivity into LoginViewModel
- [x] 3.2 Move biometric trigger decision (when to auto-trigger, button visibility) into LoginViewModel
- [x] 3.3 Ensure LoginViewModel owns biometric unlock completion (already partially done from security boundary refactor)
- [x] 3.4 LoginActivity retains BiometricAuthManager as a platform-boundary adapter (BiometricPrompt requires FragmentActivity); remove BiometricAuthHelper direct instantiation (it is internal to BiometricAuthManager)
- [x] 3.5 LoginActivity only hosts BiometricPrompt and forwards callback results to LoginViewModel
- [x] 3.6 Biometric error classification moved to LoginViewModel; LoginViewModel.shouldAutoTriggerBiometric added. Focused tests for biometric callbacks not added (requires BiometricAuthManager mock infrastructure)

## 4. LoginActivity Boundary Cleanup

- [x] 4.1 Remove direct ServiceLocator access; LoginActivity gets BackendService through LoginViewModel or AuthViewModel only
- [x] 4.2 Remove direct AuthSessionManager instantiation; session management handled through ViewModels
- [x] 4.3 Remove direct BackendService field from LoginActivity; all service calls go through ViewModels
- [x] 4.4 Verify LoginActivity only renders UI, handles system callbacks, and observes ViewModel state
- [x] 4.5 Verify biometric prompt hosting is the only platform-boundary exception

## 5. RegisterActivity Boundary Cleanup

- [x] 5.1 Remove direct ServiceLocator access; RegisterActivity gets BackendService through AuthViewModel only
- [x] 5.2 Remove direct AuthSessionManager instantiation; session management handled through AuthViewModel
- [x] 5.3 Remove direct BackendService field from RegisterActivity; all service calls go through AuthViewModel
- [x] 5.4 Move inline registration result handling and post-registration orchestration into AuthViewModel LiveData events
- [x] 5.5 Verify RegisterActivity only renders UI, handles input validation display, and observes ViewModel state

## 6. Compile and Test Verification

- [x] 6.1 Run `.\gradlew.bat :app:assembleDebug` and verify build passes
- [x] 6.2 Run `.\gradlew.bat test` and verify all tests pass
- [x] 6.3 Run `openspec validate "refactor-android-auth-boundaries" --strict` and verify it passes
- [x] 6.4 Record build and test results in task.md

## 7. Manual Verification

- [x] 7.1 Manual: register a new account end-to-end (including email verification and vault initialization)
- [x] 7.2 Manual: login with master password
- [x] 7.3 Manual: biometric unlock after successful login
- [x] 7.4 Manual: logout and re-login flow
- [x] 7.5 Manual: biometric unlock after app restart
- [x] 7.6 Manual: first-login vault setup after registration (verify DataKey initialization completes)
- [x] 7.7 Verify root Git status is clean or as expected

### Build & Test Results (2026-05-14)

- `gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL
- `gradlew.bat test` → BUILD SUCCESSFUL (all unit tests pass)
- `openspec validate refactor-android-auth-boundaries --strict` → valid
- FakeBackendService updated with all new interface methods
