## Context

SafeVault 已实现三层密钥存储架构（SecureKeyStorageManager）、会话管理（SessionGuard）、生物识别（BiometricAuthManager）和 token 刷新（TokenManager + AuthInterceptor）。这些组件各自工作，但缺少统一的跨组件生命周期编排：

- 注册时密钥初始化的完整时序未被文档化，依赖隐式调用顺序。
- SessionGuard 的 DataKey 解锁依赖 SecureKeyStorageManager 的解密成功，但两者的错误传播路径不完全对齐。
- 生物识别 enrollment 和 unlock 的前置条件未在文档中区分：enrollment 只需要主密码路径完整，unlock 才需要 DeviceKey 路径完整。
- 后台超时锁定由 SessionGuard、ApplicationLifecycleWatcher 和 Activity 各自处理，存在重复或遗漏。
- 后端 refresh token 目前是长期有效的，没有轮换机制，也没有 token family 追踪模型。
- 注册流程的实际落点是 UNLOCKED（代码中注册后直接调用 sessionGuard.unlockWithDataKey()），而非 REGISTERED。

## Goals / Non-Goals

**Goals:**

- 定义并文档化密钥生命周期时序图：注册、登录、解锁、锁定、登出各阶段哪些密钥存在、何时生成、何时销毁。
- 定义会话状态机：6 个核心状态和它们之间的合法转换，反映实际代码行为（注册流程直达 UNLOCKED）。
- 固化生物识别前置条件：区分 ENROLLMENT（只需主密码路径完整）和 UNLOCK（需 DeviceKey 路径完整）。
- 统一后台超时锁定行为：SessionGuard 是唯一锁定检查入口，Activity 只负责调用。
- 后端增加 refresh token 轮换和 token family 追踪模型。
- 提供端到端验证清单和关键路径测试。

**Non-Goals:**

- 不改变三层密钥架构的加密算法或存储方式（DeviceKey 保持 AES-256 SecretKey）。
- 不将 DeviceKey 从 AES 迁移为 RSA（这不改变安全等级，且引入不必要的迁移风险）。
- 不引入新的 DI 框架（Hilt/Dagger）。
- 不做大规模 UI 重写。
- 不引入多设备密钥同步机制（仅处理 token 层面的多设备失效）。
- 不改变后端 JWT 签名算法或密钥。

## Decisions

### Decision 1: 会话状态机定义

定义 6 个状态：

```
UNINITIALIZED ──→ UNLOCKED (注册流程直达)
     │
     ↓ (异常路径)
 REGISTERED ──→ LOGGED_IN ──→ UNLOCKED ──→ LOCKED ──→ LOGGED_OUT
                                ↑              │
                                └──────────────┘ (重新解锁)
```

| 状态 | 含义 | 可访问的数据 |
|------|------|-------------|
| UNINITIALIZED | 未注册，无本地数据 | 无 |
| REGISTERED | 本地有加密密钥材料但无有效 JWT | EncryptedDataKey (PasswordKey 加密) |
| LOGGED_IN | 已登录，有有效 JWT 但 DataKey 未加载 | EncryptedDataKey + JWT |
| UNLOCKED | DataKey 在内存中 | 所有加密数据可解密 |
| LOCKED | DataKey 已从内存清除 | EncryptedDataKey 仍在，但需重新解锁 |
| LOGGED_OUT | 已登出，凭证已清除 | 无有效 JWT |

**关键修正**: 正常注册流程从 UNINITIALIZED 直接到达 UNLOCKED，因为代码中注册后立即调用 sessionGuard.unlockWithDataKey() 并保存 JWT。REGISTERED 状态仅作为异常路径存在（如本地密钥已初始化但 JWT 获取失败）。

**Alternative considered**: 使用更细粒度的状态（如 BIOMETRIC_ENROLLED），但增加的复杂度大于收益，生物识别启用作为 UNLOCKED 状态的子条件即可。

### Decision 2: 密钥生成时序

注册流程中的密钥生成按以下顺序（反映当前代码行为）：

1. 用户输入主密码 → Argon2id 派生 PasswordKey（不存储，仅临时使用）
2. 生成随机 DataKey（256-bit AES key）
3. 用 PasswordKey 加密 DataKey → 存储 PasswordKeyEncryptedDataKey
4. 在 AndroidKeyStore 中生成 DeviceKey（AES-256 SecretKey，绑定用户认证，30 秒有效期）
5. 用 DeviceKey（AES-GCM）加密 DataKey → 存储 DeviceKeyEncryptedDataKey
6. 用 DataKey 加密 RSA 私钥 → 存储 EncryptedRSAPrivateKey（RSA 密钥对用于密码分享，非用于 DataKey 加密）
7. 清除内存中的 PasswordKey 明文
8. 调用 sessionGuard.unlockWithDataKey(dataKey) → 进入 UNLOCKED 状态
9. 保存后端返回的 JWT 和 refresh token

**关键修正**: DeviceKey 是 AES-256 SecretKey 而非 RSA keypair。RSA keypair 在当前架构中用于密码分享（X25519/Ed25519），不参与 DataKey 加密链路。

### Decision 3: 生物识别 ENROLLMENT vs UNLOCK 前置条件

**ENROLLMENT 前置条件**（首次启用生物识别）：
- 仅要求 PasswordKeyEncryptedDataKey 存在（可通过主密码路径取得 DataKey）
- 不要求 DeviceKey 已存在，因为 enrollment 会生成新 DeviceKey 并重新加密 DataKey

**UNLOCK 前置条件**（生物识别解锁）：
- DeviceKey 存在于 AndroidKeyStore 且未失效
- DeviceKeyEncryptedDataKey 存在于本地存储

**Rationale**: 当前代码 BiometricAuthManager 已区分 canAuthenticateForEnrollment() 和 canAuthenticateForUnlock()，enrollment 只检查主密码路径。如果 enrollment 要求 DeviceKey 已存在，就变成了"启用生物识别之前必须已经启用过生物识别"的逻辑悖论。

### Decision 4: 统一锁定检查入口

SessionGuard 是唯一的锁定状态管理者。Activity/Application 层只负责：
- `onPause()` 时记录后台时间戳
- `onResume()` 时调用 `SessionGuard.shouldLockBySessionTimeout(backgroundTime)` 检查
- 如果需要锁定，调用 `SessionGuard.lock()`

ApplicationLifecycleWatcher 仅在系统内存压力时清除 DataKey，不再处理业务级别的后台超时。

**Rationale**: 消除 SessionGuard、ApplicationLifecycleWatcher 和 Activity 之间的锁定逻辑重复。

### Decision 5: Refresh Token 轮换与 Token Family 追踪

后端 refresh token 策略改为：

1. 引入 RefreshTokenRecord 表追踪 token 生命周期：
   - jti: token 唯一标识
   - family: token family 标识（首次签发 token 的 jti）
   - userId: 所属用户
   - rotated: 是否已被轮换替换
   - revoked: 是否已被撤销
   - expiresAt: 过期时间

2. 每次调用 `/auth/refresh` 后：
   - 旧 refresh token 标记 rotated = true
   - 签发新 refresh token（同 family，新 jti）
   - 返回新 access token 和新 refresh token

3. 重用检测：如果 rotated = true 的 token 被再次使用：
   - 撤销该 family 下所有 refresh token
   - 返回 401

**Alternative considered**:
- 不做轮换，仅依赖长期 refresh token → 风险是无法检测 token 盗用。
- 只用 revoke denylist（当前方案）→ 无法区分"已轮换的正常 token"和"被重用的可疑 token"，因为 revoke 和 rotate 是不同的语义。

## Risks / Trade-offs

- **Risk**: 状态机引入可能需要重构现有 Activity 的锁定/解锁逻辑 → Mitigation: 状态机定义是规范性的，不强制立即重写所有 Activity，通过 tasks 按优先级逐步对齐。
- **Risk**: Refresh token 轮换增加客户端复杂度，需要处理轮换失败的降级 → Mitigation: 轮换失败时清除本地 token 并跳转登录页，与当前行为一致。
- **Risk**: RefreshTokenRecord 表引入新的数据库迁移 → Mitigation: 使用 Flyway append-only 迁移，不修改现有表结构。
- **Risk**: 密钥时序文档可能与实际代码不一致 → Mitigation: 文档化后通过 tasks 中添加验证步骤确认代码与文档对齐。
- **Trade-off**: 不做多设备密钥同步意味着用户在新设备上需要重新注册或恢复备份，这是当前阶段的可接受限制。
- **Trade-off**: DeviceKey 保持 AES 而非迁移为 RSA → 避免不必要的算法迁移风险，AES-GCM 在 AndroidKeyStore 中的硬件支持更广泛，且当前方案安全性足够。
