# Design: Refactor Backend Service Boundaries

## Overview
This change prepares the Spring Boot backend for safer incremental maintenance. It keeps the current stack and behavior while improving ownership boundaries around backend use cases.

## Current Architecture
The intended direction is:

`controller -> service -> repository/entity`

Current packages include `controller`, `service`, `repository`, `entity`, `security`, `dto`, `config`, `websocket`, `annotation`, `aspect`, `exception`, `modules`, and `util`. Module marker packages exist, but most implementation remains in classic layered packages.

## Target Boundary Decision

This change commits to strengthening the existing layered architecture.

Keep the top-level packages `controller`, `service`, `repository`, `entity`, `dto`, `security`, `websocket`, `config`, and supporting infrastructure packages. Improve cohesion by extracting focused collaborators inside those existing layers instead of moving implementation into domain-module packages.

Rationale:
- The course project benefits from a simple, recognizable Spring Boot structure.
- The immediate refactor goal is readability and risk reduction, not a package architecture migration.
- The existing `modules/*` marker packages can remain documentation markers for now, but they are not implementation targets in this proposal.

A modular-monolith migration remains possible later, but it must be proposed separately because it changes package ownership, review scope, and acceptance criteria.

## Service Boundary Rules
- Controllers handle HTTP concerns, authentication principal extraction, validation, and DTO mapping.
- Services own business workflows, transactions, authorization decisions, and notification orchestration.
- Repositories expose persistence access only.
- Entities represent storage state and should not be exposed directly through public API responses.
- Security classes own JWT, password hashing, auth filters, token revocation checks, and related infrastructure.
- WebSocket publishing should be mediated through service-level methods, not scattered across controllers.

## Migration Strategy
1. Inventory each backend use case and its current controller, service, repository, DTO, entity, and security dependencies.
2. Keep implementation in the current layered packages and document ownership rules for each layer.
3. Start with low-risk extractions that do not change request/response shape.
4. Refactor one use case at a time.
5. Keep DTO and entity package moves separate from behavior changes.
6. Run backend tests after each non-trivial slice.

## Flyway and API Stability
- Existing Flyway migrations are append-only history and should not be edited during structural refactor work.
- Any schema change requires a later functional proposal and a new migration.
- REST endpoints, request DTOs, response DTOs, status codes, WebSocket topics, JWT claims, Redis key meaning, and email verification semantics stay stable.

## Verification
- Run `.\mvnw.cmd test` from `safevault-backend/`.
- Spot-check generated OpenAPI or controller mappings if DTO/controller package moves occur.
- Confirm no old migration file content changed.
- Confirm backend Git status separately from root Git status.
