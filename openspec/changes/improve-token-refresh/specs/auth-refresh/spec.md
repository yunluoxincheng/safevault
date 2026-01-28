# Auth Refresh Capability Specification

## ADDED Requirements

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

- **WHEN** 刷新 token API 调用失败（如 refresh token 也过期）
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

- **WHEN** token 刷新成功
- **THEN** 系统应更新存储的 access token 和 refresh token
- **AND** 更新用户信息（userId, displayName）
- **AND** 使用 commit() 确保同步写入

### Requirement: Token Refresh Endpoint Exclusion

系统 SHALL 对刷新 token 端点进行特殊处理。

#### Scenario: Bypass auth for refresh endpoint

- **WHEN** 请求路径包含 `/auth/refresh`
- **THEN** AuthInterceptor 不应添加 Authorization 头
- **AND** 请求应直接发送到服务器
- **AND** 原始的 refresh token 在 Authorization 头中传递
