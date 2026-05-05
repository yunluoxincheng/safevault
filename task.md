# SafeVault Refactor Task Log

Last updated: 2026-05-06

## Current User Goal

Prepare this course final project for a whole-project cleanup/refactor. The user feels the structure and documentation are messy and wants future conversations to have reliable project memory before coding.

## Work Completed This Round

- Explored the repository structure, Android module, Spring Boot backend, docs, OpenSpec records, and top-level memory files.
- Rewrote the main agent memory in `AGENTS.md`.
- Added current authoritative OpenSpec context at the top of `openspec/project.md`.
- Updated `AI_RULES.md` and `Android_rules.md` so they match the real Java/XML + Spring Boot project instead of generic stale templates.
- Created this `task.md` because project instructions require it to be updated.
- Audited pre-refactor Git/OpenSpec hygiene and completed the cleanup snapshot so broad refactor can begin from a clean baseline.
- User confirmed the latest OpenSpec layout removes `openspec/AGENTS.md` and replaces old assistant command files with `.codex/skills/openspec-*`.

No runtime code was changed.

## Repository Inventory

- Root project: Android Gradle project named `SafeVault`, includes only `:app`.
- Android module: `app/`
  - Java source count observed: 253 files under `app/src/main/java`.
  - Layout XML count observed: 62 files under `app/src/main/res/layout`.
  - Main package: `com.ttt.safevault`.
  - Current top-level packages include `adapter`, `analytics`, `autofill`, `config`, `core`, `crypto`, `data`, `dto`, `exception`, `manager`, `model`, `network`, `receiver`, `security`, `service`, `sync`, `ui`, `utils`, `viewmodel`.
- Backend module: `safevault-backend/`
  - Separate nested Git repository.
  - Java source count observed: 145 files under `safevault-backend/src/main/java`.
  - Flyway migration count observed: 24 SQL files under `safevault-backend/src/main/resources/db/migration`.
  - Main package: `org.ttt.safevaultbackend`.
  - Current top-level packages include `annotation`, `aspect`, `config`, `controller`, `dto`, `entity`, `enums`, `exception`, `modules`, `repository`, `security`, `service`, `util`, `websocket`.
- Documentation:
  - Root docs include `README.md`, `implementation_plan.md`, Java 17 notes, and several Chinese development documents.
  - Main docs are under `docs/`.
  - Backend docs are under `safevault-backend/docs/`.
  - OpenSpec files are under `openspec/`.

## Key Technical Facts

- Android uses Java 17, minSdk 29, target/compile SDK 36, XML layouts, ViewBinding, Material Components, Navigation Component, Retrofit/OkHttp, RxJava, Room, WorkManager, Biometric, ZXing, Glide, Bouncy Castle, and Argon2Kt.
- Backend uses Java 17, Spring Boot 3.5.9, Maven, Spring Security, JWT RS256, JPA, Flyway, PostgreSQL, Redis, WebSocket/STOMP, SpringDoc OpenAPI, Bucket4j, Bouncy Castle, Argon2-JVM, Docker Compose.
- Android production API base URL is currently `https://server.safevaultapp.top/api/` in `ApiConstants`.
- Backend default context path is `/api` and default port is `8080`.
- Backend default active Spring profile is `dev`.

## Important Existing Guidance

- The current OpenSpec workflow is represented by skill files under `.codex/skills/openspec-*`; new capabilities, breaking changes, architecture shifts, performance behavior changes, and security pattern changes still require proposal-first workflow.
- `docs/directory-standards.md` defines intended dependency direction:
  - Android: `ui -> viewmodel -> model/service -> (security|crypto|network|data)`
  - Backend: `controller -> service -> repository/entity`
- Existing OpenSpec change `openspec/changes/refactor-project-structure/` appears completed and should not be reused for a new whole-project reorganization.

## Current Risks / Inconsistencies

- Some historical documentation is stale or appears garbled in terminal output.
- Older docs/templates said Android should use Compose/Hilt/StateFlow, but current code is Java/XML/ViewBinding with manual `ServiceLocator`.
- Some older docs describe frontend-only crypto, but current Android code has substantial `security` and `crypto` packages.
- `SafeVaultApplication` declares package `com.ttt.safevault.core`, Manifest references `.core.SafeVaultApplication`, but the source file path is still under `app/src/main/java/com/ttt/safevault/SafeVaultApplication.java`.
- Android UI classes still contain some direct Retrofit/security calls; future refactor can gradually move these toward ViewModel/service/manager.
- Backend has module marker packages under `modules/*`, but most implementation remains in traditional layered packages.
- Root Git status already had unrelated user changes before this doc pass:
  - modified `app/src/main/java/com/ttt/safevault/security/SecureKeyStorageManager.java`
  - modified nested `safevault-backend`
  - many untracked `.agents/skills/*` files
- Backend nested Git status had untracked `.kilo/plans/1775917009627-proud-planet.md`.
- Latest pre-refactor audit found root Git is still dirty:
  - deleted `.clinerules/workflows/openspec-apply.md`, `.clinerules/workflows/openspec-archive.md`, `.clinerules/workflows/openspec-proposal.md`, and `CLINE.md`
  - modified `AGENTS.md`, `AI_RULES.md`, `Android_rules.md`, `openspec/project.md`
  - modified `app/src/main/java/com/ttt/safevault/security/SecureKeyStorageManager.java`
  - modified `safevault-backend` gitlink from `a89027d` to `545bc3c`
  - untracked `.agents/skills/*` and `task.md`
- Latest pre-refactor cleanup committed backend `.kilo/plans/1775917009627-proud-planet.md` as backend commit `4b9dd97 docs: add email optimization plan`; backend working tree is clean and ahead of origin by 3 commits.
- Pre-refactor cleanup archived the completed `refactor-project-structure` change into `openspec/changes/archive/2026-05-06-refactor-project-structure/`, synced its delta into `openspec/specs/project-structure/spec.md`, and removed the empty `add-your-feature` scaffold.
- Root cleanup snapshot was committed as `4d79a38 chore: prepare refactor baseline` before final task-log polishing; the final amended commit should be checked with `git log -1 --oneline`.
- Root and backend `git diff --exit-code` / `git diff --cached --exit-code` passed after cleanup. `git submodule status` hit a local Git shell permission error, so the backend gitlink was verified with `git ls-files -s safevault-backend` instead.

## Recommended Next Steps

1. Create a new OpenSpec proposal for the larger repository/documentation refactor before moving code or deleting/migrating docs.
2. Establish baseline verification: Android `.\gradlew.bat test`, Android `.\gradlew.bat :app:assembleDebug`, backend `.\mvnw.cmd test`.
3. Build a documentation inventory and choose one canonical home for root docs, Android docs, backend docs, API docs, security docs, and user docs.
4. Decide whether backend should remain a nested Git repository or be treated as a true submodule/separate repo; do not change this casually.
5. Triage Android package boundaries and identify low-risk first refactors, especially UI classes directly calling network/security logic.
6. Triage backend package/module direction and decide whether to keep layered architecture or complete modular-monolith migration.

## Hard Constraints

- Do not batch-delete files or directories.
- Do not use recursive deletion commands.
- Do not revert user changes unless explicitly asked.
- Update this file when the plan, findings, or task status changes.
