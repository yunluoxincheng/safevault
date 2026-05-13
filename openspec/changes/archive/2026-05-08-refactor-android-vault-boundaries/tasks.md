## 1. Inventory
- [x] 1.1 List vault UI classes for password list, detail, add, edit, search, copy, delete, sync, and refresh flows.
- [x] 1.2 List vault ViewModels and their current dependencies.
- [x] 1.3 List vault service/manager/data/sync classes used by those screens.
- [x] 1.4 Identify direct UI access to `BackendService`, `AppDatabase`, DAO classes, `PasswordManager`, crypto/security managers, `TokenManager`, `VaultSyncManager`, or network clients.
- [x] 1.5 Classify each direct dependency as acceptable UI concern, migration target, or explicit Android platform-boundary exception.
- [x] 1.6 Record inventory findings and high-risk files in `task.md`.

## 2. Target Boundary Design
- [x] 2.1 Define the vault list state model.
- [x] 2.2 Define the vault detail/edit state model.
- [x] 2.3 Define the vault operation event model for save, delete, copy, sync, retry, and refresh.
- [x] 2.4 Define where lock/auth-required state is exposed to vault screens.
- [x] 2.5 Decide whether to extend existing ViewModels/services or introduce a small vault-specific facade.
- [x] 2.6 Document retained platform-boundary exceptions, if any.

## 3. Implementation Slices
- [x] 3.1 Move password list loading/search/empty-state orchestration behind ViewModel/service boundaries.
- [x] 3.2 Move password detail loading and sensitive-field display preparation behind ViewModel/service boundaries.
- [x] 3.3 Move add/edit/save validation and save result handling behind ViewModel/service boundaries.
- [x] 3.4 Move delete confirmation result handling and delete execution behind ViewModel/service boundaries.
- [x] 3.5 Move copy-to-clipboard intent handling behind a boundary that preserves sensitive clipboard cleanup.
- [x] 3.6 Move sync refresh/status/retry orchestration behind ViewModel/service/sync manager boundaries.
- [x] 3.7 Remove direct DAO/database/crypto/security/network/token/sync dependencies from vault UI classes unless explicitly retained as platform-boundary exceptions.
- [x] 3.8 Remove temporary compatibility wrappers after migrated call sites no longer need them.

## 4. Behavior Preservation Checks
- [x] 4.1 Confirm vault UI layouts and navigation behavior are unchanged.
- [x] 4.2 Confirm local password data remains encrypted at rest.
- [x] 4.3 Confirm decrypted data is only available in unlocked flows.
- [x] 4.4 Confirm lock/logout blocks decrypted vault display until unlock.
- [x] 4.5 Confirm sync payload format and backend API contracts are unchanged.
- [x] 4.6 Confirm no new logs expose sensitive vault data.

## 5. Tests
- [x] 5.1 Add or update tests for vault list ViewModel/service behavior.
- [x] 5.2 Add or update tests for vault detail/edit/save/delete behavior where practical.
- [x] 5.3 Add or update tests for sync state/error mapping where practical.
- [x] 5.4 Add or update tests for lock/auth-required state handling where practical.
- [x] 5.5 Keep test fixtures synthetic and free of real secrets.

## 6. Documentation
- [x] 6.1 Update `docs/directory-standards.md` only if vault-specific package ownership rules are clarified.
- [x] 6.2 Update package-level docs if the implementation adds or changes vault boundary contracts.
- [x] 6.3 Update `task.md` after each important exploration, decision, code change, and verification result.

## 7. Verification
- [x] 7.1 Run Android unit tests with `.\gradlew.bat test`.
- [x] 7.2 Run Android debug compile with `.\gradlew.bat :app:assembleDebug`.
- [x] 7.3 Manually verify password list load/search.
- [x] 7.4 Manually verify add/edit/save/delete.
- [x] 7.5 Manually verify copy-to-clipboard and clipboard cleanup behavior.
- [x] 7.6 Manually verify sync refresh/status/retry behavior.
- [x] 7.7 Manually verify lock/logout/relogin vault display behavior.
- [x] 7.8 Confirm root Git status and nested backend Git status before final reporting.
