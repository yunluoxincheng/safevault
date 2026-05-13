# Change: Refactor Android Vault Boundaries

## Why
The core vault sync flow has been stabilized and archived, so SafeVault can now move into roadmap phase 2: Android architecture boundary convergence. The vault area is the right next slice because it is the main password-manager surface and touches local encrypted storage, unlocked state, sync state, search, edit, copy, delete, and relogin behavior.

The goal is to reduce the amount of business orchestration in Activities and Fragments while preserving the working vault behavior from `stabilize-core-vault-sync-flow`. Vault UI should render state and wire user interaction; ViewModels and focused services/managers should own vault CRUD, search, sync, lock-aware loading, error classification, and retry decisions.

## What Changes
- Inventory vault-related Activities, Fragments, Dialogs, ViewModels, managers, services, Room access, crypto calls, and sync managers.
- Define the target Android vault dependency direction: `ui -> viewmodel -> model/service -> (network|security|crypto|data)`.
- Introduce or refine a vault-facing ViewModel/service boundary for list, detail, edit, save, delete, search, copy, sync, and refresh actions.
- Move vault CRUD and sync orchestration out of UI classes when it currently reaches into `BackendService`, DAO, crypto, sync, token, or security internals.
- Keep local encryption, sync contracts, lock/unlock gates, token behavior, and backend API behavior unchanged.
- Add or adjust focused tests for migrated ViewModel/service behavior.
- Record manual verification for the vault screens and the core flow preserved from the previous change.

## Non-Goals
- Do not redesign vault UI layouts, visual styling, navigation structure, or app information architecture.
- Do not migrate the app to Compose, Kotlin, Hilt, StateFlow, Coroutines, or a new architecture framework.
- Do not change encryption algorithms, key derivation, AndroidKeyStore behavior, biometric behavior, sync payload format, backend API paths, DTO shapes, or Room schema unless a correctness issue requires a separate approved proposal.
- Do not refactor auth, account security, sharing, contacts, autofill, or key lifecycle beyond what is strictly needed for vault boundary preservation.
- Do not rewrite `BackendServiceImpl`, `PasswordManager`, or sync managers wholesale; extract or wrap only where it reduces vault UI coupling.
- Do not weaken `FLAG_SECURE`, clipboard protection, lock enforcement, sensitive-data clearing, or logging restrictions.

## Impact
- Affected specs: `project-structure`, and any vault/sync behavior spec produced or updated by the archived core vault flow change.
- Affected Android code: vault-related classes under `app/src/main/java/com/ttt/safevault/ui/**`, `viewmodel/**`, `model/**`, `service/**`, `service/manager/**`, `sync/**`, and tests as needed.
- Affected documentation: `docs/directory-standards.md` only if the vault boundary decision clarifies package ownership; `task.md` after each meaningful implementation step.
- Risk areas: password list loading, add/edit/delete, search, copy-to-clipboard, sync refresh, unlocked/locked state handling, local encryption boundaries, and relogin display behavior.
- Verification: `.\gradlew.bat test`, `.\gradlew.bat :app:assembleDebug`, focused vault manual checks, and preserved register-login-create-sync-relogin-decrypt smoke verification where practical.
