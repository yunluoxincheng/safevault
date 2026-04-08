# SafeVault Directory Standards

## Purpose

This document defines mandatory directory responsibilities and dependency
direction for the SafeVault repository. It is used to keep structural refactors
consistent without changing runtime behavior.

## Scope

- Android client: `app/src/main/java/com/ttt/safevault/*`
- Spring Boot backend: `safevault-backend/src/main/java/org/ttt/safevaultbackend/*`

## Android Standards

### `core`
- Path: `app/src/main/java/com/ttt/safevault/core`
- Responsibility: app bootstrap and global facades
- Examples: `SafeVaultApplication`, `ServiceLocator` facade
- Must not contain feature business logic

### `ui`
- Path: `app/src/main/java/com/ttt/safevault/ui`
- Responsibility: Activity/Fragment/Dialog rendering and interaction wiring
- Can depend on: `viewmodel`, minimal `core`
- Must not directly do crypto or persistence operations

### `viewmodel`
- Path: `app/src/main/java/com/ttt/safevault/viewmodel`
- Responsibility: UI state orchestration and use-case coordination
- Can depend on: `model`, `service`, `core`
- Must not host complex UI widget logic

### `model`
- Path: `app/src/main/java/com/ttt/safevault/model`
- Responsibility: domain models and service contracts
- Must stay implementation-agnostic

### `service`
- Path: `app/src/main/java/com/ttt/safevault/service`
- Responsibility: app-level orchestration and facade services
- Subpackage `service/manager` holds capability-focused managers

### `service/manager`
- Path: `app/src/main/java/com/ttt/safevault/service/manager`
- Responsibility: focused capability modules (auth/sync/share/account/etc.)
- Can depend on: `network`, `security`, `model`

### `security`
- Path: `app/src/main/java/com/ttt/safevault/security`
- Responsibility: session security, biometric integration, policy enforcement
- Must not contain screen flow logic

### `crypto`
- Path: `app/src/main/java/com/ttt/safevault/crypto`
- Responsibility: algorithm and protocol implementation
- Must stay UI-independent

### `network`
- Path: `app/src/main/java/com/ttt/safevault/network`
- Responsibility: API clients, token, interceptors, transport
- Must not contain business rules

### `data`
- Path: `app/src/main/java/com/ttt/safevault/data`
- Responsibility: local persistence (database/dao/entity)
- Must not orchestrate feature flows

## Backend Standards

### `controller`
- Path: `safevault-backend/src/main/java/org/ttt/safevaultbackend/controller`
- Responsibility: HTTP boundary, request/response mapping

### `service`
- Path: `safevault-backend/src/main/java/org/ttt/safevaultbackend/service`
- Responsibility: backend use-case orchestration

### `repository`
- Path: `safevault-backend/src/main/java/org/ttt/safevaultbackend/repository`
- Responsibility: persistence access

### `entity`
- Path: `safevault-backend/src/main/java/org/ttt/safevaultbackend/entity`
- Responsibility: storage entities

### `security`
- Path: `safevault-backend/src/main/java/org/ttt/safevaultbackend/security`
- Responsibility: authn/authz and security infrastructure

### `dto`
- Path: `safevault-backend/src/main/java/org/ttt/safevaultbackend/dto`
- Responsibility: external API request/response contracts

## Dependency Direction Rules

### Android
`ui -> viewmodel -> model/service -> (security|crypto|network|data)`

### Backend
`controller -> service -> repository/entity`

## Forbidden Patterns

- Android `service`/`viewmodel` depending on `ui`
- `network` containing business branching logic
- `crypto` containing Android UI flow code
- backend `controller` directly calling `repository` (bypass `service`)

## Change Management

- Structural refactor changes must be tracked with OpenSpec change records
- Functional changes and structural changes should be split into different PRs
