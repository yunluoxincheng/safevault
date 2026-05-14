## Why

SafeVault 已具备完整的三层密钥架构（PasswordKey/DataKey/DeviceKey）、生物识别解锁、会话锁定和 token 刷新能力，但各组件的生命周期时序和状态转换缺少统一文档和端到端验证。注册后密钥初始化的完整时序（包括直接到达 UNLOCKED 状态的实际行为）未被文档化、生物识别 enrollment 与 unlock 的前置条件未区分、后台超时后重新解锁行为不一致、后端 refresh token 缺少轮换和盗用检测机制。需要在添加更多高级功能之前，将认证与密钥的生命周期从"代码实现了很多能力"固化为"有明确文档、测试和状态机的安全模型"。

## What Changes

- 定义完整的密钥生命周期时序：PasswordKey 派生、DataKey 生成、DeviceKey（AES-256 SecretKey）创建的触发条件和先后顺序。
- 定义会话状态机：Uninitialized → Registered → LoggedIn → Unlocked → Locked → LoggedOut 的完整状态和转换条件，反映注册流程直达 UNLOCKED 的实际行为。
- 区分生物识别 ENROLLMENT 和 UNLOCK 前置条件：ENROLLMENT 只要求主密码路径完整，UNLOCK 要求 DeviceKey 路径完整。
- 固化后台超时锁定与恢复：统一 SessionGuard 超时检查、ApplicationLifecycleWatcher 和 Activity 级锁定行为。
- 固化 token 生命周期：JWT access token 过期刷新、refresh token 轮换与撤销（引入 token family 追踪模型）、重用检测。
- 补充端到端验证清单：注册→密钥初始化→登录→解锁→同步→锁定→恢复→登出的完整手动验证流程。
- 补充关键路径单元测试。

## Capabilities

### New Capabilities
- `key-lifecycle`: 密钥生成时序、存储层级、轮换规则和销毁流程的规范定义，覆盖 PasswordKey、DataKey、DeviceKey（AES-256）和生物识别密钥材料。区分生物识别 ENROLLMENT 和 UNLOCK 的不同前置条件。
- `session-state-machine`: 会话状态转换规范，定义 Uninitialized/Registered/LoggedIn/Unlocked/Locked/LoggedOut 各状态间的合法转换和触发条件。注册流程直达 UNLOCKED。

### Modified Capabilities
- `auth-refresh`: 补充 refresh token 轮换（token family + jti 追踪模型）、重用检测、显式撤销的行为规范。更新现有 Refresh Token Persistence 和 Token Refresh Failure Notification 要求。

## Impact

- Android `security/` 包：SecureKeyStorageManager、SessionGuard、SecurityManager、BiometricAuthManager、ApplicationLifecycleWatcher 的生命周期逻辑可能需要调整。
- Android `service/manager/`：AuthSessionManager、CloudAuthManager 的认证编排可能需要适配新状态机。
- Android `ui/`：LoginActivity、MainActivity 等的锁定/解锁流程可能需要适配统一状态转换。
- 后端 `entity/`：新增 RefreshTokenRecord 实体，需要 Flyway 迁移。
- 后端 `security/`：JwtTokenProvider 需要在 refresh token 中携带 jti 和 family claim。
- 后端 `service/`：AuthService 需要实现轮换逻辑和重用检测；TokenRevokeService 需要适配 family 级撤销。
- 后端 `repository/`：新增 RefreshTokenRecordRepository。
- 现有 `auth-refresh` spec 需要更新以包含 refresh token 轮换和持久化模型规范。
