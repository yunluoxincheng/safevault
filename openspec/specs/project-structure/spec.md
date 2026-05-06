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
The repository MUST document how the root project relates to the nested `safevault-backend/` Git repository.

#### Scenario: Backend repository status is needed
- **WHEN** maintainers prepare root or backend refactor work
- **THEN** they can determine whether backend changes require root Git actions, backend Git actions, or both

### Requirement: Backend Documentation Routing
The repository MUST define a strict routing rule for backend-related documentation across `docs/backend/`, `docs/api/`, and `safevault-backend/docs/`.

#### Scenario: Backend-local operational documentation is classified
- **WHEN** documentation depends on backend-only working directory commands, Spring profiles, Maven, Docker Compose, deployment, or environment configuration
- **THEN** its canonical home is `safevault-backend/docs/`

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

### Requirement: Android Refactor Behavior Preservation
Android layer-boundary refactors MUST avoid behavior changes unless a separate proposal authorizes them.

#### Scenario: Security-sensitive flow is touched
- **WHEN** login, registration, biometric auth, KeyStore access, token storage, sharing crypto, autofill, clipboard handling, or key migration code is refactored
- **THEN** existing security gates and sensitive-data handling remain at least as strict as before

#### Scenario: Refactor slice is completed
- **WHEN** a non-trivial Android refactor slice is complete
- **THEN** Android tests or compile verification are run and the result is recorded
