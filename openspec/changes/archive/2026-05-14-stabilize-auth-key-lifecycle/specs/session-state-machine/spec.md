## ADDED Requirements

### Requirement: 会话状态定义

系统 SHALL 维护以下 6 个会话状态：

| 状态 | 描述 | DataKey 在内存 | JWT 有效 |
|------|------|---------------|---------|
| UNINITIALIZED | 未注册，无任何本地数据 | 否 | 否 |
| REGISTERED | 已注册，本地有加密密钥材料但未持有有效 JWT | 否 | 否 |
| LOGGED_IN | 已登录，有有效 JWT 但 DataKey 未加载 | 否 | 是 |
| UNLOCKED | DataKey 已加载到内存 | 是 | 是 |
| LOCKED | DataKey 已从内存清除 | 否 | 是（可能） |
| LOGGED_OUT | 已登出，凭证已清除 | 否 | 否 |

系统 SHALL 确保任意时刻处于且仅处于一个状态。

#### Scenario: 应用首次安装启动

- **WHEN** 应用首次安装后启动
- **THEN** 会话状态为 UNINITIALIZED
- **AND** 本地无加密密钥材料、无 JWT

#### Scenario: 注册成功后直接进入解锁态

- **WHEN** 用户成功完成注册流程
- **THEN** 注册流程中本地密钥初始化完成（PasswordKeyEncryptedDataKey 等已存储）
- **AND** 后端返回 JWT 和 refresh token 并保存到本地
- **AND** SessionGuard.unlockWithDataKey() 被调用
- **AND** 会话状态直接转为 UNLOCKED

#### Scenario: 仅本地密钥存在但未登录（如 token 过期后）

- **WHEN** 本地存在 PasswordKeyEncryptedDataKey 但无有效 JWT
- **THEN** 会话状态为 REGISTERED
- **AND** 用户需要重新登录获取 JWT

#### Scenario: 登录成功后

- **WHEN** 用户成功登录（获取到 JWT）
- **THEN** 会话状态转为 LOGGED_IN
- **AND** JWT 已保存到本地

### Requirement: 状态转换规则

系统 SHALL 仅允许以下状态转换：

| 从 | 到 | 触发条件 |
|----|----|---------|
| UNINITIALIZED | UNLOCKED | 注册成功（注册流程内完成密钥初始化 + JWT 获取 + DataKey 加载） |
| UNINITIALIZED | REGISTERED | 仅本地密钥初始化完成但未获取到 JWT（异常路径） |
| REGISTERED | LOGGED_IN | 登录成功获取 JWT |
| REGISTERED | UNLOCKED | 登录成功 + 主密码验证成功，DataKey 加载（快速路径） |
| LOGGED_IN | UNLOCKED | 主密码验证成功，DataKey 加载到内存 |
| LOGGED_IN | LOGGED_OUT | 登出或 token 彻底失效 |
| UNLOCKED | LOCKED | 后台超时、手动锁定、内存压力 |
| UNLOCKED | LOGGED_OUT | 主动登出 |
| LOCKED | UNLOCKED | 主密码或生物识别重新解锁 |
| LOCKED | LOGGED_OUT | JWT 过期且刷新失败，或主动登出 |
| LOGGED_OUT | LOGGED_IN | 重新登录 |
| LOGGED_OUT | UNLOCKED | 重新登录 + 主密码验证（快速路径） |

系统 SHALL 拒绝任何未在上述表中定义的状态转换。

#### Scenario: 正常注册流程的状态转换

- **WHEN** 用户完成注册
- **THEN** 状态从 UNINITIALIZED 直接转为 UNLOCKED
- **AND** 经过中间过程：密钥初始化 → JWT 获取 → DataKey 加载

#### Scenario: 正常登录解锁流程的状态转换

- **WHEN** 已注册用户完成 登录 → 主密码解锁 → 后台超时锁定 → 生物识别解锁 → 登出
- **THEN** 状态依次为 REGISTERED → LOGGED_IN → UNLOCKED → LOCKED → UNLOCKED → LOGGED_OUT

#### Scenario: 非法状态转换被拒绝

- **WHEN** 系统处于 LOCKED 状态时尝试直接跳到 LOGGED_IN
- **THEN** 系统 SHALL 不执行此转换
- **AND** 保持在 LOCKED 状态

### Requirement: 解锁条件检查

系统 SHALL 在执行 UNLOCKED 转换前验证以下条件：
- 对于主密码解锁：PasswordKey 能成功解密 DataKey
- 对于生物识别解锁：DeviceKey 能成功解密 DataKey 且通过生物识别验证

#### Scenario: 主密码解锁成功

- **WHEN** 用户输入正确的主密码
- **THEN** 系统使用 Argon2id 派生 PasswordKey
- **AND** 使用 PasswordKey 解密 DataKey
- **AND** DataKey 存入 SessionGuard
- **AND** 状态转为 UNLOCKED

#### Scenario: 主密码解锁失败

- **WHEN** 用户输入错误的主密码
- **THEN** PasswordKey 解密 DataKey 失败（AES-GCM 认证失败）
- **AND** 状态保持 LOCKED 或 LOGGED_IN
- **AND** 显示错误提示

#### Scenario: 生物识别解锁成功

- **WHEN** 生物识别已启用且 DeviceKey 有效
- **AND** 用户通过生物识别验证
- **THEN** 系统使用 DeviceKey（AES SecretKey）解密 DataKey
- **AND** DataKey 存入 SessionGuard
- **AND** 状态转为 UNLOCKED

#### Scenario: 生物识别解锁失败

- **WHEN** DeviceKey 已失效（KeyPermanentlyInvalidatedException）
- **THEN** 系统禁用生物识别解锁选项
- **AND** 提示用户使用主密码解锁
- **AND** 状态保持 LOCKED

### Requirement: 锁定触发条件

系统 SHALL 在以下任一条件满足时从 UNLOCKED 转为 LOCKED：
- 应用进入后台超过配置的超时时间（由 SessionGuard.shouldLockBySessionTimeout 判定）
- 用户手动点击锁定
- 系统内存压力触发 ApplicationLifecycleWatcher 清除 DataKey

#### Scenario: 后台超时触发锁定

- **WHEN** 应用进入后台，且在后台时间超过配置的超时阈值
- **AND** 应用回到前台时 SessionGuard.shouldLockBySessionTimeout 返回 true
- **THEN** 状态转为 LOCKED
- **AND** DataKey 从内存清除
- **AND** UI 显示解锁界面

#### Scenario: 立即锁定模式

- **WHEN** 用户配置了立即锁定模式（超时为 0）
- **AND** 应用进入后台后回到前台
- **THEN** 状态立即转为 LOCKED

#### Scenario: 从不锁定模式

- **WHEN** 用户配置了从不锁定模式
- **AND** 应用进入后台后回到前台
- **THEN** 状态保持 UNLOCKED
- **AND** DataKey 仍在内存中

### Requirement: 登出清理

系统 SHALL 在转为 LOGGED_OUT 状态时执行以下清理：
- 清除 SessionGuard 中的 DataKey
- 清除本地 JWT 和 refresh token
- 调用后端 refresh token 撤销接口
- 保留 PasswordKeyEncryptedDataKey 和 DeviceKeyEncryptedDataKey（允许重新登录）

#### Scenario: 主动登出

- **WHEN** 用户点击登出
- **THEN** 状态转为 LOGGED_OUT
- **AND** 所有清理步骤依次执行
- **AND** UI 跳转到登录页面

#### Scenario: 后端 token 撤销失败

- **WHEN** 用户登出时后端 refresh token 撤销请求失败
- **THEN** 本地 token 仍被清除
- **AND** 记录失败日志
- **AND** 不阻止用户登出流程
