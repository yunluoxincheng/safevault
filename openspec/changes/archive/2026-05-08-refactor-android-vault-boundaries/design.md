# Design: Refactor Android Vault Boundaries

## Overview
This change is a behavior-preserving Android refactor focused on the vault feature area. It follows the roadmap phase 2 direction and builds on the archived core vault flow work. The refactor should make vault screens easier to maintain by pushing orchestration into ViewModels and focused services/managers while leaving crypto, sync contracts, and backend behavior stable.

## Target Dependency Direction
Vault code must follow the project Android direction:

`ui -> viewmodel -> model/service -> (network|security|crypto|data)`

Layer responsibilities for this change:

- `ui`: render vault screens, bind input fields, configure adapters, show dialogs, handle navigation, request Android permissions where needed, and trigger ViewModel intents.
- `viewmodel`: expose vault list/detail/edit/sync UI state; validate user intent; call service/manager APIs; classify operation results for UI; avoid direct widget references.
- `model/service`: define vault use-case contracts and app-level orchestration facades.
- `service/manager`: own password CRUD orchestration, local encrypted persistence access, lock-aware operations, sync coordination, and clipboard/security helpers where applicable.
- `network`, `security`, `crypto`, `data`, and `sync`: remain implementation boundaries and should not be called directly from vault UI classes unless the class is an explicit Android platform adapter.

## Current Pressure Points
The codebase already has useful vault boundaries such as `BackendService`, `BackendServiceImpl`, `PasswordManager`, `VaultSyncManager`, `EncryptionSyncManager`, Room DAO/entity classes, and vault-related ViewModels such as edit flow handling. The implementation also still needs a focused pass to verify whether vault UI classes directly coordinate operations that should belong to ViewModel/service boundaries.

Known areas to inspect first:

- password list, detail, add, and edit screens
- search/filter behavior
- copy password and clipboard auto-clear behavior
- sync refresh/status display
- direct `BackendService` access from UI
- direct DAO, database, crypto, token, or sync manager access from UI
- ViewModels that contain Android UI widget logic or insufficient state modeling

## Boundary Decisions
### Vault UI
Vault UI may:

- observe LiveData or other existing state surfaces
- submit user intents such as load, save, delete, search, copy, sync, retry, and refresh
- show confirmation dialogs and snackbars/toasts
- navigate to edit/detail/share screens

Vault UI must not:

- directly access Room DAO or `AppDatabase`
- directly perform encryption/decryption or key lookup
- directly call low-level sync/network/token classes
- decide retryability based on low-level exceptions
- retain decrypted password values beyond rendering/user-action needs

### Vault ViewModel
Vault ViewModels should provide a small, explicit state model for:

- list loading and empty state
- item detail/edit loading
- save/delete/copy operation status
- sync state and last sync result
- locked/unlocked or auth-required gating when needed
- user-facing error events with sensitive details removed

### Vault Service/Manager Boundary
Prefer extending focused existing managers before adding broad new abstractions. Possible targets include:

- `PasswordManager` for encrypted local CRUD details
- `BackendService` or a vault-specific facade for app-facing vault operations
- `VaultSyncManager` for sync execution/conflict decisions
- a small vault repository/use-case class if existing boundaries are too broad for ViewModel use

The final shape should be the smallest one that removes UI coupling and keeps behavior understandable.

## Migration Strategy
1. Inventory vault UI classes and classify each dependency as acceptable UI concern, ViewModel concern, service/manager concern, or platform-boundary exception.
2. Define the minimal target state model for list/detail/edit/sync screens.
3. Move one screen or flow at a time, starting with the lowest-risk vault CRUD/list orchestration.
4. Keep method signatures stable where possible and add compatibility wrappers only if they reduce risk during migration.
5. Remove temporary wrappers after call sites move.
6. Run compile/tests after each non-trivial slice and record results in `task.md`.

## Behavior Preservation
This proposal must preserve:

- the core vault sync flow verified by `stabilize-core-vault-sync-flow`
- client-side encryption of stored password data
- zero-knowledge backend storage assumptions
- lock/unlock gates before decrypted data display
- clipboard auto-clear and sensitive text handling
- current navigation and screen layout behavior
- backend API paths, DTO shapes, token refresh behavior, and sync payload format

## Testing Strategy
- Add or update unit tests for ViewModel/service behavior when orchestration moves.
- Prefer fake `BackendService` or manager dependencies in tests instead of real crypto/network where practical.
- Run `.\gradlew.bat test` for logic changes.
- Run `.\gradlew.bat :app:assembleDebug` for ViewBinding, navigation, manifest-visible, package, or resource-sensitive changes.
- Manually verify vault list, create, edit, delete, search, copy, sync refresh, lock/relogin display, and the preserved core sync smoke flow.

## Security Notes
- Do not log plaintext passwords, decrypted notes, tokens, private keys, salts, tags, or sync payload secrets.
- Do not store decrypted fields in long-lived UI state unless existing behavior already requires it and there is no safer equivalent in this slice.
- Do not weaken `FLAG_SECURE`, clipboard clearing, lock timeout, biometric gates, AndroidKeyStore, Argon2, AES-GCM, or token revoke behavior.
- Any discovered behavior bug that requires changing crypto, sync payloads, API contracts, or key lifecycle should be split into a dedicated proposal unless it blocks this refactor.
