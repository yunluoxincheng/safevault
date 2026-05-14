## ADDED Requirements

### Requirement: PasswordKey 派生时序

系统 SHALL 在以下时机通过 Argon2id 从主密码派生 PasswordKey：
- 用户注册时
- 用户通过主密码解锁时

PasswordKey 明文 SHALL 仅在内存中短暂存在，完成 DataKey 加密/解密后立即安全清除。

#### Scenario: 注册时派生 PasswordKey

- **WHEN** 用户完成注册表单并提交主密码
- **THEN** 系统使用 Argon2id 从主密码派生 PasswordKey
- **AND** 使用 PasswordKey 加密新生成的 DataKey
- **AND** PasswordKey 明文在加密完成后安全清除

#### Scenario: 主密码解锁时派生 PasswordKey

- **WHEN** 用户输入主密码尝试解锁
- **THEN** 系统使用 Argon2id 从主密码派生 PasswordKey
- **AND** 使用 PasswordKey 解密 DataKey
- **AND** PasswordKey 明文在解密完成后安全清除

#### Scenario: PasswordKey 派生失败

- **WHEN** Argon2id 派生过程因内存不足或其他原因失败
- **THEN** 系统返回明确的错误信息
- **AND** 不留存任何部分派生的密钥材料

### Requirement: DataKey 生成时序

系统 SHALL 在用户首次注册时生成随机 256-bit DataKey。DataKey 是加密密码库条目的主密钥。

DataKey 明文 SHALL 仅在会话解锁态（SessionGuard.unlocked == true）时存在于内存中。

#### Scenario: 注册时生成 DataKey

- **WHEN** 用户首次完成注册
- **THEN** 系统生成随机 256-bit AES DataKey
- **AND** 使用 PasswordKey 加密后持久化存储
- **AND** 使用 DeviceKey 加密后持久化存储
- **AND** DataKey 明文在加密完成后存入 SessionGuard 内存

#### Scenario: DataKey 不重复生成

- **WHEN** 已注册用户登录或解锁
- **THEN** 系统 SHALL NOT 生成新的 DataKey
- **AND** 从加密存储中解密恢复现有 DataKey

### Requirement: DeviceKey 创建时序

系统 SHALL 在生物识别 enrollment 时在 AndroidKeyStore 中创建 AES-256 DeviceKey（SecretKey）。

DeviceKey SHALL 绑定用户认证（setUserAuthenticationRequired = true），认证有效期 30 秒，使用 AES/GCM/NoPadding 模式。启用生物识别时系统生成新 DeviceKey 并用它重新加密 DataKey。

#### Scenario: 生物识别 enrollment 时创建 DeviceKey

- **WHEN** 用户首次启用生物识别（enrollment）
- **THEN** 系统在 AndroidKeyStore 中生成 AES-256 SecretKey 作为 DeviceKey
- **AND** DeviceKey 设置 setUserAuthenticationRequired(true)、30 秒认证有效期
- **AND** 使用 DeviceKey 通过 AES-GCM 加密 DataKey 并持久化存储

#### Scenario: DeviceKey 已存在

- **WHEN** AndroidKeyStore 中已存在 DeviceKey 别名
- **THEN** 系统 SHALL 返回现有 DeviceKey，不创建新密钥
- **AND** 使用现有 DeviceKey 解密 DataKey

#### Scenario: DeviceKey 失效

- **WHEN** AndroidKeyStore 报告 KeyPermanentlyInvalidatedException（如新增指纹后）
- **THEN** 系统 SHALL 标记生物识别解锁不可用
- **AND** 用户仍可通过主密码解锁
- **AND** 下次 enrollment 时重新生成 DeviceKey

### Requirement: 生物识别前置条件

系统 SHALL 区分生物识别 ENROLLMENT 和 UNLOCK 两种场景，使用不同的前置条件。

**ENROLLMENT 前置条件**（首次启用生物识别）：
- PasswordKeyEncryptedDataKey 存在于本地存储（可通过主密码路径取得 DataKey）

ENROLLMENT 不要求 DeviceKey 已存在或 DeviceKeyEncryptedDataKey 已存在，因为 enrollment 过程会生成新 DeviceKey 并重新加密 DataKey。

**UNLOCK 前置条件**（生物识别解锁）：
- DeviceKey 存在于 AndroidKeyStore 且未失效
- DeviceKeyEncryptedDataKey 存在于本地存储

#### Scenario: ENROLLMENT — PasswordKey 路径完整

- **WHEN** 用户请求启用生物识别且 PasswordKeyEncryptedDataKey 存在
- **THEN** 系统 SHALL 生成新 DeviceKey（或获取已有的）
- **AND** 使用 DeviceKey 加密 DataKey 并存储 DeviceKeyEncryptedDataKey
- **AND** 标记生物识别已启用

#### Scenario: ENROLLMENT — PasswordKey 路径不完整

- **WHEN** PasswordKeyEncryptedDataKey 不存在
- **THEN** 系统 SHALL 拒绝启用生物识别
- **AND** 显示错误提示

#### Scenario: UNLOCK — DeviceKey 有效

- **WHEN** DeviceKey 存在且 DeviceKeyEncryptedDataKey 存在
- **AND** 用户通过生物识别验证
- **THEN** 系统 SHALL 使用 DeviceKey 解密 DataKey 并解锁会话

#### Scenario: UNLOCK — DeviceKey 失效

- **WHEN** DeviceKey 抛出 KeyPermanentlyInvalidatedException
- **THEN** 系统 SHALL 禁用生物识别解锁选项
- **AND** 提示用户通过主密码解锁后重新启用生物识别

### Requirement: 密钥销毁规则

系统 SHALL 在以下时机安全销毁密钥材料：

| 密钥 | 销毁时机 | 销毁方式 |
|------|---------|---------|
| PasswordKey 明文 | DataKey 加密/解密完成后 | 内存零填充 |
| DataKey 明文 | 会话锁定、登出、内存压力 | SensitiveData.close() |
| DeviceKey | 用户主动撤销或 enrollment 重新生成 | AndroidKeyStore delete |
| JWT/Refresh Token | 登出 | 本地清除 + 服务端撤销 |

#### Scenario: 会话锁定时清除 DataKey

- **WHEN** SessionGuard.lock() 被调用
- **THEN** DataKey 明文通过 SensitiveData.close() 安全清零
- **AND** SessionGuard 转为锁定态
- **AND** 后续任何需要 DataKey 的操作都返回 SessionLockedException

#### Scenario: 登出时清除所有凭证

- **WHEN** 用户主动登出
- **THEN** SessionGuard.lock() 清除 DataKey
- **AND** 本地存储的 JWT 和 refresh token 被清除
- **AND** 后端 refresh token 被撤销
- **AND** PasswordKeyEncryptedDataKey 保留（允许重新登录）
