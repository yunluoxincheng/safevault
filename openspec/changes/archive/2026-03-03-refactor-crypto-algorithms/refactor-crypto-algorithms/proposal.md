# Change: 迁移到 X25519/Ed25519 加密算法

## Why

当前 SafeVault 使用 RSA-2048 进行端到端分享，存在以下限制：
1. **密钥尺寸大**：RSA 公钥 256 字节，私钥 ~1.2KB，存储开销大
2. **性能较慢**：RSA 加密/解密比椭圆曲线慢 10-100 倍
3. **无前向保密**：一旦私钥泄露，所有历史分享数据可被解密
4. **无现代特性**：不支持 ephemeral key 等现代安全特性

X25519 (Curve25519 ECDH) 和 Ed25519 (Curve25519 EdDSA) 是现代加密算法的行业标准：
- **密钥尺寸小**：公私钥各 32 字节（RSA 的 1/8）
- **性能高**：比 RSA 快 10-100 倍
- **前向保密**：每次会话生成新的 ephemeral key，长期私钥泄露不影响历史分享
- **安全性**：256 位安全级别（等同于 RSA-3072）
- **抗侧信道**：常数时间实现

## What Changes

### 前端 (Android)
- [x] 新增 `X25519KeyManager` 接口和实现（系统 API + Bouncy Castle 回退）
- [x] 新增 `Ed25519Signer` 接口和实现
- [x] 新增 `HKDFManager` 工具类
- [x] 新增协议版本 3.0（X25519/Ed25519）
- [x] 扩展 `ShareEncryptionManager` 支持 v3.0
- [x] 扩展 `SecureKeyStorageManager` 同时存储 RSA + X25519 + Ed25519 密钥
- [x] 实现密钥迁移工具 `KeyMigrationService`
- [x] 创建迁移 UI 向导 `KeyMigrationActivity`
- [x] 添加 ProGuard 规则
- [x] 新增 `CryptoUtils` 工具类优化性能
- [x] 新增 `AnalyticsManager` 实现埋点监控

### 后端 (Spring Boot)
- [x] `users` 表扩展：添加 `x25519_public_key`、`ed25519_public_key`、`key_version` 字段
- [x] 新增 API：`GET /v1/users/{userId}/keys` - 获取用户密钥信息
- [x] 新增 API：`POST /v1/users/me/ecc-public-keys` - 上传公钥（迁移时）
- [x] 更新用户注册逻辑：新用户生成 X25519/Ed25519 + RSA 密钥

### 兼容性
- [x] 保持对版本 2.0（RSA）的完全向后兼容
- [x] 版本协商机制（基于对方密钥类型自动选择）
- [x] RSA 密钥在过渡期保留

## Impact

- **Affected specs**: `crypto-security`, `contact-sharing`
- **Affected code (前端)**:
  - `crypto/ShareEncryptionManager.java` - 扩展支持 v3.0
  - `security/SecureKeyStorageManager.java` - 扩展支持新密钥
  - 新增 `crypto/X25519KeyManager.java` - X25519 密钥管理
  - 新增 `crypto/Ed25519Signer.java` - Ed25519 签名
  - 新增 `crypto/HKDFManager.java` - HKDF 密钥派生
  - 新增 `crypto/CryptoConstants.java` - 安全常量
  - 新增 `service/KeyMigrationService.java` - 密钥迁移服务
  - 新增 `ui/migration/KeyMigrationActivity.java` - 迁移界面
- **Affected code (后端)**:
  - `UserRepository.java` - 扩展密钥字段
  - `UserService.java` - 密钥管理
  - `UserController.java` - 新增 API 端点
  - `schema.sql` - 数据库迁移

## Breaking Changes

**非破坏性变更**：本提案保持完全向后兼容，所有现有数据和分享链接继续有效。

**分阶段过渡**：
- **阶段 1**（当前版本）：新数据使用 v3.0，旧数据保持 v2.0，RSA 密钥保留
- **阶段 2**：提供迁移工具，老用户可选择迁移到 v3.0
- **阶段 3**：应用内通知建议迁移
- **阶段 4**（未来版本）：新连接强制使用 v3.0，RSA 仅用于解密历史分享
- **阶段 5**（未来版本）：弃用并移除 RSA 支持

## Non-Goals

- 不强制迁移（用户可选择）
- 不立即移除 RSA 支持
- 不改变 DataKey 加密方式（不影响密码库数据）
- 不实现完整的 Double Ratchet 协议（未来可选）
- 不迁移历史分享链接（保持 v2.0 格式）

## Success Criteria

- [ ] 新用户默认生成 X25519/Ed25519 密钥
- [ ] v3.0 分享功能正常工作
- [ ] v2.0/v3.0 互操作（版本协商正确）
- [ ] 密钥迁移成功率 > 99%
- [ ] 性能提升达到预期（密钥生成/加密快 10-100 倍）
- [ ] 无数据丢失或损坏
- [ ] 安全测试通过（重放攻击、密钥混淆、invalid curve 防护）