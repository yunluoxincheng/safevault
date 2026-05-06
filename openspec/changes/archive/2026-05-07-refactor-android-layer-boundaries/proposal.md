# Change: Refactor Android Layer Boundaries

## Why
The Android client has grown into many packages and screens. Several UI classes still directly use network clients, token managers, Room data access, crypto helpers, or security storage managers. That makes screens hard to reason about, increases the risk of security regressions, and makes later feature work more expensive.

## What Changes
- Inventory Android package dependencies and identify boundary violations against the target direction `ui -> viewmodel -> model/service -> (network|security|crypto|data)`.
- Move UI-owned orchestration into ViewModels or focused service/manager classes in small slices.
- Keep crypto, token, biometric, and local persistence behavior unchanged while changing call sites.
- Normalize only package/file placement inconsistencies that directly affect Android layer boundaries or manifest-visible bootstrap classes already identified during inventory.
- Add focused tests or compile checks around migrated slices.

## Non-Goals
- Do not introduce Kotlin, Compose, Hilt, StateFlow, or a new app architecture framework.
- Do not redesign UI layouts or navigation flows.
- Do not alter encryption algorithms, key derivation, token refresh behavior, backend API contracts, or persisted data formats.
- Do not migrate all screens in one pass. The work must be sliced by feature area.
- Do not use this proposal as a catch-all for unrelated Android package moves, resource reorganization, UI redesign, or documentation-only cleanup.

## Impact
- Affected specs: `project-structure`
- Affected code: Android Java source under `app/src/main/java/com/ttt/safevault/**`, Android tests where needed
- Risk areas: login/register, account security, biometric auth, sharing, contact/friend flows, autofill, key migration
- Verification: `.\gradlew.bat test`, `.\gradlew.bat :app:assembleDebug`, focused manual review of security-sensitive flows
