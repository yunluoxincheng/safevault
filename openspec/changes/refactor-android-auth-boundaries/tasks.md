## 1. Inventory

- [ ] 1.1 List all direct dependencies of LoginActivity (imports, field types, ServiceLocator calls)
- [ ] 1.2 List all direct dependencies of RegisterActivity (imports, field types, ServiceLocator calls)
- [ ] 1.3 List all direct dependencies of AuthViewModel (imports, especially TokenManager, SecureKeyStorageManager, Argon2KeyDerivationManager, RetrofitClient)
- [ ] 1.4 List all direct dependencies of LoginViewModel (imports, especially BiometricAuthHelper, BiometricAuthManager)
- [ ] 1.5 Document current auth flow: register → verify → login → session init → biometric setup → unlock

## 2. AuthViewModel Boundary Cleanup

- [ ] 2.1 Identify AuthViewModel methods that directly use TokenManager; plan BackendService facade methods
- [ ] 2.2 Identify AuthViewModel methods that directly use SecureKeyStorageManager; plan BackendService facade methods
- [ ] 2.3 Identify AuthViewModel methods that directly use Argon2KeyDerivationManager; plan BackendService facade methods
- [ ] 2.4 Identify AuthViewModel methods that directly use RetrofitClient; plan BackendService facade methods
- [ ] 2.5 Add necessary facade methods to BackendService interface and BackendServiceImpl
- [ ] 2.6 Refactor AuthViewModel to use BackendService facades instead of low-level imports
- [ ] 2.7 Remove low-level imports (TokenManager, SecureKeyStorageManager, Argon2KeyDerivationManager, RetrofitClient) from AuthViewModel
- [ ] 2.8 Add or update unit tests for AuthViewModel migrated methods

## 3. LoginViewModel Biometric Orchestration

- [ ] 3.1 Move biometric eligibility check from LoginActivity into LoginViewModel
- [ ] 3.2 Move biometric trigger decision (when to auto-trigger, button visibility) into LoginViewModel
- [ ] 3.3 Ensure LoginViewModel owns biometric unlock completion (already partially done from security boundary refactor)
- [ ] 3.4 LoginActivity retains BiometricAuthManager as a platform-boundary adapter (BiometricPrompt requires FragmentActivity); remove BiometricAuthHelper direct instantiation (it is internal to BiometricAuthManager)
- [ ] 3.5 LoginActivity only hosts BiometricPrompt and forwards callback results to LoginViewModel
- [ ] 3.6 Add or update unit tests for LoginViewModel biometric eligibility and unlock completion

## 4. LoginActivity Boundary Cleanup

- [ ] 4.1 Remove direct ServiceLocator access; LoginActivity gets BackendService through LoginViewModel or AuthViewModel only
- [ ] 4.2 Remove direct AuthSessionManager instantiation; session management handled through ViewModels
- [ ] 4.3 Remove direct BackendService field from LoginActivity; all service calls go through ViewModels
- [ ] 4.4 Verify LoginActivity only renders UI, handles system callbacks, and observes ViewModel state
- [ ] 4.5 Verify biometric prompt hosting is the only platform-boundary exception

## 5. RegisterActivity Boundary Cleanup

- [ ] 5.1 Remove direct ServiceLocator access; RegisterActivity gets BackendService through AuthViewModel only
- [ ] 5.2 Remove direct AuthSessionManager instantiation; session management handled through AuthViewModel
- [ ] 5.3 Remove direct BackendService field from RegisterActivity; all service calls go through AuthViewModel
- [ ] 5.4 Move inline registration result handling and post-registration orchestration into AuthViewModel LiveData events
- [ ] 5.5 Verify RegisterActivity only renders UI, handles input validation display, and observes ViewModel state

## 6. Compile and Test Verification

- [ ] 6.1 Run `.\gradlew.bat :app:assembleDebug` and verify build passes
- [ ] 6.2 Run `.\gradlew.bat test` and verify all tests pass
- [ ] 6.3 Run `openspec validate "refactor-android-auth-boundaries" --strict` and verify it passes
- [ ] 6.4 Record build and test results in task.md

## 7. Manual Verification

- [ ] 7.1 Manual: register a new account end-to-end (including email verification and vault initialization)
- [ ] 7.2 Manual: login with master password
- [ ] 7.3 Manual: biometric unlock after successful login
- [ ] 7.4 Manual: logout and re-login flow
- [ ] 7.5 Manual: biometric unlock after app restart
- [ ] 7.6 Manual: first-login vault setup after registration (verify DataKey initialization completes)
- [ ] 7.7 Verify root Git status is clean or as expected
