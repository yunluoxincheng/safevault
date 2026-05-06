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
