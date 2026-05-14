## 1. Inventory & Documentation

- [ ] 1.1 盘点 Android 密钥相关类：SecureKeyStorageManager、Argon2KeyDerivationManager、BiometricAuthManager、SessionGuard 的当前调用时序
- [ ] 1.2 盘点 Android UI 层（LoginActivity、MainActivity、RegisterActivity）中的密钥操作调用点
- [ ] 1.3 盘点后端 AuthController、AuthService、JwtTokenProvider、TokenRevokeService 的 token 生命周期逻辑
- [ ] 1.4 绘制并文档化当前注册/登录/解锁/锁定/登出的实际密钥时序图
- [ ] 1.5 对比实际代码行为与 design.md 中定义的状态机，记录偏差

## 2. Key Lifecycle Alignment

- [ ] 2.1 验证注册流程中 PasswordKey→DataKey→DeviceKey(AES) 的生成顺序与 design.md 一致
- [ ] 2.2 验证注册流程实际落点：确认 sessionGuard.unlockWithDataKey() 在注册后被调用，状态直达 UNLOCKED
- [ ] 2.3 验证 PasswordKey 明文在 DataKey 加密/解密后被安全清除
- [ ] 2.4 区分生物识别 ENROLLMENT 和 UNLOCK 前置条件：确认 enrollment 只检查 PasswordKeyEncryptedDataKey，unlock 检查 DeviceKey + DeviceKeyEncryptedDataKey
- [ ] 2.5 确保 DeviceKey 失效（KeyPermanentlyInvalidatedException）时优雅降级到主密码解锁
- [ ] 2.6 添加密钥生命周期关键节点的单元测试

## 3. Session State Machine

- [ ] 3.1 定义 SessionState 枚举或文档化状态常量：UNINITIALIZED、REGISTERED、LOGGED_IN、UNLOCKED、LOCKED、LOGGED_OUT
- [ ] 3.2 在 SessionGuard 或新建类中实现状态转换校验（拒绝非法转换）
- [ ] 3.3 确认注册流程状态转换：UNINITIALIZED → UNLOCKED（经密钥初始化 + JWT + DataKey 加载）
- [ ] 3.4 统一锁定检查入口：确保 Activity 在 onResume 中只调用 SessionGuard.shouldLockBySessionTimeout
- [ ] 3.5 确保 ApplicationLifecycleWatcher 仅在系统内存压力时清除 DataKey，不再处理业务级后台超时
- [ ] 3.6 验证登出时执行完整清理：DataKey 清除、token 清除、后端 refresh token 撤销

## 4. Backend Token Lifecycle

- [ ] 4.1 创建 RefreshTokenRecord 实体和 Flyway 迁移：jti、family、userId、rotated、revoked、expiresAt
- [ ] 4.2 创建 RefreshTokenRecordRepository
- [ ] 4.3 实现 refresh token 轮换：刷新成功后旧 token 标记 rotated = true，签发新 token（同 family）
- [ ] 4.4 实现 refresh token 重用检测：rotated = true 的 token 被重用时撤销该 family 下所有 token
- [ ] 4.5 验证 TokenRevokeService 在登出时正确撤销 refresh token
- [ ] 4.6 更新 JwtTokenProvider.generateRefreshToken()，让 refresh token 携带 jti 和 family claim
- [ ] 4.7 客户端适配：刷新成功后同步替换本地 refresh token
- [ ] 4.8 梳理 RevokedToken 与 RefreshTokenRecord 的职责边界（RevokedToken 仍可能用于 access token/logout denylist，不废弃）

## 5. Verification

- [ ] 5.1 Android assembleDebug 通过
- [ ] 5.2 Android 单元测试通过
- [ ] 5.3 后端 mvnw test 通过
- [ ] 5.4 手动验证：注册（直达 UNLOCKED）→后台超时锁定→生物识别解锁→登出 完整流程
- [ ] 5.5 手动验证：生物识别 ENROLLMENT 只需主密码路径（不需要 DeviceKey 已存在）
- [ ] 5.6 手动验证：DeviceKey 失效后降级到主密码解锁
- [ ] 5.7 手动验证：refresh token 轮换正常工作，旧 token 重用触发 family 撤销
- [ ] 5.8 更新文档：在 docs/security/ 下添加 key-lifecycle.md
