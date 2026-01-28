# Implementation Tasks

## 1. 方案 A：修复错误传播

- [x] 1.1 移除 ContactSyncManager.getCloudFriends() 中的 `onErrorReturnItem(new ArrayList<>())`
- [x] 1.2 在 ContactSyncManager 添加统一的错误处理逻辑，区分认证错误和其他错误
- [x] 1.3 添加 TokenExpiredException 自定义异常类
- [x] 1.4 修改 syncContacts() 方法，让认证错误向上传播
- [x] 1.5 更新调用 ContactSyncManager 的 UI 代码，添加错误处理
- [ ] 1.6 测试：模拟 token 过期场景，验证错误正确传播

## 2. 方案 B：添加刷新失败广播

- [x] 2.1 在 AuthInterceptor 中定义 ACTION_TOKEN_EXPIRED 常量
- [x] 2.2 修改 refreshTokenSync() 方法，刷新失败时发送广播
- [x] 2.3 创建 AuthReceiver 广播接收器类
- [x] 2.4 在 BaseActivity 的 onCreate() 中注册 AuthReceiver
- [x] 2.5 实现接收器逻辑：清除 token 并跳转到登录页
- [x] 2.6 在 AndroidManifest.xml 中注册 ACTION_TOKEN_EXPIRED 广播
- [ ] 2.7 测试：模拟刷新失败，验证应用跳转到登录页

## 3. 方案 C：主动刷新机制

- [x] 3.1 在 TokenManager 添加 JWT 解析方法，获取 token 过期时间
- [x] 3.2 添加 shouldRefreshToken() 方法，判断是否需要刷新（剩余时间 < 5分钟）
- [x] 3.3 在 MainActivity 的 initCloudServices() 中调用 token 检查逻辑
- [x] 3.4 添加 refreshIfNearExpiry() 方法，尝试刷新 token
- [x] 3.5 处理刷新失败场景（显示提示但不跳转）
- [ ] 3.6 测试：修改后端 token 过期时间为 1 分钟，验证主动刷新生效

## 4. 方案 D：改进并发刷新

- [x] 4.1 在 AuthInterceptor 中添加等待队列（Queue<Request>）
- [x] 4.2 使用 AtomicReference<RefreshState> 保存刷新状态（IDLE/PENDING/SUCCESS/FAILED）
- [x] 4.3 修改 refreshTokenSync() 方法，使用 compareAndSet 避免并发刷新
- [x] 4.4 添加超时保护（REFRESH_TIMEOUT_SECONDS = 10）
- [x] 4.5 优化日志输出，便于调试并发场景
- [ ] 4.6 测试：模拟多个并发请求同时收到 401 响应

## 5. 集成测试

- [ ] 5.1 完整测试 token 过期流程（24小时后首次操作）
- [ ] 5.2 测试 refresh token 也过期的情况
- [ ] 5.3 测试网络异常时的行为
- [ ] 5.4 测试并发场景下的刷新逻辑
- [ ] 5.5 验证用户体验：错误提示清晰、登录流程顺畅
