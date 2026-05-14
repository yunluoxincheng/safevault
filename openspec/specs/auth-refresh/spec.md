# auth-refresh Specification

## Purpose
TBD - created by archiving change improve-token-refresh. Update Purpose after archive.
## Requirements
### Requirement: Token Expiration Detection

系统 SHALL 能够检测 JWT access token 的过期时间。

#### Scenario: Parse token expiration from JWT

- **WHEN** access token 存在且格式正确
- **THEN** 系统应能解析出过期时间（exp 字段）
- **AND** 返回过期时间的毫秒时间戳

#### Scenario: Handle malformed token

- **WHEN** access token 格式错误或无法解析
- **THEN** 系统应捕获异常并返回 null
- **AND** 记录错误日志

### Requirement: Proactive Token Refresh

系统 SHALL 在应用启动时主动检查 token 是否即将过期。

#### Scenario: Token valid for more than 5 minutes

- **WHEN** 应用启动时 token 剩余有效期 > 5分钟
- **THEN** 系统不执行刷新操作
- **AND** 应用正常启动

#### Scenario: Token expires within 5 minutes

- **WHEN** 应用启动时 token 剩余有效期 < 5分钟
- **THEN** 系统应主动调用刷新 API
- **AND** 刷新成功后保存新 token
- **AND** 刷新失败时记录日志但不阻止应用启动

#### Scenario: Token already expired

- **WHEN** 应用启动时 token 已经过期
- **THEN** 系统应跳过主动刷新
- **AND** 等待下一次 API 调用时触发被动刷新

### Requirement: Authentication Error Propagation

系统 SHALL 正确传播认证错误到 UI 层。

#### Scenario: 401 response from API

- **WHEN** API 调用返回 401 状态码
- **THEN** AuthInterceptor 应尝试刷新 token
- **AND** 刷新成功后重试原请求
- **AND** 刷新失败后抛出 TokenExpiredException

#### Scenario: Contact sync authentication failure

- **WHEN** ContactSyncManager 遇到认证错误
- **THEN** 不应吞掉错误或返回空列表
- **AND** 应让错误通过 Observable.error() 传播
- **AND** UI 层能接收到错误并处理

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

### Requirement: Concurrent Refresh Handling

系统 SHALL 正确处理多个并发请求同时遇到 token 过期的情况。

#### Scenario: Multiple requests receive 401 simultaneously

- **WHEN** 多个 API 请求同时返回 401
- **THEN** 只应执行一次 token 刷新操作
- **AND** 其他请求应等待刷新完成
- **AND** 刷新成功后所有请求使用新 token 重试

#### Scenario: Refresh in progress timeout

- **WHEN** 等待 token 刷新超过 5 秒
- **THEN** 等待中的请求应返回超时错误
- **AND** 不应无限期阻塞

#### Scenario: Refresh fails during concurrent wait

- **WHEN** token 刷新失败且有其他请求在等待
- **THEN** 所有等待的请求应收到认证失败错误
- **AND** 系统应发送 token 过期广播

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

### Requirement: Token Refresh Endpoint Exclusion

系统 SHALL 对刷新 token 端点进行特殊处理。

#### Scenario: Bypass auth for refresh endpoint

- **WHEN** 请求路径包含 `/auth/refresh`
- **THEN** AuthInterceptor 不应添加 Authorization 头
- **AND** 请求应直接发送到服务器
- **AND** 原始的 refresh token 在 Authorization 头中传递

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

