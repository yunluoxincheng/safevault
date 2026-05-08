# SafeVault 后续开发计划

> 适用项目：SafeVault Android 密码管理器  
> 目标：将原结课作业项目逐步升级为可展示、可维护、可扩展的零知识 Android 密码管理器项目。  
> 建议使用方式：将本文作为阶段路线图，每个阶段拆成一个或多个 OpenSpec 变更提案，避免一次性大改。

---

## 1. 总体目标

SafeVault 当前已经具备 Android 客户端、Spring Boot 后端、加密设计、密码分享、WebSocket、文档与 OpenSpec 管理基础。下一阶段不建议继续盲目堆功能，而应围绕以下目标推进：

1. 跑通核心密码库闭环。
2. 收敛 Android 与后端架构边界。
3. 固化安全模型与密钥生命周期。
4. 完善测试、CI、部署和文档。
5. 将项目包装成可用于简历、毕设、作品集展示的完整系统。

最终目标是让 SafeVault 成为一个具备以下特征的项目：

- Android 原生密码管理器
- 客户端加密
- 服务端零知识存储
- 云同步
- 生物识别解锁
- 自动填充
- 密码分享
- WebSocket 通知
- Docker 部署
- OpenAPI 文档
- 自动化测试与 CI
- 清晰的架构文档和安全设计文档

---

## 2. 当前阶段判断

当前 SafeVault 不再是简单的课程作业项目，而是一个已经进入持续重构和工程化阶段的完整应用。  
接下来最重要的不是立刻上微服务、消息队列、更多分享方式，而是先完成稳定的主流程。

当前应优先解决的问题：

- 主流程是否稳定可运行。
- Android 与后端接口是否完全对齐。
- Activity/Fragment 是否仍承担过多业务逻辑。
- 密钥初始化、登录、解锁、同步、锁定是否有清晰生命周期。
- Debug 与 Release 安全配置是否分离。
- 测试与 CI 是否可以稳定验证项目。
- README 与文档是否足够支持他人运行和理解项目。

---

## 3. 分阶段开发路线

推荐分为 6 个阶段推进。

```text
阶段 1：核心密码库闭环稳定化
阶段 2：Android 架构边界收敛
阶段 3：认证、密钥与安全生命周期固化
阶段 4：密码分享与协作能力完善
阶段 5：工程化、部署与 CI
阶段 6：项目展示与文档包装
```

---

# 阶段 1：核心密码库闭环稳定化

## 1.1 阶段目标

优先跑通 SafeVault 作为密码管理器最核心的业务闭环：

```text
注册账号
→ 设置主密码
→ 初始化本地/云端密钥
→ 登录
→ 新增密码
→ 本地加密保存
→ 上传密文到后端
→ 退出/锁定
→ 重新登录
→ 拉取密文
→ 客户端解密展示
```

这个阶段完成后，SafeVault 才真正具备“密码管理器主流程可用”的基础。

## 1.2 建议 OpenSpec 变更提案

建议创建：

```text
openspec/changes/stabilize-core-vault-sync-flow/
```

### proposal.md 建议目标

```md
# Stabilize Core Vault Sync Flow

## Why

SafeVault has grown beyond the original course project. The next step is to make the core password-manager flow stable before adding more advanced sharing or discovery features.

## What Changes

- Define the canonical register/login/vault-init/unlock/sync flow.
- Ensure Android and backend API contracts match.
- Stabilize local encrypted vault CRUD.
- Stabilize cloud sync of encrypted vault records.
- Add integration tests and manual verification checklist.

## Non-Goals

- No microservice split.
- No new share protocol.
- No UI redesign.
- No database migration rewrite unless required by the vault contract.
```

## 1.3 任务清单

```md
## 1. Inventory

- [ ] 1.1 List current Android vault-related classes
- [ ] 1.2 List current backend vault/private-key endpoints
- [ ] 1.3 Compare Android API models with backend DTOs
- [ ] 1.4 Document current key lifecycle

## 2. Contract

- [ ] 2.1 Define vault init contract
- [ ] 2.2 Define encrypted item CRUD contract
- [ ] 2.3 Define sync pull/push contract
- [ ] 2.4 Define error codes and retry behavior

## 3. Android

- [ ] 3.1 Move vault UI orchestration into ViewModel
- [ ] 3.2 Ensure UI does not directly access crypto/data/network
- [ ] 3.3 Add locked/unlocked vault state model
- [ ] 3.4 Add sync status state model

## 4. Backend

- [ ] 4.1 Ensure VaultController delegates only to service
- [ ] 4.2 Ensure VaultService owns sync transaction logic
- [ ] 4.3 Ensure DTOs never expose entity internals
- [ ] 4.4 Ensure Flyway migrations are append-only

## 5. Verification

- [ ] 5.1 Android assembleDebug passes
- [ ] 5.2 Android unit tests pass
- [ ] 5.3 Backend mvnw test passes
- [ ] 5.4 Manual register-login-create-sync-relogin-decrypt flow passes
```

## 1.4 验收标准

本阶段完成后，应满足：

- Android debug build 稳定通过。
- Android unit test 稳定通过。
- 后端 `mvnw test` 稳定通过。
- 后端可以通过 Docker Compose 启动依赖服务。
- App 可以成功连接后端。
- 注册、登录、新增密码、同步、重新登录解密完整跑通。
- 服务端只存储密文，不存储明文密码。
- README 或文档中记录完整手动验证流程。

---

# 阶段 2：Android 架构边界收敛

## 2.1 阶段目标

将 Android 客户端从“Activity/Fragment 承担大量业务逻辑”的结构，逐步收敛为清晰的分层结构。

推荐目标依赖方向：

```text
ui -> viewmodel -> model/service -> security|crypto|network|data
```

各层职责建议：

```text
Activity / Fragment:
- 页面渲染
- 用户点击事件
- 权限请求
- 页面跳转

ViewModel:
- 页面状态
- 加载中/错误/成功
- 调用 Manager/Service

Manager / Service:
- token/session
- 数据库
- 网络请求
- 密钥状态
- 业务编排

crypto / security:
- 算法
- AndroidKeyStore
- Argon2
- AES-GCM
- X25519/Ed25519
```

## 2.2 建议 OpenSpec 变更提案

可以拆成多个小提案，而不是一个大提案。

推荐顺序：

```text
refactor-android-auth-boundaries
refactor-android-vault-boundaries
refactor-android-security-boundaries
refactor-android-share-boundaries
refactor-android-autofill-boundaries
```

## 2.3 任务清单

### 2.3.1 Auth/Login/Register 边界

```md
- [ ] Inventory LoginActivity/RegisterActivity direct dependencies
- [ ] Introduce or refine AuthViewModel
- [ ] Move login/register orchestration into AuthViewModel or AuthManager
- [ ] Hide TokenManager behind AuthSessionManager
- [ ] Hide BackendService direct access behind manager/use-case boundary
- [ ] Verify login/register/manual logout flow
```

### 2.3.2 Vault 边界

```md
- [ ] Inventory vault UI classes
- [ ] Add VaultViewModel if missing
- [ ] Move vault CRUD orchestration out of Activity/Fragment
- [ ] Hide Room DAO access behind repository/manager
- [ ] Hide crypto calls behind vault service/manager
- [ ] Add sync state model
```

### 2.3.3 Security/Biometric 边界

```md
- [ ] Inventory biometric and key-storage direct calls in UI
- [ ] Define SecurityUnlockManager or BiometricUnlockManager boundary
- [ ] Move biometric enrollment logic out of UI
- [ ] Move lock/unlock state handling into security/session manager
- [ ] Verify master password unlock and biometric unlock
```

### 2.3.4 Share 边界

```md
- [ ] Inventory share-related Activity/Fragment direct dependencies
- [ ] Add ShareViewModel or ShareManager boundary
- [ ] Move share creation/receive/revoke orchestration out of UI
- [ ] Keep QR rendering and scanner handling in UI boundary only
- [ ] Verify user-to-user share flow
```

### 2.3.5 Autofill 边界

```md
- [ ] Inventory AutofillService and autofill UI classes
- [ ] Separate Android Autofill framework boundary from vault query logic
- [ ] Hide decrypted credential access behind a controlled service
- [ ] Verify autofill selection and save flow
```

## 2.4 验收标准

- Activity/Fragment 不直接访问 DAO。
- Activity/Fragment 不直接操作加密算法。
- Activity/Fragment 不直接读写 TokenManager。
- UI 层只负责交互、渲染和系统权限边界。
- ViewModel/Manager 负责业务编排。
- Android 构建和测试通过。
- 每个提案完成后记录手动验证结果。

---

# 阶段 3：认证、密钥与安全生命周期固化

## 3.1 阶段目标

将 SafeVault 的安全核心从“代码中实现了很多安全能力”整理成“有明确生命周期、有测试、有文档的安全模型”。

重点关注：

- 主密码
- PasswordKey
- DataKey
- DeviceKey
- AndroidKeyStore
- 生物识别
- JWT
- Refresh Token
- 多设备
- 本地锁定
- 内存中密钥清理

## 3.2 建议 OpenSpec 变更提案

推荐创建：

```text
stabilize-auth-key-lifecycle
```

也可以继续拆成：

```text
stabilize-master-password-login
stabilize-biometric-unlock
stabilize-device-key-storage
stabilize-token-refresh-and-revoke
```

## 3.3 任务清单

```md
## 1. Key Lifecycle Documentation

- [ ] Define DataKey generation timing
- [ ] Define PasswordKey derivation timing
- [ ] Define DeviceKey creation timing
- [ ] Define biometric enrollment timing
- [ ] Define lock and unlock state transitions

## 2. Android Security

- [ ] Ensure DataKey is only available after unlock
- [ ] Ensure DataKey is cleared on lock
- [ ] Ensure background timeout locks vault
- [ ] Ensure biometric unlock uses AndroidKeyStore-backed material
- [ ] Ensure screenshot protection is enabled for sensitive screens
- [ ] Ensure clipboard auto-clear for copied passwords

## 3. Backend Auth

- [ ] Stabilize login nonce or challenge-response flow
- [ ] Stabilize JWT access token generation
- [ ] Stabilize refresh token rotation
- [ ] Add token revoke/logout verification
- [ ] Add multi-device behavior documentation

## 4. Verification

- [ ] Test first login after registration
- [ ] Test master password unlock
- [ ] Test biometric enrollment
- [ ] Test biometric unlock after app restart
- [ ] Test lock after background timeout
- [ ] Test logout and token revoke
```

## 3.4 验收标准

- 用户注册后不会出现密钥初始化不完整的问题。
- 生物识别启用前必须已经有可用的本地密钥材料。
- App 锁定后不能读取明文密码。
- 后台超时后需要重新解锁。
- Refresh Token 可以轮换或撤销。
- 认证与密钥流程有完整文档。

---

# 阶段 4：密码分享与协作能力完善

## 4.1 阶段目标

在核心密码库稳定之后，再完善密码分享能力。  
这部分可以作为 SafeVault 的高级亮点，但不应早于核心 Vault 闭环。

重点功能：

- 用户搜索
- 好友请求
- 公钥查询
- X25519 密钥交换
- Ed25519 签名校验
- 分享密文生成
- 分享接收
- 分享撤销
- 分享过期
- WebSocket 通知

## 4.2 建议 OpenSpec 变更提案

推荐拆成：

```text
stabilize-contact-friend-flow
stabilize-password-share-protocol
stabilize-share-notification-flow
stabilize-share-history-and-revoke
```

## 4.3 任务清单

### 4.3.1 好友/联系人

```md
- [ ] Stabilize user search
- [ ] Stabilize friend request send/accept/reject
- [ ] Add local friend cache behavior
- [ ] Add conflict/error handling
- [ ] Verify friend list sync
```

### 4.3.2 分享协议

```md
- [ ] Define v3 share packet format
- [ ] Define sender and receiver key agreement
- [ ] Define Ed25519 signing scope
- [ ] Define verification failure behavior
- [ ] Define fallback behavior for older key versions
- [ ] Add crypto unit tests
```

### 4.3.3 分享流程

```md
- [ ] Create encrypted share
- [ ] Receive encrypted share
- [ ] Accept and save share
- [ ] Revoke share
- [ ] Expire share
- [ ] List sent and received shares
```

### 4.3.4 WebSocket 通知

```md
- [ ] Verify authenticated WebSocket connection
- [ ] Verify new-share notification
- [ ] Verify reconnect behavior
- [ ] Verify token expiration behavior
- [ ] Verify foreground/background notification behavior
```

## 4.4 验收标准

- 分享数据服务端不可解密。
- 接收方可以验证分享来源。
- 分享可以撤销和过期。
- 分享通知可以实时到达。
- 分享失败时有明确错误提示。
- 分享协议有文档和测试。

---

# 阶段 5：工程化、部署与 CI

## 5.1 阶段目标

让 SafeVault 从“本地能跑”升级为“别人可以按文档运行，CI 可以自动验证，服务器可以稳定部署”。

## 5.2 建议 OpenSpec 变更提案

推荐拆成：

```text
add-android-ci
add-backend-ci
stabilize-docker-compose-dev-env
stabilize-production-deployment-docs
harden-release-build-config
```

## 5.3 任务清单

### 5.3.1 Android CI

```md
- [ ] Add GitHub Actions workflow for Android build
- [ ] Run ./gradlew test
- [ ] Run ./gradlew :app:assembleDebug
- [ ] Cache Gradle dependencies
- [ ] Upload APK artifact for debug build
```

### 5.3.2 Backend CI

```md
- [ ] Add GitHub Actions workflow for backend
- [ ] Run ./mvnw test
- [ ] Cache Maven dependencies
- [ ] Add PostgreSQL/Redis service if integration tests need them
- [ ] Upload test reports if failed
```

### 5.3.3 Docker Compose

```md
- [ ] Ensure docker-compose starts backend dependencies
- [ ] Ensure PostgreSQL init is documented
- [ ] Ensure Redis configuration is documented
- [ ] Add .env.example
- [ ] Add local dev startup guide
```

### 5.3.4 Release 安全配置

```md
- [ ] Move API_BASE_URL into BuildConfig
- [ ] Move WS_URL into BuildConfig
- [ ] Separate debug and release network security config
- [ ] Disable HTTP logging in release
- [ ] Ensure release disallows cleartext HTTP
- [ ] Ensure release does not trust user certificates
```

## 5.4 验收标准

- Push 到 GitHub 后自动跑 Android CI。
- Push 到 GitHub 后自动跑 Backend CI。
- 本地可以一键启动后端依赖。
- Debug/Release 配置明确分离。
- Release 构建不泄露敏感日志。
- README 中有完整本地启动命令。

---

# 阶段 6：项目展示与文档包装

## 6.1 阶段目标

将 SafeVault 包装成一个专业项目，而不只是代码仓库。

最终要让访问仓库的人快速理解：

- 这是什么项目。
- 解决什么问题。
- 架构如何设计。
- 安全模型如何设计。
- 如何本地运行。
- 如何部署。
- 有哪些核心功能。
- 项目亮点是什么。
- 后续路线是什么。

## 6.2 建议 OpenSpec 变更提案

推荐创建：

```text
polish-project-documentation
```

也可以拆成：

```text
rewrite-root-readme
add-architecture-docs
add-security-model-docs
add-demo-and-screenshots
```

## 6.3 根 README 建议结构

```md
# SafeVault

一句话介绍项目。

## Features

## Architecture

## Security Model

## Tech Stack

## Screenshots

## Quick Start

## API Documentation

## Development

## Testing

## Deployment

## Roadmap

## License
```

## 6.4 文档目录建议

```text
docs/
├── README.md
├── architecture/
│   ├── android-architecture.md
│   ├── backend-architecture.md
│   └── system-overview.md
├── security/
│   ├── zero-knowledge-model.md
│   ├── key-lifecycle.md
│   ├── biometric-unlock.md
│   └── share-protocol.md
├── api/
│   ├── auth-api.md
│   ├── vault-api.md
│   └── share-api.md
├── operations/
│   ├── local-development.md
│   ├── docker-compose.md
│   └── server-deployment.md
├── plans/
│   └── safevault-development-roadmap.md
└── screenshots/
```

## 6.5 展示重点

建议最终在 README 和简历中突出这些点：

```text
- Android 原生密码管理器
- 客户端 AES-256-GCM 加密
- Argon2id 主密码派生
- AndroidKeyStore + 生物识别解锁
- 服务端零知识存储
- Spring Boot + PostgreSQL + Redis 后端
- JWT RS256 鉴权
- X25519/Ed25519 分享协议
- WebSocket 实时分享通知
- AutofillService 自动填充
- Docker Compose 部署
- OpenAPI 接口文档
- GitHub Actions 自动化测试
```

---

# 4. 不建议近期优先做的事情

## 4.1 不建议马上拆微服务

当前 SafeVault 更适合继续作为模块化单体推进。

原因：

- 密码管理器的核心复杂度在安全模型和客户端加密，不在服务数量。
- 微服务会引入服务发现、网关、配置中心、链路追踪、分布式事务等额外复杂度。
- 对学生项目和作品集来说，模块化单体更容易展示清晰架构。
- 当前更需要的是核心链路稳定、测试完整、文档清晰。

推荐路线：

```text
第一阶段：模块化单体
第二阶段：Redis、WebSocket、后台任务
第三阶段：Docker Compose 部署
第四阶段：如有必要，再拆通知、审计、邮件等边界服务
```

## 4.2 不建议继续堆太多分享方式

当前项目已经包含用户分享、二维码、附近发现、蓝牙、WebSocket 通知、分享历史等方向。  
近期应优先保证：

```text
密码库主流程 > 分享流程 > 自动填充 > 附近发现/蓝牙
```

附近发现和蓝牙可以作为后续亮点，不应阻塞核心密码库稳定化。

## 4.3 不建议大规模 UI 重写

除非当前 UI 已经严重影响功能，否则不建议现在从 XML View 系统迁移到 Compose 或整体重写 UI。

更推荐：

- 保留当前 UI。
- 逐步抽出 ViewModel。
- 清理 Activity/Fragment 的业务逻辑。
- 等核心功能稳定后，再考虑 UI 视觉升级。

---

# 5. 推荐执行顺序

建议按照以下顺序创建变更提案：

```text
1. stabilize-core-vault-sync-flow
2. refactor-android-vault-boundaries
3. stabilize-auth-key-lifecycle
4. harden-debug-release-security-config
5. add-android-and-backend-ci
6. stabilize-password-share-protocol
7. stabilize-autofill-flow
8. polish-project-documentation
```

如果想更稳，可以先只做前三个：

```text
1. stabilize-core-vault-sync-flow
2. refactor-android-vault-boundaries
3. stabilize-auth-key-lifecycle
```

这三个完成后，项目的核心质量会明显提升。

---

# 6. 建议的近期工作安排

## 第 1 周

```text
- 创建 stabilize-core-vault-sync-flow 提案
- 盘点 Android vault 相关类
- 盘点后端 vault/private-key 相关接口
- 对齐 Android API model 与后端 DTO
- 写出核心流程文档
```

## 第 2 周

```text
- 修复主流程不一致问题
- 跑通注册、登录、创建密码、同步、重新登录解密
- 补充手动验证清单
- 补充关键单元测试
```

## 第 3 周

```text
- 创建 refactor-android-vault-boundaries 提案
- 将 Vault 相关 UI 编排迁移到 ViewModel/Manager
- 收敛 DAO、TokenManager、Crypto 直接访问
```

## 第 4 周

```text
- 创建 stabilize-auth-key-lifecycle 提案
- 固化主密码、DataKey、DeviceKey、生物识别生命周期
- 修复锁定、超时、后台、重新解锁等边界问题
```

## 第 5 周

```text
- 拆分 Debug/Release 配置
- 添加 Android CI
- 添加 Backend CI
- 整理本地开发和 Docker Compose 文档
```

## 第 6 周

```text
- 完善分享协议
- 完善自动填充
- 重写 README
- 添加截图、架构图和项目展示说明
```

---

# 7. 最终交付状态

当以上阶段完成后，SafeVault 应达到以下状态：

```text
功能层面：
- 可以注册、登录、解锁、管理密码、同步密码
- 可以生物识别解锁
- 可以自动填充
- 可以安全分享密码
- 可以收到分享通知

安全层面：
- 服务端不保存明文密码
- 服务端无法解密用户密码库
- 本地密钥生命周期清晰
- Release 构建不允许调试级网络和日志行为

工程层面：
- Android 构建和测试稳定
- 后端构建和测试稳定
- GitHub Actions 自动验证
- Docker Compose 支持本地启动
- 文档完整
- README 适合作品展示

架构层面：
- Android UI 层不直接处理 crypto/data/network 细节
- 后端 controller 不直接访问 repository
- DTO/API 合同清晰
- Flyway 迁移保持 append-only
- OpenSpec 记录每次结构性变更
```

---

# 8. 总结

SafeVault 下一阶段的核心策略是：

> 先稳定主流程，再收敛架构边界；先固化安全生命周期，再扩展高级功能；先做好模块化单体，再考虑更复杂的企业级架构。

推荐立即开始的第一个变更提案：

```text
stabilize-core-vault-sync-flow
```

这个提案完成后，SafeVault 就会从“功能很多但略散的课程项目”升级为“核心链路稳定的密码管理器项目”。  
后续再逐步推进 Android 边界收敛、安全生命周期、分享协议、自动填充、CI 和项目展示。
