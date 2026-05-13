# Backend Service Boundary Map

## Purpose

This map records the current backend ownership boundaries for the
`refactor-backend-service-boundaries` change. The approved direction is to
strengthen the existing Spring layered structure:

`controller -> service -> repository/entity`

This change does not move implementation into domain-module packages.

## Controller Boundary Map

| Controller | Service boundary | Main DTO contracts |
| --- | --- | --- |
| `AuthController` | `AuthService` | auth, email registration, verification, login, logout, device DTOs |
| `AccountController` | `AccountService` | `DeleteAccountResponse` |
| `UserController` | `UserService` | profile, search, key info, QR code, statistics, ECC key DTOs |
| `VaultController` | `VaultService`, `PrivateKeyService` | vault init/sync and private-key DTOs |
| `FriendController` | `FriendService`, `UserService` | friend request, friend list, user search DTOs |
| `ContactShareController` | `ContactShareService`, `UserService` | contact share create/receive/accept/sent/received DTOs |
| `VerificationWebController` | none | static verification result view |

Controllers currently delegate to services and do not directly access
repositories.

## Service Dependency Map

| Service | Primary responsibility | Persistence/security/infra dependencies |
| --- | --- | --- |
| `AuthService` | registration, login, token refresh, email verification, logout, devices | `UserRepository`, `UserPrivateKeyRepository`, `JwtTokenProvider`, `Argon2PasswordHasher`, verification/token/pending-user services |
| `AccountService` | account deletion workflow | user, vault, share repositories |
| `UserService` | profile, search, QR/statistics, ECC key upload | user/share repositories, security context |
| `VaultService` | encrypted vault lifecycle | vault/user repositories |
| `PrivateKeyService` | encrypted private-key lifecycle | private-key/user repositories |
| `FriendServiceImpl` | friend request/list/delete/search flows | user/friend repositories, `WebSocketService`, connection manager |
| `ContactShareService` | contact share create/receive/accept/revoke/list/expiry flows | share/user/friend repositories, `WebSocketService` |
| `VerificationTokenService` | persisted verification token lifecycle | user repository |
| `VerificationEventService` | verification audit events | verification event repository |
| `EmailVerificationHistoryService` | email verification delivery history | email verification history repository |
| `TokenRevokeService` | token/device revocation | revoked token repository, `JwtTokenProvider` |
| `RegistrationCleanupService` | cleanup timed-out pending registration records | user repository, verification event service |
| `PendingUserService` | Redis-backed pending registration state | Redis template |
| `NonceService` | Redis-backed login nonce state | Redis template |
| `EmailService` | email delivery | mail sender/config |
| `CryptoService` | cryptographic utility operations | JCA/Bouncy Castle |
| `WebSocketServiceImpl` | notification publish boundary | STOMP messaging template, connection manager |

## Hotspot Inventory

- `AuthService` is the largest mixed-use-case class. It owns multiple related
  auth concerns; future slices should extract focused collaborators inside the
  `service` package without changing controller DTOs or token behavior.
- `ContactShareService` combines share workflow, response mapping, expiry, and
  notification assembly. Notification publishing is already mediated by
  `WebSocketService`.
- `PendingUserService`, `CryptoService`, `VaultService`, and `UserService` are
  medium-sized services that should be touched only for focused helper
  extraction or boundary cleanup.
- `modules/*` packages are documentation markers only for this change.

## Flyway Baseline

Current migration files are `V1`, `V2`, `V3`, `V4`, `V7`, `V8`, `V9`, and
`V10` through `V26`. Existing migration files are append-only history and must
not be rewritten by this structural refactor.
