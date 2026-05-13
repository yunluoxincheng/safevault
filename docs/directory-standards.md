# SafeVault Directory Standards

## Purpose

This document defines mandatory directory responsibilities and dependency
direction for the SafeVault repository. It is used to keep structural refactors
consistent without changing runtime behavior.

## Scope

- Android client: `android/app/src/main/java/com/ttt/safevault/*`
- Spring Boot backend: `server/src/main/java/org/ttt/safevaultbackend/*`

## Android Standards

### `core`
- Path: `android/app/src/main/java/com/ttt/safevault/core`
- Responsibility: app bootstrap and global facades
- Examples: `SafeVaultApplication`, `ServiceLocator` facade
- Must not contain feature business logic

### `ui`
- Path: `android/app/src/main/java/com/ttt/safevault/ui`
- Responsibility: Activity/Fragment/Dialog rendering and interaction wiring
- Can depend on: `viewmodel`, minimal `core`
- Must not directly do crypto or persistence operations
- Must not directly access `SessionGuard`, `SecureKeyStorageManager`, or `BiometricAuthManager` internals; use ViewModel/BackendService boundaries instead
- BiometricPrompt hosting is an accepted platform-boundary exception; enrollment eligibility and completion decisions must come from ViewModel

### `viewmodel`
- Path: `android/app/src/main/java/com/ttt/safevault/viewmodel`
- Responsibility: UI state orchestration and use-case coordination
- Can depend on: `model`, `service`, `core`
- Must not host complex UI widget logic

### `model`
- Path: `android/app/src/main/java/com/ttt/safevault/model`
- Responsibility: domain models and service contracts
- Must stay implementation-agnostic

### `service`
- Path: `android/app/src/main/java/com/ttt/safevault/service`
- Responsibility: app-level orchestration and facade services
- Subpackage `service/manager` holds capability-focused managers

### `service/manager`
- Path: `android/app/src/main/java/com/ttt/safevault/service/manager`
- Responsibility: focused capability modules (auth/sync/share/account/etc.)
- Session/token state access from UI should be wrapped here (for example `AuthSessionManager`) instead of direct `TokenManager` calls in Activities/Fragments.
- Can depend on: `network`, `security`, `model`

### `security`
- Path: `android/app/src/main/java/com/ttt/safevault/security`
- Responsibility: session security, biometric integration, policy enforcement
- Must not contain screen flow logic

### `crypto`
- Path: `android/app/src/main/java/com/ttt/safevault/crypto`
- Responsibility: algorithm and protocol implementation
- Must stay UI-independent

### `network`
- Path: `android/app/src/main/java/com/ttt/safevault/network`
- Responsibility: API clients, token, interceptors, transport
- Must not contain business rules

### `data`
- Path: `android/app/src/main/java/com/ttt/safevault/data`
- Responsibility: local persistence (database/dao/entity)
- Must not orchestrate feature flows

## Backend Standards

### `controller`
- Path: `server/src/main/java/org/ttt/safevaultbackend/controller`
- Responsibility: HTTP boundary, request/response mapping
- Can depend on: `service`, `dto`, framework validation/security annotations
- Must not directly call repositories or own business workflows

### `service`
- Path: `server/src/main/java/org/ttt/safevaultbackend/service`
- Responsibility: backend use-case orchestration
- Can depend on: `repository`, `entity`, `security`, `websocket`, `dto`
- Owns transactions, authorization decisions, and notification orchestration

### `repository`
- Path: `server/src/main/java/org/ttt/safevaultbackend/repository`
- Responsibility: persistence access
- Must not contain business workflows or controller-facing DTO mapping

### `entity`
- Path: `server/src/main/java/org/ttt/safevaultbackend/entity`
- Responsibility: storage entities
- Must not be exposed directly as public API response contracts

### `security`
- Path: `server/src/main/java/org/ttt/safevaultbackend/security`
- Responsibility: authn/authz and security infrastructure

### `dto`
- Path: `server/src/main/java/org/ttt/safevaultbackend/dto`
- Responsibility: external API request/response contracts

### `websocket`
- Path: `server/src/main/java/org/ttt/safevaultbackend/websocket`
- Responsibility: WebSocket/STOMP infrastructure and connection handling
- Publishing application notifications should be mediated through service-level methods

### `modules`
- Path: `server/src/main/java/org/ttt/safevaultbackend/modules`
- Responsibility: logical ownership markers only
- Must not receive implementation classes during the backend service-boundary refactor

## Dependency Direction Rules

### Android
`ui -> viewmodel -> model/service -> (security|crypto|network|data)`

### Backend
`controller -> service -> repository/entity`

Supporting backend infrastructure packages such as `security`, `websocket`,
`config`, `annotation`, `aspect`, `exception`, and `util` are consumed from the
layer that owns the relevant boundary. For example, notification publishing is
called from services through `WebSocketService`, while STOMP transport details
stay under `websocket`.

## Forbidden Patterns

- Android `service`/`viewmodel` depending on `ui`
- `network` containing business branching logic
- `crypto` containing Android UI flow code
- backend `controller` directly calling `repository` (bypass `service`)
- moving backend implementation into `modules/*` packages without a separate approved proposal

## Change Management

- Structural refactor changes must be tracked with OpenSpec change records
- Functional changes and structural changes should be split into different PRs
