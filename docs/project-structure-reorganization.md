# SafeVault Project Structure Reorganization

## Scope

This reorganization focuses on structure clarity only:

- No functional changes
- No file deletions
- No API contract changes

## Current Topology

SafeVault is a mono-repo with two runtime parts:

- `app`: Android native client (Java, MVVM)
- `safevault-backend`: Spring Boot backend

Both parts are already separated at the repository level, but package responsibilities were not explicitly documented at package scope.

## Reorganization Actions

### 1) Package Boundary Documentation

Added package-level descriptors (`package-info.java`) to root packages:

- `com.ttt.safevault`
- `org.ttt.safevaultbackend`

Added package-level descriptors for high-traffic subpackages:

- Android:
  - `com.ttt.safevault.ui`
  - `com.ttt.safevault.viewmodel`
  - `com.ttt.safevault.model`
  - `com.ttt.safevault.service`
  - `com.ttt.safevault.service.manager`
  - `com.ttt.safevault.security`
  - `com.ttt.safevault.crypto`
  - `com.ttt.safevault.network`
- Backend:
  - `org.ttt.safevaultbackend.controller`
  - `org.ttt.safevaultbackend.service`
  - `org.ttt.safevaultbackend.repository`
  - `org.ttt.safevaultbackend.entity`
  - `org.ttt.safevaultbackend.security`
  - `org.ttt.safevaultbackend.dto`

These files define responsibility boundaries and dependency direction for maintainers, without touching runtime logic.

### 2) OpenSpec Change Traceability

Created OpenSpec change record:

- `openspec/changes/refactor-project-structure/proposal.md`
- `openspec/changes/refactor-project-structure/tasks.md`
- `openspec/changes/refactor-project-structure/specs/project-structure/spec.md`

This provides auditable refactor intent and completion checklist.

### 3) Core Bootstrap Namespace

Introduced `com.ttt.safevault.core` for bootstrap-level structure:

- `SafeVaultApplication` moved to `com.ttt.safevault.core`
- `AndroidManifest.xml` application entry updated to `.core.SafeVaultApplication`
- Added `com.ttt.safevault.core.ServiceLocator` facade for migration compatibility

## Target Responsibility Split

### Android (`app`)

- `ui`: screen composition and interaction only
- `viewmodel`: state orchestration and user-intent handling
- `model`: domain models and backend contract abstraction
- `service` + `service/manager`: backend-service orchestration and modular capability managers
- `security` + `crypto`: key lifecycle and cryptographic primitives
- `network`: transport, token, and API contracts

### Backend (`safevault-backend`)

- `controller`: HTTP boundary
- `service`: use-case logic
- `repository` + `entity`: persistence boundary
- `security`: authn/authz token and filter chain
- `dto`: API input/output contracts

## Follow-up (Optional, Non-breaking)

1. Introduce architecture linting rules (package cycles, forbidden imports) in CI.
2. Add lightweight architecture tests to enforce dependency direction.
3. Keep structural changes and functional changes in separate PRs.
