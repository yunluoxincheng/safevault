# Change: Refactor Backend Service Boundaries

## Why
The backend already has clear Spring layers and module marker packages, but the implementation is still mostly organized as broad controller/service/repository packages. As the project grows, large services and unclear module ownership make it harder to safely change auth, vault sync, contact sharing, email verification, and account flows.

## What Changes
- Inventory backend controllers, services, repositories, DTOs, entities, security classes, and module marker packages.
- Strengthen the current layered Spring Boot package structure rather than migrating to a modular monolith.
- Clarify service boundaries for auth, account, vault, contacts, sharing, email verification, token revocation, and private keys.
- Keep API contracts, Flyway migrations, database schema, token behavior, and security policy unchanged unless later proposals authorize changes.
- Improve package documentation and tests around moved or extracted service code.

## Non-Goals
- Do not rewrite the backend framework, persistence layer, or security stack.
- Do not edit existing Flyway migrations except for documentation comments in a separately approved migration policy.
- Do not change REST paths, response DTOs, WebSocket topics, JWT behavior, Redis keys, or mail templates as part of pure boundary cleanup.
- Do not merge or split the nested backend Git repository in this change.
- Do not move implementation into domain-module packages such as `auth`, `account`, `vault`, or `sharing`. A modular-monolith migration would require a separate proposal.

## Impact
- Affected specs: `project-structure`
- Affected code: backend Java source under `safevault-backend/src/main/java/org/ttt/safevaultbackend/**`, backend tests where needed
- Risk areas: auth/registration, token revocation, email verification, vault sync, contact sharing, WebSocket notification flow, private key storage
- Verification: `.\mvnw.cmd test` from `safevault-backend/`, API contract spot checks where practical
