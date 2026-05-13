# SafeVault Backend Module Markers

## Goal

Record logical backend ownership without changing the current layered Spring
Boot package structure.

## Logical Ownership Areas

- `auth` - registration/login/token/identity verification
- `vault` - encrypted vault and private key lifecycle
- `share` - cloud sharing and notification workflows
- `contact` - friend/contact relationship graph
- `platform` - security/config/rate-limiting/infra concerns

## Current Mapping

- Controllers remain in `controller/`, with logical ownership grouped as:
  - auth: `AuthController`, `AccountController`, `UserController`, `VerificationWebController`
  - vault: `VaultController`
  - share/contact: `ContactShareController`, `FriendController`
- Services, repositories, entities, DTOs, security, and websocket code remain in
  their existing top-level layered packages.
- Module boundaries are documentation markers via `modules/*/package-info.java`.

## Dependency Direction

`controller -> service -> repository/entity`

Controllers delegate to services. Services coordinate repositories, entities,
security helpers, Redis/email/WebSocket infrastructure, and transaction
boundaries.

## Out of Scope for Current Boundary Refactor

The active backend service-boundary refactor strengthens the existing layered
structure. It does not move implementation classes into `modules/*` packages and
does not perform a modular-monolith migration.

## Possible Future Extraction Path

1. Introduce module-local facades per domain.
2. Move domain-specific DTOs to module subpackages.
3. Add architecture tests to block forbidden dependencies.
4. Extract first microservice from lowest-coupled domain (`share` or `contact`).
