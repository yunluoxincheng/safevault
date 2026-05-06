# SafeVault Refactor Task Log

Last updated: 2026-05-06

## Current User Goal

Prepare this course final project for a whole-project cleanup/refactor. The user feels the structure and documentation are messy and wants future conversations to have reliable project memory before coding. The current step is to create OpenSpec proposals before starting implementation.

## Latest Session Update (normalize-repository-layout apply)

- Implemented OpenSpec change tasks for `normalize-repository-layout` by directly using change artifacts because `openspec` CLI is not available in PATH in this environment.
- Added canonical documentation map: `docs/documentation-layout.md`.
- Rebuilt docs entry index: `docs/README.md`.
- Added explicit backend routing guidance: `docs/backend/README.md`.
- Added generated/build artifact cleanup policy: `docs/operations/generated-artifacts-policy.md`.
- Added structural placeholders for canonical homes:
  - `docs/android/README.md`
  - `docs/security/README.md`
  - `docs/changelog/README.md`
- Moved historical root docs into `docs/plans/legacy-root-docs/` and replaced root versions with concise pointer files:
  - `implementation_plan.md`
  - `JAVA_17_CHANGES_SUMMARY.md`
  - `JAVA_17_UPGRADE.md`
  - `SafeVault 开发文档.md`
  - `SafeVault 开发文档前端.md`
  - `SafeVault 开发文档后端.md`
  - `图标映射清单.md`
- Updated root entrypoint `README.md` to a concise repo map.
- Updated project memory references:
  - `openspec/project.md` (documentation baseline section)
  - `AGENTS.md` (documentation layout update section)
- Marked all tasks complete in `openspec/changes/normalize-repository-layout/tasks.md`.

Verification notes:

- Root and backend Git statuses were checked separately.
- Backend remains a nested repository tracked by root as gitlink mode `160000`.
- Manual reference spot checks were run on the new docs and pointers.
- No runtime Java/XML/SQL files were changed in this session.

Remaining documentation debt:

- Several historical Chinese documents still show terminal encoding/mojibake symptoms and need a dedicated encoding-normalization pass (separate scoped change recommended).
- Existing historical planning docs may include superseded architecture assumptions and should be revalidated before reuse.

## Work Completed This Round

- Explored the repository structure, Android module, Spring Boot backend, docs, OpenSpec records, and top-level memory files.
- Rewrote the main agent memory in `AGENTS.md`.
- Added current authoritative OpenSpec context at the top of `openspec/project.md`.
- Updated `AI_RULES.md` and `Android_rules.md` so they match the real Java/XML + Spring Boot project instead of generic stale templates.
- Created this `task.md` because project instructions require it to be updated.
- Audited pre-refactor Git/OpenSpec hygiene and completed the cleanup snapshot so broad refactor can begin from a clean baseline.
- User confirmed the latest OpenSpec layout removes `openspec/AGENTS.md` and replaces old assistant command files with `.codex/skills/openspec-*`.
- Created a first OpenSpec proposal set for the upcoming large refactor:
  - `openspec/changes/normalize-repository-layout/`
  - `openspec/changes/refactor-android-layer-boundaries/`
  - `openspec/changes/refactor-backend-service-boundaries/`
- Reviewed the three active OpenSpec proposals for approval readiness, scope overlap, and missing specification details.
- Revised the active proposals after review:
  - `refactor-backend-service-boundaries` now commits to strengthening the existing layered Spring Boot structure and explicitly excludes modular-monolith migration.
  - `normalize-repository-layout` now defines strict routing between `docs/backend/`, `docs/api/`, and `safevault-backend/docs/`.
  - `normalize-repository-layout` now adds a generated/build-directory and cleanup-policy requirement.
  - `refactor-android-layer-boundaries` now narrows bootstrap/package consistency work to layer-boundary or manifest-visible issues discovered during inventory.
- Re-reviewed the revised proposals and confirmed the previously identified approval blockers are addressed. No new blocking spec/design inconsistencies were found in this review pass.
- Ran implementation-prep baseline verification after proposal approval review:
  - Android `.\gradlew.bat test` reached test execution but failed; this is recorded as a pre-existing baseline failure before refactor implementation.
  - Android `.\gradlew.bat :app:assembleDebug` passed.
  - Backend `.\mvnw.cmd test` reached test compilation but failed; this is recorded as a pre-existing baseline failure before refactor implementation.

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

## Active OpenSpec Proposal Set

The broad refactor is intentionally split into three active changes:

1. `normalize-repository-layout`
   - Scope: documentation information architecture, root docs cleanup, backend nested-Git policy, generated-directory policy.
   - Non-goal: no runtime Java/XML/SQL behavior changes.
2. `refactor-android-layer-boundaries`
   - Scope: Android package/layer boundary cleanup, especially UI classes directly calling `network`, `security`, `crypto`, or `data`.
   - Non-goal: no Compose/Hilt/Kotlin/StateFlow migration and no security/API behavior changes.
3. `refactor-backend-service-boundaries`
   - Scope: backend controller/service/repository and module ownership cleanup.
   - Non-goal: no REST contract, Flyway migration, JWT, Redis, WebSocket, or schema behavior changes.

OpenSpec CLI was not available in PATH during proposal creation, so artifacts were created manually in the existing repository format. Run `openspec validate <change-id> --strict` later if the CLI becomes available.

## Latest Proposal Review Findings

- `normalize-repository-layout` still leaves backend documentation ownership ambiguous: the design names both `docs/backend/` and `safevault-backend/docs/` as target homes, but it does not define a hard routing rule for which backend documents belong in each location.
- `normalize-repository-layout` promises generated/build-directory and cleanup-rule documentation, but its current spec delta does not add a requirement for that policy, so the scope is not fully enforceable yet.
- `refactor-backend-service-boundaries` is still under-specified because it intentionally defers the major architecture choice between strengthened layered packaging and incremental modular-monolith packaging. That choice is large enough that it should be decided before approval or split into separate changes.
- `refactor-android-layer-boundaries` is the most implementation-ready of the three, but its bootstrap/package-consistency wording should stay narrowly tied to Android layer-boundary work so it does not become a catch-all structure move.

## Latest Proposal Revisions

- `refactor-backend-service-boundaries` review blocker addressed: the approved direction is strengthened layered architecture, not modular-monolith migration. Domain-module package movement is now explicitly out of scope.
- `normalize-repository-layout` review blocker addressed: backend documentation routing is now explicit:
  - backend-local operational/deployment/build docs stay in `safevault-backend/docs/`
  - full-repository backend architecture and Android integration docs go to `docs/backend/`
  - API contracts and schema-facing docs go to `docs/api/`
  - duplicate backend docs should become pointers, not parallel canonical copies
- `normalize-repository-layout` spec gap addressed: generated/build-directory policy and safe cleanup rules are now formal requirements in the spec delta.
- `refactor-android-layer-boundaries` suggestion addressed: bootstrap/package consistency wording is limited to Android layer-boundary work or manifest-visible bootstrap placement found during inventory.

## Latest Re-Review Conclusion

- The earlier proposal-review blockers are resolved in the current drafts.
- `refactor-backend-service-boundaries` now has a stable scope and acceptance direction centered on strengthening the existing Spring layered structure.
- `normalize-repository-layout` now has enforceable backend-document routing and cleanup-policy requirements.
- `refactor-android-layer-boundaries` now keeps package/bootstrap cleanup tightly scoped to boundary-related findings.
- Remaining risk is implementation complexity, not proposal ambiguity.

## Baseline Verification Results

Commands were run on 2026-05-06 before starting refactor implementation.

Status decision: the Android unit-test failures and backend test-compilation failure are treated as known pre-existing baseline failures. They should not be attributed to future refactor slices unless a later slice changes the failing area or worsens the failure set. New refactor work should still keep `.\gradlew.bat :app:assembleDebug` passing and should avoid introducing new failures beyond this recorded baseline.

- Android unit tests: `.\gradlew.bat test`
  - Result: failed after running `:app:testDebugUnitTest`; marked as pre-existing before refactor implementation.
  - Summary: 166 tests completed, 35 failed.
  - Report: `app/build/reports/tests/testDebugUnitTest/index.html`.
  - Main failure groups observed:
    - `Argon2KeyDerivationManagerTest`: `UnsatisfiedLinkError: no argon2jni in java.library.path`.
    - share encryption tests: Android framework methods such as `android.util.Log` and `android.util.Base64` are not mocked in local JVM tests.
    - `SecurePaddingPerformanceTest`: current thresholds fail for memory/database-size impact.
    - `X25519KeyManagerTest`: key uniqueness/shared-key uniqueness/factory creation failures.
- Android debug compile: `.\gradlew.bat :app:assembleDebug`
  - Result: passed.
  - Notes: Gradle reported deprecation warnings and configuration/performance suggestions, but produced the debug APK successfully.
- Backend tests: `.\mvnw.cmd test`
  - Result: failed during Maven `testCompile`; marked as pre-existing before refactor implementation.
  - No Surefire reports were generated because test compilation failed before execution.
  - Compile errors are in `CryptoKeyManagementIntegrationTest`:
    - `UserRepository.deleteByEmail(String)` is referenced but not present.
    - `JwtTokenProvider.generateToken(String, String)` is referenced but not present.
  - Maven also warned that `org.testng:testng` uses `LATEST` or `RELEASE`, and Bucket4j artifacts are relocated.
- OpenSpec CLI validation: not run, because `openspec` is not in PATH.
- Initial non-escalated Gradle run failed on `D:\DevCache\.gradle` lock-file access; commands were rerun with approved elevated permissions.

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
- Root cleanup snapshot was amended to `9dd4159 chore: prepare refactor baseline`.
- Root and backend `git diff --exit-code` / `git diff --cached --exit-code` passed after cleanup. `git submodule status` hit a local Git shell permission error, so the backend gitlink was verified with `git ls-files -s safevault-backend` instead.

## Recommended Next Steps

1. Treat the current Android unit-test failures and backend test-compilation failure as known pre-existing baseline failures unless future work changes those areas.
2. Start with `normalize-repository-layout` so documentation and repository-boundary rules are stable before moving code.
3. Then implement `refactor-android-layer-boundaries` in feature slices.
4. Then implement `refactor-backend-service-boundaries` in backend use-case slices.

## Hard Constraints

- Do not batch-delete files or directories.
- Do not use recursive deletion commands.
- Do not revert user changes unless explicitly asked.
- Update this file when the plan, findings, or task status changes.
