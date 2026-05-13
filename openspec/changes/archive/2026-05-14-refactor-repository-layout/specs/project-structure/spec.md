## ADDED Requirements

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

## MODIFIED Requirements

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
