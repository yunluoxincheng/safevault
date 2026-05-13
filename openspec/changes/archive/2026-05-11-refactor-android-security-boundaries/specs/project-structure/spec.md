## ADDED Requirements

### Requirement: Android Security Boundary Refactor
Android security-boundary refactors MUST preserve the dependency direction `ui -> viewmodel -> model/service -> (network|security|crypto|data)` and MUST keep Activities and Fragments from directly orchestrating key-storage, session, biometric, or crypto internals unless the class is an explicit Android platform-boundary adapter.

#### Scenario: Security UI dependency is migrated
- **WHEN** an Activity or Fragment directly decides biometric enrollment eligibility, lock/unlock state, key-storage readiness, or retryability from low-level security classes
- **THEN** that decision is moved behind a ViewModel, service, or manager boundary
- **AND** the UI consumes a user-safe state or event rather than inspecting key-storage/session internals directly

#### Scenario: Android biometric prompt remains UI-hosted
- **WHEN** a biometric flow requires Android framework prompt hosting or lifecycle-bound callbacks
- **THEN** the Activity or Fragment may host the prompt interaction
- **AND** eligibility, session mutation, DataKey access, and error classification remain behind ViewModel/service/manager boundaries

### Requirement: Android Security Behavior Preservation
Android security-boundary refactors MUST preserve existing master-password unlock, biometric unlock, lock/logout, DeviceKey/DataKey, screenshot protection, clipboard cleanup, and token/session behavior unless a separate approved proposal authorizes behavior changes.

#### Scenario: Unlock behavior is refactored
- **WHEN** master-password or biometric unlock code is moved behind a new or existing boundary
- **THEN** DataKey availability remains limited to successful unlock
- **AND** failed or canceled authentication does not expose decrypted vault data
- **AND** user-facing errors do not reveal secrets, keys, tokens, salts, tags, or decrypted fields

#### Scenario: Lock behavior is refactored
- **WHEN** background timeout, manual lock, or logout code is moved behind a new or existing boundary
- **THEN** decrypted vault access is blocked after lock/logout
- **AND** DataKey/session state is cleared or made inaccessible according to the existing security model
- **AND** sensitive screen and clipboard protections remain at least as strict as before
