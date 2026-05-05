
# SafeVault Agent Memory

## 必须遵守

- 禁止批量删除文件或目录。不要使用 `del /s`、`rd /s`、`rmdir /s`、`Remove-Item -Recurse`、`rm -rf`。
- 需要删除文件时，只能一次删除一个明确路径的文件，例如 `Remove-Item "C:\path\to\file.txt"`。
- 如果需要批量删除文件，停止操作并询问用户，让用户手动删除。
- 每次完成重要探索、方案调整、代码改动或验证结论后，都要同步更新 `task.md`。
- 工作区可能包含用户未提交改动。先看状态，理解后协作处理，不要回滚自己没有做的改动。

## 项目快照

SafeVault 是课程期末作业项目：一个 Android 密码管理器，包含原生 Android 客户端和 Spring Boot 后端。

- Android 客户端在 `app/`，主语言 Java 17，界面以 XML + ViewBinding 为主，使用 Material Components、ConstraintLayout、Navigation Component、LiveData/ViewModel、Retrofit/OkHttp、RxJava、Room、AndroidX Biometric、ZXing、Bouncy Castle、Argon2Kt。
- 后端在 `safevault-backend/`，是一个独立嵌套 Git 仓库，Spring Boot 3.5.9 + Java 17 + Maven，使用 Spring Security、JWT、JPA、Flyway、PostgreSQL、Redis、WebSocket/STOMP、SpringDoc OpenAPI、Bucket4j、Bouncy Castle、Argon2-JVM。
- 当前 Android 包名和 namespace 是 `com.ttt.safevault`。Manifest 使用 `.core.SafeVaultApplication`，但源文件路径仍在 `app/src/main/java/com/ttt/safevault/SafeVaultApplication.java`，重构时要核对包名和文件路径。
- 当前后端根包是 `org.ttt.safevaultbackend`。
- `docs/` 存放主要项目文档，`safevault-backend/docs/` 存放后端部署/模块化文档，`openspec/` 存放规格和变更提案。
- `build/`、`.gradle/`、`app/build/`、`safevault-backend/target/` 是生成物目录，探索时通常忽略。

## 重要文档入口

- `task.md`：当前任务状态、盘点结论、后续路线。
- `openspec/project.md`：OpenSpec 项目上下文和约定。
- `docs/directory-standards.md`：Android/后端目录职责和依赖方向。
- `docs/project-structure-reorganization.md`：已有结构整理记录。
- `docs/security-architecture.md`：安全架构说明。
- `docs/api/*.md`：后端 API、数据库、同步和密钥管理文档。
- `safevault-backend/README.md`：后端运行、部署、模块说明。

## OpenSpec 工作流

涉及新增能力、破坏性变更、架构迁移、大规模重构、安全/性能大改时，必须使用 `.codex/skills/openspec-*` 中的新版 OpenSpec skill 入口，并读取 `openspec/project.md`、相关 `openspec/specs/*/spec.md` 和当前 `openspec/changes/*`。

- 不要在没有确认的情况下直接开始架构级实现。
- 新的大规模项目结构重构应创建新的 verb-led change id，例如 `refactor-repository-layout`。
- 结构调整和功能行为变更应拆开处理。
- 已存在 `openspec/changes/refactor-project-structure/`，它记录的是一次已完成的结构文档化/包边界整理，不要把新的大改直接混进旧任务。

## 架构边界

Android 目标依赖方向：

`ui -> viewmodel -> model/service -> (network|security|crypto|data)`

- `ui` 只做界面展示和交互绑定，不直接承载复杂业务、网络重试、密钥生命周期或持久化规则。
- `viewmodel` 负责 UI 状态和用户意图编排，尽量调用 service/manager/network facade。
- `network` 负责 Retrofit、OkHttp、Token、WebSocket 和 API 接口，不放业务分支。
- `security` 和 `crypto` 涉及登录态、生物识别、KeyStore、Argon2、AES-GCM、X25519/Ed25519、HKDF 等，任何修改都先读相关实现和测试。
- 目前项目有 `ServiceLocator`，不要随手引入 Hilt/DI 大迁移，除非 OpenSpec 提案已批准。

后端目标依赖方向：

`controller -> service -> repository/entity`

- Controller 只处理 HTTP 边界、参数校验和 DTO 映射。
- Service 放业务流程、事务、安全策略编排。
- Repository/Entity 只做持久化边界。
- 外部接口使用 DTO，不要直接暴露 Entity。
- Flyway migration 只增量演进，避免手改已发布迁移。

## 安全原则

- 这是密码管理器。默认所有密码、密钥、token、主密码、验证码、私钥、分享包都是敏感数据。
- 不要在日志、异常、Toast、文档示例或测试输出中暴露真实秘密。
- 不要降低 TLS、证书校验、JWT 过期、Token revoke、KeyStore、生物识别、FLAG_SECURE、剪贴板自动清理等安全约束。
- 加密相关改动优先使用现有管理器和测试：`SecureKeyStorageManager`、`Argon2KeyDerivationManager`、`ShareEncryptionManager`、`TokenManager`、后端 `JwtTokenProvider`、`Argon2PasswordHasher`。
- 涉及旧 `KeyManager`、`CryptoManager`、`BackupCryptoUtil` 或三层安全架构迁移时，先查现有实现和迁移文档，不要凭印象替换。

## 常用验证

Windows PowerShell 下优先使用：

- Android 单元测试：`.\gradlew.bat test`
- Android 编译：`.\gradlew.bat :app:assembleDebug`
- 后端测试：在 `safevault-backend/` 下运行 `.\mvnw.cmd test`
- 后端启动：在 `safevault-backend/` 下运行 `.\mvnw.cmd spring-boot:run`
- 后端依赖服务：在 `safevault-backend/` 下使用 `docker-compose up -d postgres redis`

如果验证命令需要网络下载依赖而失败，按当前环境的审批规则请求权限。不要用破坏性清理命令绕过构建问题。

## 当前重构关注点

- 根目录文档较多，部分历史说明和真实代码可能不一致，需要先盘点再迁移。
- Android 包很多，`ui` 中仍可能有直接 Retrofit/安全逻辑调用，后续重构可逐步下沉到 ViewModel/service/manager。
- 后端已有 `modules/*/package-info.java`，但实际代码仍主要按传统 controller/service/repository 分层组织。
- `AI_RULES.md`、`Android_rules.md` 已用于简短规则记忆；以本文件和 `openspec/project.md` 为更高优先级。
