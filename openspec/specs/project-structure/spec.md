# project-structure Specification

## Purpose
Define maintainable package-boundary and structure documentation requirements for SafeVault repository refactors.

## Requirements

### Requirement: Package Boundary Documentation
The repository MUST define package-boundary intent for each runtime root package using package-level documentation files.

#### Scenario: Android root package is documented
- **WHEN** maintainers inspect `com.ttt.safevault`
- **THEN** a package-level contract describes layer direction and dependency expectations

#### Scenario: Backend root package is documented
- **WHEN** maintainers inspect `org.ttt.safevaultbackend`
- **THEN** a package-level contract describes controller/service/repository boundary direction

### Requirement: Structure Reorganization Record
The repository MUST keep a human-readable structure reorganization record under `docs/` for non-functional structure refactors.

#### Scenario: Structure-only refactor is introduced
- **WHEN** a change updates project/package structure without behavior changes
- **THEN** a dedicated document exists under `docs/` with scope, actions, and non-breaking constraints

### Requirement: Canonical Documentation Layout
The repository MUST define one canonical documentation home for each major documentation audience and topic.

#### Scenario: Maintainer looks for architecture documentation
- **WHEN** a maintainer needs Android, backend, API, security, operations, changelog, or planning documentation
- **THEN** the repository provides a documented canonical location for that topic

#### Scenario: Root documentation is reviewed
- **WHEN** root-level Markdown files are inspected
- **THEN** only project entrypoints, assistant memory, or explicit pointers remain at the root

### Requirement: Documentation Migration Inventory
Large documentation reorganizations MUST be inventory-driven before files are moved or rewritten.

#### Scenario: Stale or duplicated documentation is found
- **WHEN** a document is stale, duplicated, historical, or encoding-damaged
- **THEN** the migration record classifies it before promotion, rewrite, archival, or removal

### Requirement: Nested Backend Repository Policy
The repository MUST document that the backend is managed by the root SafeVault repository after the layout migration, and that the previous nested `safevault-backend/` Git repository is no longer an active development boundary.

#### Scenario: Backend repository status is needed
- **WHEN** maintainers prepare root or backend refactor work
- **THEN** they can determine that root Git status is the authoritative status for Android, backend, documentation, and OpenSpec changes
- **AND** they do not treat `server/` as a nested Git repository

#### Scenario: Old backend remote is referenced
- **WHEN** maintainers encounter the previous `safevault-backend` remote repository
- **THEN** they can determine that it is not the active development remote after migration
- **AND** they can delete, archive, or ignore it according to repository-owner preference after the monorepo migration is verified

### Requirement: Backend Documentation Routing
The repository MUST define a strict routing rule for backend-related documentation across `docs/backend/`, `docs/api/`, and `server/docs/`.

#### Scenario: Backend-local operational documentation is classified
- **WHEN** documentation depends on backend-only working directory commands, Spring profiles, Maven, Docker Compose, deployment, or environment configuration
- **THEN** its canonical home is `server/docs/`

#### Scenario: Cross-repository backend architecture documentation is classified
- **WHEN** documentation explains backend ownership, architecture, or Android-to-backend integration in the context of the full SafeVault project
- **THEN** its canonical home is `docs/backend/` or `docs/api/` depending on whether it documents architecture or API contracts

#### Scenario: A backend topic appears in multiple locations
- **WHEN** a backend document would otherwise be duplicated
- **THEN** one canonical home is selected and the other location uses a pointer instead of a duplicate copy

### Requirement: Generated Directory and Cleanup Policy
The repository MUST document generated/build directories and refactor-safe cleanup rules.

#### Scenario: Generated directory policy is needed
- **WHEN** maintainers encounter build outputs, caches, generated artifacts, or dependency output directories
- **THEN** documentation identifies whether each directory should be ignored, preserved, regenerated, or cleaned manually

#### Scenario: Cleanup rules are applied
- **WHEN** documentation cleanup requires deleting files
- **THEN** maintainers follow the repository rule of deleting only one explicit path at a time and never using recursive or bulk deletion commands

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

### Requirement: Android Refactor Behavior Preservation
Android layer-boundary refactors MUST avoid behavior changes unless a separate proposal authorizes them.

#### Scenario: Security-sensitive flow is touched
- **WHEN** login, registration, biometric auth, KeyStore access, token storage, sharing crypto, autofill, clipboard handling, or key migration code is refactored
- **THEN** existing security gates and sensitive-data handling remain at least as strict as before

#### Scenario: Refactor slice is completed
- **WHEN** a non-trivial Android refactor slice is complete
- **THEN** Android tests or compile verification are run and the result is recorded

### Requirement: Backend Service Boundary Refactor
Backend structural refactors MUST preserve the dependency direction `controller -> service -> repository/entity`.

#### Scenario: Controller responsibility is reviewed
- **WHEN** a controller contains business workflow, transaction orchestration, persistence access, or security policy decisions
- **THEN** that behavior is moved behind a service boundary unless it is purely HTTP boundary handling

#### Scenario: Service responsibility is reviewed
- **WHEN** a service mixes unrelated backend use cases
- **THEN** the refactor either extracts focused collaborators or records why the current grouping is retained

### Requirement: Backend Layered Architecture Direction
Backend structural refactors MUST strengthen the existing layered package structure and MUST NOT migrate implementation into domain-module packages without a separate approved proposal.

#### Scenario: Package ownership is changed
- **WHEN** backend implementation classes are moved or extracted
- **THEN** they remain within the current layered package families unless a separate modularization proposal is approved

#### Scenario: Module marker packages are reviewed
- **WHEN** `modules/*` package markers are encountered
- **THEN** they remain documentation markers and are not treated as implementation destinations for this change

### Requirement: Backend API and Migration Stability
Backend boundary refactors MUST preserve API and database behavior unless a separate proposal authorizes a behavior change.

#### Scenario: Controller, DTO, or package structure changes
- **WHEN** backend Java classes are moved or extracted
- **THEN** REST paths, response shapes, validation semantics, WebSocket topics, JWT behavior, and Redis key meaning remain stable

#### Scenario: Database migrations are present
- **WHEN** backend structural refactor work is performed
- **THEN** existing Flyway migration files are not rewritten and any schema change is deferred to a separate proposal

### Requirement: Canonical Runtime Directory Layout
The repository MUST use lower-case runtime root directories for the Android client and Spring Boot backend.

#### Scenario: Maintainer locates runtime projects
- **WHEN** a maintainer inspects the repository root
- **THEN** the Android client is located under `android/`
- **AND** the Spring Boot backend is located under `server/`
- **AND** cross-project documentation and OpenSpec state remain outside those runtime roots

#### Scenario: Android build entrypoint is needed
- **WHEN** a maintainer needs to run Android Gradle commands
- **THEN** the documented working directory is `android/`
- **AND** the Android `app` module remains available below that runtime root

#### Scenario: Backend build entrypoint is needed
- **WHEN** a maintainer needs to run backend Maven commands
- **THEN** the documented working directory is `server/`
- **AND** backend-local deployment and operations files remain available below that runtime root

### Requirement: Snapshot Backend Import
The repository MUST treat the backend as a snapshot-imported part of the root repository after the layout migration.

#### Scenario: Backend files are changed after migration
- **WHEN** backend files under `server/` are modified
- **THEN** the root SafeVault Git repository tracks those changes directly
- **AND** maintainers do not need to perform a second backend repository commit

#### Scenario: Legacy backend remote is considered
- **WHEN** maintainers need the old backend repository after migration
- **THEN** documentation identifies it as retired or externally historical
- **AND** active development continues in the root SafeVault repository
