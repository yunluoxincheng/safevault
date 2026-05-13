# Project Context

> Current authoritative context updated on 2026-05-06. Treat this section as the first source of truth for new OpenSpec work; older notes lower in this file may be historical.

## Current Purpose

SafeVault is a course final project for an Android password manager. The repository contains two runtime parts:

- `android/`: native Android client for password management, account/security UI, autofill, vault sync, contact/friend flows, QR/Bluetooth/cloud password sharing, and local security controls.
- `server/`: Spring Boot backend for account/auth, email verification, vault sync metadata, contact sharing, WebSocket notifications, PostgreSQL persistence, Redis-backed transient state, token revocation, and API documentation.

Security-sensitive work must assume password entries, master passwords, tokens, verification codes, private keys, vault payloads, share packets, and crypto salts/tags are sensitive.

## Current Tech Stack

- Android: Java 17, Gradle/Android Gradle Plugin, minSdk 29, targetSdk/compileSdk 36, XML layouts, ViewBinding, Material Components, ConstraintLayout, Navigation Component, LiveData/ViewModel, Retrofit/OkHttp, RxJava 3, Room, WorkManager, AndroidX Biometric, ZXing, Glide, Bouncy Castle, Argon2Kt.
- Backend: Java 17, Spring Boot 3.5.9, Maven, Spring Web/Security/Validation/JPA/WebSocket/Data Redis/Mail/Thymeleaf, PostgreSQL with Flyway migrations, H2 for development/testing, JWT RS256, SpringDoc OpenAPI, Bucket4j, Bouncy Castle, Argon2-JVM, Docker Compose.

## Current Repository Topology

- `android/app/src/main/java/com/ttt/safevault/`: Android source. Top-level packages currently include `ui`, `viewmodel`, `model`, `service`, `service/manager`, `network`, `security`, `crypto`, `data`, `sync`, `autofill`, `adapter`, `dto`, `core`, and helpers.
- `android/app/src/main/res/`: XML layouts, drawables, navigation graph, menus, values, raw certs, and autofill/security XML resources.
- `server/src/main/java/org/ttt/safevaultbackend/`: backend source. Main packages include `controller`, `service`, `repository`, `entity`, `security`, `dto`, `config`, `websocket`, `annotation`, `aspect`, `exception`, `modules`, and `util`.
- `server/src/main/resources/db/migration/`: Flyway migrations, currently V1 through V26 with gaps from removed/legacy versions.
- `docs/`: main project documentation.
- `server/docs/`: backend-specific deployment and modularization documentation.
- `openspec/specs/`: current behavior/spec truth.
- `openspec/changes/`: proposed or historical changes.

The root SafeVault repository is the single source of truth. Android commands run from `android/`, backend commands run from `server/`.

## Current Architecture Conventions

Android dependency direction:

`ui -> viewmodel -> model/service -> (network|security|crypto|data)`

- `ui` should render screens and wire user interactions.
- `viewmodel` should coordinate UI state and user intents.
- `service` and `service/manager` should hold capability-level orchestration.
- `network` should stay transport/API focused.
- `security` and `crypto` should stay UI-independent where possible and preserve existing key lifecycle rules.
- Do not introduce Compose, Hilt, Kotlin, or StateFlow as a casual cleanup; those are architecture migrations and need an OpenSpec proposal.

Backend dependency direction:

`controller -> service -> repository/entity`

- Controllers should use DTOs and validation, not expose entities.
- Services own business workflows, transactions, security decisions, and WebSocket notification orchestration.
- Repositories and entities stay persistence-focused.
- Flyway migration files are append-only once used; prefer new migrations over editing old ones.

## Current Refactor Governance

- Large structure changes, package migrations, security architecture changes, API/schema changes, and behavior-altering performance work require a new OpenSpec change.
- Use the current OpenSpec skill entrypoints under `.codex/skills/openspec-*`, then read this file, active changes, and relevant specs before authoring proposals.
- Keep functional changes separate from structural cleanup when practical.
- Existing change `openspec/changes/refactor-project-structure/` documents a completed package-boundary/structure-documentation effort. Do not extend it for a new whole-repository reorganization.
- `docs/directory-standards.md` and package-level `package-info.java` files describe the intended boundaries, but verify against code because some historical docs may be stale.

## Verification Expectations

- Android: from `android/`, run `./gradlew test` and, for compile-sensitive changes, `./gradlew :app:assembleDebug`.
- Backend: from `server/`, run `./mvnw test`.
- OpenSpec: validate new changes with `openspec validate <change-id> --strict`.
- Documentation-only changes usually do not require full builds, but must keep `task.md` updated.

## Current Known Cleanup Targets

- Root documentation is scattered and partly duplicated across `README.md`, Chinese development docs, `docs/`, backend docs, and tool-specific memory files.
- Some legacy docs describe older assumptions, such as frontend-only crypto or Java 8. Inspect current code before relying on those statements.
- `Android_rules.md` previously referenced Compose/Hilt/StateFlow, but the current app is Java/XML/ViewBinding without Hilt.
- `SafeVaultApplication` declares package `com.ttt.safevault.core` while its file path appears under the root package directory; verify before package/file moves.
- Android UI classes still contain some direct network/security calls; future refactors can gradually move those toward ViewModel/service/manager boundaries.

## Documentation Layout Baseline (2026-05-06)

- Canonical documentation map is now defined in `docs/documentation-layout.md`.
- Root-level historical docs were moved to `docs/plans/legacy-root-docs/` and root now keeps pointer files for those entries.
- Backend documentation routing is now explicit:
  - cross-repository backend architecture in `docs/backend/`
  - API contracts and schema docs in `docs/api/`
  - backend-local deployment/runbooks in `server/docs/`
- Generated/build directory and cleanup guidance is tracked in `docs/operations/generated-artifacts-policy.md`.
- The repository uses a monorepo layout: `android/` and `server/` are tracked by the root SafeVault Git repository.

## Legacy Notes

## Purpose
SafeVault 是一个原生 Android 密码管理器应用，旨在为用户提供安全、便捷的密码存储和管理功能。
- 前端只负责 UI 和用户交互
- 所有加密和数据持久化由后端服务处理
- 支持 Android AutofillService 自动填充
- 支持生物识别认证

## Tech Stack
- **语言**: Java 17
- **架构**: MVVM (Model-View-ViewModel)
- **最小 SDK**: 29 (Android 10)
- **目标 SDK**: 36
- **UI 框架**: Material Components + ConstraintLayout
- **导航**: Android Navigation Component
- **视图绑定**: ViewBinding
- **安全**: Biometric authentication, FLAG_SECURE
- **包名**: `com.ttt.safevault`
- **构建命名空间**: `com.safevault`

## Project Conventions

### Code Style
- 包结构位于 `com.ttt.safevault/` 下
- 命名规范遵循 Java 约定
- 使用 Material Design 组件保持 UI 一致性
- 所有敏感数据操作必须通过 BackendService

### Architecture Patterns
- **MVVM 架构**: Activities/Fragments → ViewModels → BackendService
- **前端原则**: 前端绝不处理：
  - 加密/解密操作
  - 直接数据库访问
  - 明文密码存储
  - 敏感数据持久化
- **包结构**:
  ```
  com.ttt.safevault/
  ├── ui/           # UI 组件
  ├── viewmodel/    # MVVM ViewModels
  ├── model/        # 数据模型和 BackendService 接口
  ├── autofill/     # AutofillService 实现
  ├── security/     # 安全工具和配置
  ├── utils/        # 辅助类
  └── adapter/      # RecyclerView adapters
  ```

### Testing Strategy
- 单元测试: `./gradlew test`
- Android 测试: `./gradlew connectedAndroidTest`
- 测试应覆盖 ViewModel 和业务逻辑

### Git Workflow
- 主分支: `master`
- 提交信息遵循项目规范（参考现有提交格式）
- Pull Requests 基于 master 分支

## Domain Context
- **密码管理器核心功能**: 密码条目的增删改查、搜索、分类
- **安全特性**:
  - FLAG_SECURE 防止截屏
  - 剪贴板自动清除敏感数据
  - 应用后台时自动锁定
  - 生物识别认证
- **自动填充**: Android AutofillService 集成，支持其他应用自动填充密码

## Important Constraints
- **版本兼容**: 必须兼容 Android 10 (API 29) 及以上版本
- **安全隔离**: 前端代码不能直接处理加密或存储敏感数据
- **命名空间不一致**: 包名 (`com.ttt.safevault`) 与构建命名空间 (`com.safevault`) 需保持一致性
- **后端依赖**: 前端代码库依赖 BackendService 实现，当前存在占位符

## External Dependencies
- **BackendService**: 定义所有后端操作的接口
  - 返回已解密的 `PasswordItem` 对象
  - 处理搜索结果列表
  - 返回操作结果的原始类型
- **Android 系统**:
  - BiometricPrompt API
  - AutofillFramework
  - ClipboardManager
  - Navigation Component
