## ADDED Requirements

### Requirement: Refresh Token 轮换

系统 SHALL 在每次成功刷新 token 时执行 refresh token 轮换：旧的 refresh token 立即失效，新的 refresh token 返回给客户端。

后端 SHALL 维护 refresh token 世代追踪模型：每个 refresh token 携带 jti（唯一标识），记录所属 token family（由首次签发的 refresh token 的 jti 标识）。轮换时在同一 family 下生成新 token，旧 token 标记为已轮换（rotated）。

#### Scenario: 成功刷新时轮换 refresh token

- **WHEN** 客户端调用 `/auth/refresh` 并提供有效的 refresh token
- **THEN** 后端返回新的 access token 和新的 refresh token（新 jti，同 family）
- **AND** 旧的 refresh token 标记为已轮换（rotated = true）
- **AND** 客户端用新的 refresh token 替换本地存储的旧 token

#### Scenario: 客户端替换 refresh token

- **WHEN** 刷新成功并收到新的 refresh token
- **THEN** 客户端 SHALL 使用 `commit()` 同步写入新的 refresh token
- **AND** 旧的 refresh token 从本地存储中清除

#### Scenario: Refresh token 轮换失败降级

- **WHEN** 刷新成功但客户端未能保存新的 refresh token（如存储异常）
- **THEN** 客户端 SHALL 清除所有本地 token
- **AND** 跳转到登录页面

### Requirement: Refresh Token 重用检测

系统 SHALL 检测 refresh token 的重用行为。当已轮换（rotated）的 refresh token 被重复使用时，撤销该 token family 下所有 refresh token。

后端 SHALL 查询 refresh token 记录：如果提供的 token 已标记为 rotated = true，则判定为重用。

#### Scenario: 检测到 refresh token 重用

- **WHEN** 客户端使用已轮换的 refresh token（rotated = true）尝试刷新
- **THEN** 后端 SHALL 撤销该 token family 下的所有 refresh token
- **AND** 返回 401 错误
- **AND** 客户端收到错误后清除本地 token 并跳转登录页

#### Scenario: 正常的 refresh token 使用

- **WHEN** 客户端使用当前有效且未轮换的 refresh token 刷新
- **THEN** 后端正常处理刷新请求
- **AND** 旧 token 标记为 rotated，新 token 签发
- **AND** 不触发重用检测

### Requirement: Refresh Token 持久化模型

后端 SHALL 使用 RefreshTokenRecord 表追踪 refresh token 生命周期，包含以下字段：
- jti: token 唯一标识
- family: token family 标识（首次签发 token 的 jti）
- userId: 所属用户
- rotated: 是否已被轮换替换
- revoked: 是否已被撤销
- expiresAt: 过期时间
- createdAt: 创建时间

#### Scenario: 签发新 refresh token 时创建记录

- **WHEN** 后端签发新的 refresh token
- **THEN** 创建 RefreshTokenRecord 记录
- **AND** 首次签发时 family = jti，后续轮换时 family 不变

#### Scenario: 轮换时更新旧 token 记录

- **WHEN** refresh token 被新 token 替换
- **THEN** 旧 token 的 RefreshTokenRecord 标记 rotated = true

#### Scenario: 撤销 family 下所有 token

- **WHEN** 检测到 token 重用或用户主动撤销
- **THEN** 所有相同 family 的 RefreshTokenRecord 标记 revoked = true

## MODIFIED Requirements

### Requirement: Refresh Token Persistence

系统 SHALL 正确保存和更新 refresh token。

#### Scenario: Save tokens after login

- **WHEN** 用户成功登录
- **THEN** 系统应同时保存 access token 和 refresh token
- **AND** 使用 SharedPreferences 持久化
- **AND** 使用 commit() 确保同步写入

#### Scenario: Update tokens after refresh

- **WHEN** token 刷新成功（轮换后收到新 refresh token）
- **THEN** 系统应更新存储的 access token 和 refresh token
- **AND** 旧的 refresh token 从本地存储中完全替换
- **AND** 更新用户信息（userId, displayName）
- **AND** 使用 commit() 确保同步写入

### Requirement: Token Refresh Failure Notification

系统 SHALL 在 token 刷新失败时通知用户。

#### Scenario: Refresh token also expired

- **WHEN** 刷新 token API 调用失败（如 refresh token 已过期、已轮换重用、或已撤销）
- **THEN** AuthInterceptor 应清除本地存储的 token
- **AND** 发送 ACTION_TOKEN_EXPIRED 广播
- **AND** 广播包含失败原因信息

#### Scenario: UI receives token expired broadcast

- **WHEN** BaseActivity 接收到 ACTION_TOKEN_EXPIRED 广播
- **THEN** 应显示友好的错误提示
- **AND** 跳转到登录页面
- **AND** 清除所有后台任务
