## 1. Pre-Migration Inventory

- [x] 1.1 Record root and backend Git status, branches, and remotes before moving files.
- [x] 1.2 Inventory root files and classify them as Android runtime, backend runtime, cross-project documentation, OpenSpec state, agent/project guidance, generated output, or local-only files.
- [x] 1.3 Search for path references to `app/`, `safevault-backend/`, Gradle commands, Maven commands, backend Git status, and backend remote repository names.
- [x] 1.4 Identify generated/build/cache directories that should not be migrated as source and handle them according to the repository cleanup policy without bulk deletion commands.
- [x] 1.5 Decide whether currently ignored governance files such as `openspec/` and `task.md` should become committed root-repository files or remain local-only guidance, then update ignore/tracking guidance accordingly.
- [x] 1.6 Inventory backend tracked files that look like secrets, certificates, keystores, binary configuration, or environment-specific files, including any tracked `.p12` files, and record whether each file is safe to import, should be replaced with an example, or should be deferred to a separate security cleanup.

## 2. Repository Layout Migration

- [x] 2.1 Create the `android/` and `server/` runtime root directories.
- [x] 2.2 Move Android project files, Gradle wrapper files, Gradle settings/build files, and the `app/` module under `android/`.
- [x] 2.3 Import backend tracked source/configuration files, Maven wrapper files, backend docs, Docker Compose files, and supporting backend files from `safevault-backend/` under `server/`, explicitly excluding `.git`, generated outputs, caches, local dependency repositories, and files rejected by the sensitive/binary review.
- [x] 2.4 Confirm the imported `server/` tree has no nested `.git` boundary and root Git tracks `server/` files directly.
- [x] 2.5 Preserve cross-project files at the repository root, including `docs/`, `openspec/`, `task.md`, root README/guidance files, and agent/project memory files that are intended to remain tracked.
- [x] 2.6 Leave the old local `safevault-backend/` directory in place for manual cleanup or separately confirmed safe removal; do not recursively delete it during the migration.

## 3. Build and Tooling Updates

- [x] 3.1 Update Android Gradle settings and relative paths so Android commands run from `android/`.
- [x] 3.2 Update backend Maven, Docker Compose, and backend-local path references so backend commands run from `server/`.
- [x] 3.3 Update `.gitignore` and any repository tooling references so generated Android and backend outputs are ignored at their new paths.
- [x] 3.4 Confirm root Git status shows backend files under `server/` as regular root-repository changes, not as a nested repository or gitlink.

## 4. Documentation and OpenSpec Updates

- [x] 4.1 Update root README/setup guidance to describe the monorepo layout and new Android/backend working directories.
- [x] 4.2 Update `docs/` references to the old Android and backend paths.
- [x] 4.3 Update backend-local documentation moved under `server/docs/` for the new backend working directory.
- [x] 4.4 Update backend documentation routing references from `safevault-backend/docs/` to `server/docs/`.
- [x] 4.5 Update `openspec/project.md` and `openspec/specs/project-structure/spec.md` to make `android/` and `server/` canonical.
- [x] 4.6 Update `task.md` after completing the major migration and documentation updates.

## 5. Verification

- [x] 5.1 From `android/`, run Android unit-test verification or document any pre-existing blocker.
- [x] 5.2 From `android/`, run Android debug compile verification or document any pre-existing blocker.
- [x] 5.3 From `server/`, run backend test verification or document any pre-existing blocker.
- [x] 5.4 Run OpenSpec validation for `refactor-repository-layout`.
- [x] 5.5 Review root Git diff/status to confirm the migration contains only intended structure, documentation, and build-entrypoint changes.

### Verification Blockers (Pre-existing, Not Migration-Related)

**Android (5.1, 5.2):**
- `./gradlew test` and `./gradlew :app:assembleDebug` from `android/` fail in WSL because build-tools 36.0.0 AAPT binary is not available at `/mnt/e/Android/SDK/build-tools/36.0.0/aapt`. The Android SDK is installed on Windows and the Linux AAPT binary is missing. In Windows PowerShell with `local.properties` using `sdk.dir=E\\:\\Android\\SDK`, this would work. The migration correctly relocated the Gradle project and `local.properties` needs the platform-appropriate SDK path.

**Backend (5.3):**
- `./mvnw test` from `server/`: 38 tests, 37 passed, 1 error. `RegistrationFlowIntegrationTest.completeRegistrationFlow_Success` fails because Redis is not running at `localhost:6379`. This integration test requires external services (Redis) that are not started in the current environment. All unit tests pass.

## 6. Remote Repository Transition

- [x] 6.1 Document that `git@github.com:yunluoxincheng/SafeVault.git` is the active repository after migration.
- [x] 6.2 Document that `https://github.com/yunluoxincheng/safevault-backend.git` is retired after migration and may be deleted by the repository owner after verification.
- [x] 6.3 After the monorepo migration is verified and pushed, perform any remote backend repository deletion or archival manually outside the local implementation tasks.

---

## Inventory Record

### 1.1 Git Status

**Root repository (master)**
- Remote: `git@github.com:yunluoxincheng/SafeVault.git`
- Branch: `master` (up to date with `origin/master`)
- Latest commit: `b772e4b refactor: enforce security/biometric boundary contracts`
- 711 tracked files
- Many unstaged changes to `.agents/`, `.claude/`, `.codex/` skill files (local agent tooling, non-blocking)

**Backend repository (master)**
- Remote: `https://github.com/yunluoxincheng/safevault-backend.git`
- Branch: `master` (up to date with `origin/master`)
- Latest commit: `c760c8f 后端.gitignore更新`
- 212 tracked files
- Many unstaged changes to source, config, tests (pending work; snapshot import will use current working tree)

### 1.2 Root File Classification

| Category | Files/Directories |
|---|---|
| Android runtime | `app/`, `build.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `settings.gradle`, `gradle/` |
| Backend runtime | `safevault-backend/` (nested independent Git repo) |
| Cross-project docs | `docs/`, `README.md`, `AGENTS.md`, `AI_RULES.md`, `Android_rules.md` |
| Project config | `CLAUDE.md`, `.gitignore` |
| App assets (root-level) | `safevault_icon.png`, `safevault_icon-1.png`, `safevault_icon.svg` |
| OpenSpec state | `openspec/` (ignored by `.gitignore`) |
| Agent guidance (tracked) | `.agents/`, `.codex/` |
| Agent guidance (ignored) | `.claude/`, `.sixth/` |
| CI/CD | `.github/` |
| Generated/build | `build/`, `app/build/`, `.gradle/`, `.gradle-local/`, `.idea/` |
| Local-only | `local.properties`, `task.md` (ignored), `.worktrees/` (ignored) |

### 1.3 Path References to Update

Files referencing `safevault-backend/` (must update after migration):
- `CLAUDE.md` (lines 8, 23)
- `README.md` (lines 3, 8, 10, 24)
- `AGENTS.md` (lines 17, 20, 21, 31, 78–80, 98)
- `AI_RULES.md` (line 40)
- `.idea/vcs.xml` (VCS mapping)
- `docs/README.md`, `docs/documentation-layout.md`, `docs/directory-standards.md`
- `docs/project-structure-reorganization.md`
- `docs/api/manual-verification-checklist.md`, `docs/api/migration-guide-v2.2.md`
- `docs/backend/README.md`
- `docs/operations/generated-artifacts-policy.md`

Files referencing `app/` paths (must update after migration):
- `CLAUDE.md`, `README.md`, `AGENTS.md`
- `docs/autofill-implementation.md`, `docs/directory-standards.md`, `docs/documentation-layout.md`
- `docs/operations/generated-artifacts-policy.md`
- `.agents/skills/android-security-architecture-upgrade/*`
- `docs/plans/2026-01-19-*`, `docs/plans/2026-01-21-*`

Gradle/Maven command references (must update working directory):
- `AI_RULES.md` (lines 39–40), `AGENTS.md` (lines 76–80), `Android_rules.md` (lines 40–41)
- `README.md` (lines 22–24)
- `docs/api/manual-verification-checklist.md` (lines 8–9, 91–98)
- `docs/api/migration-guide-v2.2.md` (lines 77, 105, 214)

### 1.4 Generated/Build/Cache Directories

| Directory | Location | In .gitignore | Migration Action |
|---|---|---|---|
| `.gradle/` | Root | Yes | Do not migrate; recreated by Gradle |
| `build/` | Root | Yes | Do not migrate |
| `app/build/` | Root | Yes | Do not migrate |
| `.gradle-local/` | Root | Yes | Do not migrate |
| `.idea/` | Root | Partial | Do not migrate; IDE-local |
| `target/` | Backend | Yes (backend .gitignore) | Do not migrate |
| `.mvn/wrapper/maven-wrapper.jar` | Backend | Yes (backend .gitignore) | Do not migrate (wrapper jar regenerated by `mvn wrapper:wrapper`) |
| `.m2repo/`, `.m2-local/`, `.maven-wrapper-home/` | Backend | Yes (backend .gitignore) | Do not migrate; local dependency caches |

### 1.5 Governance Files Decision

| File/Dir | Current Status | Decision |
|---|---|---|
| `openspec/` | Ignored by root `.gitignore` | **Commit to root repo.** Contains specs, project config, and change history that are part of project governance and should be portable across clones. Remove `openspec/` line from `.gitignore`. |
| `task.md` | Ignored by root `.gitignore` | **Keep local-only.** Volatile working document, not suitable for version control. |
| `.claude/` | Ignored by root `.gitignore` | **Keep local-only.** Personal Claude Code settings and local configuration. |
| `.agents/` | Tracked by root Git | **Keep tracked.** Shared agent skills relevant to the project. |
| `.codex/` | Tracked by root Git | **Keep tracked.** Shared Codex/OpenSpec skills. |
| `.sixth/` | Ignored (not in .gitignore but under `.claude/`-like pattern) | **Keep local-only.** AI tool local state. |

Migration action: Remove `openspec/` from root `.gitignore` so the `openspec/` directory becomes tracked governance.

### 1.6 Backend Sensitive/Binary File Review

| File | Type | Size | Tracked Since | Decision |
|---|---|---|---|---|
| `src/main/resources/keystore.p12` | Binary keystore | 2766 bytes | Commit `c3489de` (before `*.p12` gitignore rule) | **Replace with placeholder + generation instructions.** This is a self-signed dev cert for `server.safevaultapp.top` (CN=TTT). Backend `.gitignore` already excludes `*.p12`. Import a README or script showing how to generate a local keystore instead of the binary. Do not import the `.p12` file itself. |
| `.env.example` | Env template | Text | Tracked | **Safe to import.** Contains only placeholder comments, no real secrets. |
| `scripts/deploy/.env.prod.example` | Env template | Text | Tracked | **Safe to import.** Template only. |
| `src/main/resources/application-dev.yml` | Config | Text | Tracked | **Review before import.** May contain dev-specific secrets or credentials; inspect content during import and redact if needed. |
| `src/main/resources/application-prod.yml` | Config | Text | Tracked | **Review before import.** May reference prod secrets via env vars; safe if it uses `${VAR}` placeholders only. |
| `.kilo/plans/1775917009627-proud-planet.md` | AI tool plan | Text | Tracked | **Exclude from import.** Non-essential tool artifact, not project source. |

Android side (tracked by root repo):
- `app/src/main/res/raw/safevault_cert.pem` — Public certificate (1391 bytes, no private key). Used for SSL pinning. **Safe to keep.** Will move with `app/` under `android/`.
