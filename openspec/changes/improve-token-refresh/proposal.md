# Change: Improve Token Refresh Mechanism

**Status**: ✅ APPLIED (2026-01-29)

## Why

当前应用的 token 刷新机制存在以下问题，导致用户体验不佳：

1. **被动刷新**：只在收到 401 响应时才刷新 token，用户静默过期后第一次操作会失败
2. **错误被吞掉**：ContactSyncManager 在网络失败时返回空列表，用户无法感知 token 已过期
3. **刷新失败无通知**：token 刷新失败后只清除 token，UI 没有任何提示
4. **并发机制不够健壮**：使用 while 循环等待刷新完成，可能导致请求阻塞

后端 token 配置：
- Access Token: 24小时过期
- Refresh Token: 30天过期

## What Changes

本次改动将分步实施以下改进：

### 方案 A：修复错误传播（优先级：高）
- 移除 ContactSyncManager 中的 `onErrorReturnItem(new ArrayList<>())`
- 让 token 过期等认证错误正确传播到调用者
- 添加统一的认证错误处理机制

### 方案 B：添加刷新失败广播（优先级：高）
- 在 AuthInterceptor 刷新失败时发送 `ACTION_TOKEN_EXPIRED` 广播
- 在 BaseActivity 中监听广播并跳转到登录页
- 显示用户友好的提示信息

### 方案 C：主动刷新机制（优先级：中）
- 在应用启动时检查 token 剩余有效期
- 如果剩余时间少于 5 分钟，主动刷新 token
- 避免用户在操作时才发现 token 已过期

### 方案 D：改进并发刷新（优先级：低）
- 使用 AtomicReference + CountDownLatch 改进同步刷新逻辑
- 添加等待队列机制，避免多个请求同时触发刷新
- 优化并发场景下的性能

## Impact

- 受影响规范：新增 `auth-refresh` 能力规范
- 受影响代码：
  - `network/AuthInterceptor.java` - 刷新逻辑和广播发送
  - `service/ContactSyncManager.java` - 错误传播
  - `ui/MainActivity.java` - 广播监听和登录跳转
  - `network/TokenManager.java` - 主动刷新和过期检查
  - 新增 `auth/AuthReceiver.java` - 广播接收器

## Implementation Summary

### 方案 A：修复错误传播 ✅
- 已完成（之前实现）
- 移除了 `ContactSyncManager` 中的错误吞没逻辑
- 添加了 `TokenExpiredException` 自定义异常
- 认证错误正确向上传播

### 方案 B：添加刷新失败广播 ✅
- `AuthInterceptor` 中定义了 `ACTION_TOKEN_EXPIRED` 常量
- 刷新失败时发送广播
- `AuthReceiver` 接收器处理广播，清除 token 并跳转登录
- `BaseActivity` 中注册接收器
- `AndroidManifest.xml` 中注册广播

### 方案 C：主动刷新机制 ✅
**新增方法（TokenManager.java）：**
- `parseTokenExpiryTime()` - JWT 解析获取过期时间
- `shouldRefreshToken()` - 判断是否需要刷新（包括已过期情况）
- `refreshIfNearExpiry()` - 主动刷新，失败时不清除 token
- 修改了 `saveTokens()` 和 `clearTokens()` 以处理过期时间缓存

**新增调用（MainActivity.java）：**
- `checkAndRefreshTokenIfNeeded()` - 应用启动时检查并刷新
- 在 `initCloudServices()` 中调用

**额外修复：**
- 修改了判断逻辑，移除 `&& timeUntilExpiry > 0` 条件
- 现在即使 token 已过期也会触发主动刷新（只要 refresh token 有效）

### 方案 D：改进并发刷新 ✅
**新增机制（AuthInterceptor.java）：**
- `RefreshState` 枚举（IDLE/PENDING/SUCCESS/FAILED）
- `AtomicReference<RefreshState>` 原子状态管理
- `Queue<Request>` 请求队列
- `RefreshResult` 内部类封装刷新结果

**改进方法：**
- `refreshTokenSync()` - 使用 `compareAndSet()` 避免并发刷新
- `waitForRefreshCompletion()` - 改进的等待逻辑
- `processPendingRequests()` - 处理队列中的请求
- 添加超时保护（10 秒）

### 待测试项
- [ ] 完整测试 token 过期流程
- [ ] 测试 refresh token 也过期的情况
- [ ] 测试并发场景下的刷新逻辑
- [ ] 验证用户体验：错误提示清晰、登录流程顺畅

### 注意事项
**用户必须退出并重新登录**才能使用新的 token 刷新机制，因为：
- 旧 token 可能没有过期时间缓存
- 已过期太久的 token 需要重新获取
