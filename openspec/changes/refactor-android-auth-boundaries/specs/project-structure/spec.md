## MODIFIED Requirements

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

#### Scenario: Auth Activity direct service access is removed
- **WHEN** LoginActivity or RegisterActivity directly instantiates AuthSessionManager, accesses ServiceLocator, or holds a BackendService field
- **THEN** the Activity receives all service access through its ViewModel and does not construct or locate service objects itself

#### Scenario: Auth ViewModel does not import low-level network or crypto classes
- **WHEN** AuthViewModel or LoginViewModel directly imports TokenManager, SecureKeyStorageManager, Argon2KeyDerivationManager, or RetrofitClient
- **THEN** the refactor routes that access through BackendService or an existing manager facade, and the ViewModel no longer holds low-level imports

#### Scenario: Biometric auth orchestration is owned by ViewModel
- **WHEN** LoginActivity directly instantiates BiometricAuthHelper or BiometricAuthManager, or decides biometric eligibility and trigger timing
- **THEN** LoginActivity only hosts the BiometricPrompt as a platform adapter and forwards callback results to LoginViewModel
- **AND** LoginViewModel owns biometric eligibility, trigger decision, and unlock completion
