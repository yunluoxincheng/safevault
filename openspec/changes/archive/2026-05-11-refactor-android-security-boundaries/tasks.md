## 1. Inventory
- [x] 1.1 List security-sensitive UI classes for login/unlock, account security, biometric enrollment, key migration, lock/logout, and sensitive settings.
- [x] 1.2 List current ViewModels, services, and managers involved in unlock, lock, biometric enrollment, biometric unlock, and security settings.
- [x] 1.3 Identify direct UI access to `SecureKeyStorageManager`, `SessionGuard`, `BiometricAuthManager`, `BiometricAuthHelper`, crypto classes, token/session classes, or low-level key-storage APIs.
- [x] 1.4 Classify each direct dependency as platform prompt hosting, rendering-only state, migration target, or documented exception.
- [x] 1.5 Record inventory findings and high-risk flows in `task.md`.

## 2. Boundary Design
- [x] 2.1 Define the security UI state model for locked, unlocked, auth-required, enrolling-biometric, enrolled, unavailable, and failed states.
- [x] 2.2 Define operation events for master-password unlock, biometric unlock, biometric enrollment, lock, logout, and retry.
- [x] 2.3 Decide whether to extend existing ViewModels/managers or introduce a focused security unlock/enrollment facade.
- [x] 2.4 Define accepted Android platform-boundary exceptions for BiometricPrompt hosting and system UI interaction.
- [x] 2.5 Define user-safe error classification for key-storage, biometric, locked-session, canceled-auth, and unavailable-hardware cases.

## 3. Implementation Slices
- [x] 3.1 Move biometric enrollment eligibility decisions out of UI and behind ViewModel/service/manager boundaries.
- [x] 3.2 Move biometric enrollment execution orchestration out of UI while keeping Android prompt hosting in UI where required.
- [x] 3.3 Move biometric unlock result handling and session-state mapping behind a security boundary.
- [x] 3.4 Move master-password unlock state mapping behind a ViewModel/service/manager boundary where UI currently owns it.
- [x] 3.5 Move lock/logout state handling behind service/manager boundaries where UI currently reaches into session/key internals.
- [x] 3.6 Remove direct `SecureKeyStorageManager`, `SessionGuard`, crypto, token, and key-storage dependencies from Activities/Fragments unless explicitly documented as platform-boundary exceptions.
- [x] 3.7 Remove temporary compatibility wrappers after migrated call sites no longer need them.

## 4. Behavior Preservation Checks
- [x] 4.1 Confirm DataKey is only available after successful unlock.
- [x] 4.2 Confirm DataKey is cleared or made inaccessible after lock/logout.
- [x] 4.3 Confirm biometric unlock still uses AndroidKeyStore-backed DeviceKey material.
- [x] 4.4 Confirm biometric-only unlock keeps cloud sync behavior consistent with the core vault flow.
- [x] 4.5 Confirm background timeout still locks sensitive data.
- [x] 4.6 Confirm `FLAG_SECURE` and clipboard cleanup behavior are not weakened.
- [x] 4.7 Confirm no new logs expose secrets or decrypted vault data.

## 5. Tests
- [x] 5.1 Add or update tests for security state mapping where practical.
- [x] 5.2 Add or update tests for biometric eligibility and unavailable/canceled/failure classification where practical.
- [x] 5.3 Add or update tests for lock/logout state handling where practical.
- [x] 5.4 Add or update tests for ViewModel/service behavior touched by migrated account security flows.
- [x] 5.5 Keep all test fixtures synthetic and free of real secrets.

## 6. Documentation
- [x] 6.1 Update `docs/directory-standards.md` only if security package ownership or manager responsibilities are clarified.
- [x] 6.2 Update package-level docs if a new security boundary facade or manager is introduced.
- [x] 6.3 Keep `docs/security-architecture.md` behavior descriptions unchanged unless implementation uncovers stale documentation.
- [x] 6.4 Update `task.md` after each important exploration, decision, code change, and verification result.

## 7. Verification
- [x] 7.1 Run Android unit tests with `.\gradlew.bat test`.
- [x] 7.2 Run Android debug compile with `.\gradlew.bat :app:assembleDebug`.
- [x] 7.3 Manually verify master-password unlock.
- [x] 7.4 Manually verify biometric enrollment.
- [x] 7.5 Manually verify biometric unlock after app restart.
- [x] 7.6 Manually verify background timeout lock.
- [x] 7.7 Manually verify logout lock and token/session cleanup behavior.
- [x] 7.8 Manually verify screenshot protection and clipboard cleanup on sensitive screens.
- [x] 7.9 Confirm root Git status and nested backend Git status before final reporting.
