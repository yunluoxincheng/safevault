## ADDED Requirements

### Requirement: Backend Service Boundary Refactor
Backend structural refactors MUST preserve the dependency direction `controller -> service -> repository/entity`.

#### Scenario: Controller responsibility is reviewed
- **WHEN** a controller contains business workflow, transaction orchestration, persistence access, or security policy decisions
- **THEN** that behavior is moved behind a service boundary unless it is purely HTTP boundary handling

#### Scenario: Service responsibility is reviewed
- **WHEN** a service mixes unrelated backend use cases
- **THEN** the refactor either extracts focused collaborators or records why the current grouping is retained

### Requirement: Backend Layered Architecture Direction
This backend refactor MUST strengthen the existing layered package structure and MUST NOT migrate implementation into domain-module packages.

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
