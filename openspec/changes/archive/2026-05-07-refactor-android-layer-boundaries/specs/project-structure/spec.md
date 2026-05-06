## ADDED Requirements

### Requirement: Android Layer Boundary Refactor
Android structural refactors MUST preserve the dependency direction `ui -> viewmodel -> model/service -> (network|security|crypto|data)`.

#### Scenario: UI dependency is migrated
- **WHEN** a UI class directly orchestrates network, crypto, security, or persistence work
- **THEN** the refactor moves that orchestration behind a ViewModel, service, or manager boundary unless the class is an explicit Android system-boundary adapter

#### Scenario: Android package move is performed
- **WHEN** a manifest-visible, resource-referenced, or navigation-referenced class is moved
- **THEN** all Android manifest, navigation, resource, and import references are updated in the same slice

#### Scenario: Core bootstrap entry points are normalized
- **WHEN** application bootstrap or core service-location classes are adjusted for boundary consistency
- **THEN** only one canonical package path remains for each bootstrap entry point, and compatibility wrappers are removed after call sites migrate

### Requirement: Android Refactor Behavior Preservation
Android layer-boundary refactors MUST avoid behavior changes unless a separate proposal authorizes them.

#### Scenario: Security-sensitive flow is touched
- **WHEN** login, registration, biometric auth, KeyStore access, token storage, sharing crypto, autofill, clipboard handling, or key migration code is refactored
- **THEN** existing security gates and sensitive-data handling remain at least as strict as before

#### Scenario: Refactor slice is completed
- **WHEN** a non-trivial Android refactor slice is complete
- **THEN** Android tests or compile verification are run and the result is recorded
