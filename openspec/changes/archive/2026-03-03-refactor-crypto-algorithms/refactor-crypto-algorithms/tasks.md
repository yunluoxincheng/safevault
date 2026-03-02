# X25519/Ed25519 迁移 - 实施清单

## Phase 1: 前端依赖和工具类（2 天）

### 1.1 依赖集成
- [x] 1.1.1 在 `build.gradle` 中添加 Bouncy Castle 依赖
  ```gradle
  implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'
  ```
- [x] 1.1.2 验证库版本兼容性（API 29+）
- [x] 1.1.3 添加 ProGuard 规则减小 APK 体积
  ```proguard
  -keep class org.bouncycastle.crypto.** { *; }
  -keep class org.bouncycastle.math.ec.** { *; }
  -dontwarn org.bouncycastle.**
  ```

### 1.2 X25519KeyManager 实现
- [x] 1.2.1 创建 `X25519KeyManager` 接口
  ```java
  public interface X25519KeyManager {
      KeyPair generateKeyPair() throws Exception;
      byte[] performECDH(PrivateKey privateKey, PublicKey publicKey) throws Exception;
      void validatePublicKey(byte[] encodedKey) throws Exception;
      PublicKey decodePublicKey(byte[] encodedKey) throws Exception;
      PrivateKey decodePrivateKey(byte[] encodedKey) throws Exception;
  }
  ```
- [x] 1.2.2 实现 `SystemX25519KeyManager`（API 33+ 使用系统 API）
- [x] 1.2.3 实现 `BouncyCastleX25519KeyManager`（API < 33 回退）
- [x] 1.2.4 创建 `X25519KeyManagerFactory` 工厂类
- [x] 1.2.5 添加 invalid curve 验证逻辑

### 1.3 Ed25519Signer 实现
- [x] 1.3.1 创建 `Ed25519Signer` 接口
  ```java
  public interface Ed25519Signer {
      KeyPair generateKeyPair() throws Exception;
      byte[] sign(byte[] data, PrivateKey privateKey) throws Exception;
      boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception;
      PublicKey decodePublicKey(byte[] encodedKey) throws Exception;
      PrivateKey decodePrivateKey(byte[] encodedKey) throws Exception;
      void validatePublicKey(byte[] encodedKey) throws Exception;
      void validateSignatureSize(byte[] signature) throws Exception;
  }
  ```
- [x] 1.3.2 实现 `SystemEd25519Signer`（API 34+ 使用系统 API）
- [x] 1.3.3 实现 `BouncyCastleEd25519Signer`（API < 34 回退）
- [x] 1.3.4 创建 `Ed25519SignerFactory` 工厂类

### 1.4 HKDFManager 实现
- [x] 1.4.1 创建 `HKDFManager` 工具类
- [x] 1.4.2 实现 HMAC-SHA256 提取和扩展
- [x] 1.4.3 实现身份绑定 info 参数混合
  ```java
  public SecretKey deriveAESKey(byte[] sharedSecret, String senderId, String receiverId)
  ```

### 1.5 安全常量配置
- [x] 1.5.1 创建 `CryptoConstants` 类
  ```java
  public class CryptoConstants {
      public static final long MAX_TIMESTAMP_DRIFT = 10 * 60 * 1000;
      public static final String AES_ALGORITHM = "AES/GCM/NoPadding";
      public static final int AES_KEY_SIZE = 256;
      public static final int GCM_IV_SIZE = 12;
      public static final String HKDF_HASH_ALGORITHM = "HmacSHA256";
      public static final int HKDF_OUTPUT_SIZE = 32;
      public static final int X25519_KEY_SIZE = 32;
      public static final int ED25519_SIGNATURE_SIZE = 64;
      // ...
  }
  ```

## Phase 2: ShareEncryptionManager 扩展（2 天）

### 2.1 协议版本 3.0 数据结构
- [x] 2.1.1 创建 `EncryptedSharePacketV3` DTO
  ```java
  public class EncryptedSharePacketV3 {
      private String version = "3.0";
      private String ephemeralPublicKey;  // 32 bytes
      private String encryptedData;
      private String iv;  // 12 bytes
      private String signature;  // 64 bytes
      private long createdAt;
      private long expireAt;
      private String senderId;
  }
  ```

### 2.2 v3.0 加密流程
- [x] 2.2.1 实现 `createEncryptedPacketV3()` 方法
  - 生成 ephemeral X25519 密钥对
  - ECDH 密钥交换
  - HKDF 派生 AES 密钥（混合双方身份）
  - 序列化 ShareDataPacket 为 JSON
  - Ed25519 对原始 JSON 签名
  - AES-256-GCM 加密
  - 组装 EncryptedSharePacketV3

### 2.3 v3.0 解密流程
- [x] 2.3.1 实现 `openEncryptedPacketV3()` 方法
  - 验证时间戳（防重放攻击）
  - 验证 ephemeral public key 有效性
  - ECDH 密钥交换
  - HKDF 派生 AES 密钥
  - AES-256-GCM 解密
  - Ed25519 验证签名
  - 反序列化 JSON

### 2.4 版本协商
- [x] 2.4.1 实现 `detectProtocolVersion()` 方法
- [x] 2.4.2 创建 `UserKeyInfo` DTO
  ```java
  public class UserKeyInfo {
      private String userId;
      private String rsaPublicKey;
      private String x25519PublicKey;
      private String ed25519PublicKey;
      private String keyVersion;
  }
  ```
- [x] 2.4.3 实现版本协商矩阵逻辑

### 2.5 单元测试
- [x] 2.5.1 测试 X25519 密钥生成和 ECDH
- [x] 2.5.2 测试 Ed25519 签名和验证
- [x] 2.5.3 测试 HKDF 密钥派生
- [x] 2.5.4 测试 v3.0 加密/解密流程
- [x] 2.5.5 测试版本协商逻辑

## Phase 3: 后端集成（2 天）

### 3.1 数据库 Schema 变更
- [x] 3.1.1 创建迁移脚本
  ```sql
  ALTER TABLE users ADD COLUMN x25519_public_key TEXT;
  ALTER TABLE users ADD COLUMN ed25519_public_key TEXT;
  ALTER TABLE users ADD COLUMN key_version VARCHAR(10) DEFAULT 'v1';
  CREATE INDEX idx_users_key_version ON users(key_version);
  ```
- [x] 3.1.2 更新 `User` 实体类

### 3.2 API 实现
- [x] 3.2.1 实现 `GET /v1/users/{userId}/keys` - 获取用户密钥信息
  ```json
  {
    "userId": "user123",
    "rsaPublicKey": "...",
    "x25519PublicKey": "...",
    "ed25519PublicKey": "...",
    "keyVersion": "v2"
  }
  ```
- [x] 3.2.2 实现 `POST /v1/users/me/ecc-public-keys` - 上传公钥（迁移时）
  ```json
  {
    "x25519PublicKey": "...",
    "ed25519PublicKey": "...",
    "keyVersion": "v2"
  }
  ```
- [x] 3.2.3 更新 `UserRepository` 添加新字段
- [x] 3.2.4 更新 `UserService` 添加密钥管理方法

### 3.3 用户注册更新
- [x] 3.3.1 更新注册逻辑：新用户同时生成 RSA + X25519 + Ed25519 密钥
- [x] 3.3.2 后端密钥生成（或接收前端上传）

### 3.4 集成测试
- [x] 3.4.1 测试密钥查询 API（已创建：CryptoKeyManagementIntegrationTest.java）
- [x] 3.4.2 测试密钥上传 API（已创建：CryptoKeyManagementIntegrationTest.java）
- [x] 3.4.3 测试新用户注册流程（已创建：CryptoKeyManagementIntegrationTest.java）

## Phase 4: SecureKeyStorageManager 扩展（1 天）

### 4.1 密钥存储扩展
- [x] 4.1.1 添加 X25519 密钥存储方法
  ```java
  public void saveX25519KeyPair(KeyPair keyPair, byte[] dataKey);
  public KeyPair loadX25519KeyPair(byte[] dataKey);
  ```
  已实现：`encryptAndSaveX25519PrivateKey()`, `decryptX25519PrivateKey()`
- [x] 4.1.2 添加 Ed25519 密钥存储方法
  ```java
  public void saveEd25519KeyPair(KeyPair keyPair, byte[] dataKey);
  public KeyPair loadEd25519KeyPair(byte[] dataKey);
  ```
  已实现：`encryptAndSaveEd25519PrivateKey()`, `decryptEd25519PrivateKey()`
- [x] 4.1.3 更新 SharedPreferences 存储结构
  ```java
  {
      "rsa_public_key": "...",
      "rsa_private_key_encrypted": "...",
      "x25519_public_key": "...",
      "x25519_private_key_encrypted": "...",
      "ed25519_public_key": "...",
      "ed25519_private_key_encrypted": "...",
      "key_version": "v3",
      "has_migrated_to_v3": true
  }
  ```
  已完成：常量已定义，存储结构已更新

### 4.2 密钥版本标识
- [x] 4.2.1 添加 `getKeyVersion()` 方法
- [x] 4.2.2 添加 `hasMigratedToV3()` 方法

### 4.3 额外实现的方法
- [x] `getX25519PublicKey()` - 获取 X25519 公钥（字节数组）
- [x] `getX25519PublicKeyBase64()` - 获取 X25519 公钥（Base64字符串）
- [x] `getEd25519PublicKey()` - 获取 Ed25519 公钥（字节数组）
- [x] `getEd25519PublicKeyBase64()` - 获取 Ed25519 公钥（Base64字符串）
- [x] `setKeyVersion(String version)` - 设置密钥版本
- [x] `validateX25519KeyPair()` - 验证 X25519 密钥对完整性
- [x] `validateEd25519KeyPair()` - 验证 Ed25519 密钥对完整性

## Phase 5: 密钥迁移工具（2 天）

### 5.1 KeyMigrationService 实现
- [x] 5.1.1 创建 `KeyMigrationService` 类
- [x] 5.1.2 实现 `migrateToX25519(String masterPassword)` 方法
  - 检查是否已迁移
  - 解锁获取 DataKey（DataKey 不变）
  - 生成新的 X25519/Ed25519 密钥对
  - 用 DataKey 加密存储新私钥
  - 保存公钥
  - 上传公钥到服务器
  - 更新版本标识
- [x] 5.1.3 实现 `initializeCryptoKeys(byte[] dataKey)` 方法（新用户）
  - 同时生成 RSA + X25519 + Ed25519 密钥对
  - 用 DataKey 加密存储所有私钥
- [x] 5.1.4 添加迁移进度回调
- [x] 5.1.5 添加错误处理和回滚逻辑

### 5.2 迁移 UI 向导
- [x] 5.2.1 创建 `KeyMigrationActivity`
- [x] 5.2.2 设计迁移引导界面
- [x] 5.2.3 实现进度显示
- [x] 5.2.4 实现成功/失败提示
- [x] 5.2.5 添加重试机制

### 5.3 设置页面更新
- [x] 5.3.1 在设置中显示密钥版本状态
- [x] 5.3.2 添加"迁移到新加密算法"入口
- [x] 5.3.3 添加迁移完成通知

### 5.4 测试
- [x] 5.4.1 测试新用户初始化流程
- [x] 5.4.2 测试老用户迁移流程
- [x] 5.4.3 测试迁移失败回滚
- [x] 5.4.4 测试重复迁移（幂等性）

## Phase 6: 测试和优化（2 天）

### 6.1 单元测试
- [x] 6.1.1 `X25519KeyManager` 测试套件（已创建：X25519KeyManagerTest.java）
- [x] 6.1.2 `Ed25519Signer` 测试套件（已创建：Ed25519SignerTest.java）
- [x] 6.1.3 `HKDFManager` 测试套件（已创建：HKDFManagerTest.java）
- [x] 6.1.4 `ShareEncryptionManager` v3.0 测试套件（已创建：ShareEncryptionManagerTest.java）
- [x] 6.1.5 `KeyMigrationService` 测试套件（已创建：KeyMigrationServiceTest.java）

### 6.2 集成测试
- [x] 6.2.1 端到端分享流程测试（v3.0）（已创建：ShareEncryptionIntegrationTest.java）
- [x] 6.2.2 v2.0/v3.0 互操作测试（4 种组合场景）（已创建：ShareEncryptionIntegrationTest.java）
- [x] 6.2.3 版本协商测试（已创建：ShareEncryptionIntegrationTest.java）
- [x] 6.2.4 密钥迁移完整性测试（已包含在 KeyMigrationServiceTest.java）
- [x] 6.2.5 前后端集成测试（已创建：CryptoKeyManagementIntegrationTest.java）

### 6.3 安全测试
- [x] 6.3.1 重放攻击防护测试（已创建：ShareEncryptionSecurityTest.java）
- [x] 6.3.2 密钥混淆攻击防护测试（已创建：ShareEncryptionSecurityTest.java）
- [x] 6.3.3 Invalid curve 攻击防护测试（已创建：ShareEncryptionSecurityTest.java）
- [x] 6.3.4 时间戳验证测试（已创建：ShareEncryptionSecurityTest.java）
- [x] 6.3.5 前向保密验证测试（已创建：ShareEncryptionSecurityTest.java）

### 6.4 性能测试
- [x] 6.4.1 密钥生成速度对比（RSA vs X25519）（已创建：CryptoAlgorithmPerformanceTest.java）
- [x] 6.4.2 加密速度对比（已创建：CryptoAlgorithmPerformanceTest.java）
- [x] 6.4.3 解密速度对比（已创建：CryptoAlgorithmPerformanceTest.java）
- [x] 6.4.4 签名/验证速度对比（已创建：CryptoAlgorithmPerformanceTest.java）
- [x] 6.4.5 APK 体积测试（Bouncy Castle 影响）（已添加 ProGuard 优化规则）

### 6.5 兼容性测试
- [ ] 6.5.1 Android 10 (API 29) 测试
- [ ] 6.5.2 Android 13 (API 33) 测试
- [ ] 6.5.3 Android 14 (API 34) 测试
- [ ] 6.5.4 不同设备性能测试

### 6.6 优化
- [x] 6.6.1 ProGuard 配置优化（已更新 proguard-rules.pro）
- [x] 6.6.2 代码精简（已优化 ShareEncryptionManager 使用 CryptoConstants）
- [x] 6.6.3 性能瓶颈优化（已创建 CryptoUtils 工具类并集成）

## Phase 7: 文档和发布（1 天）

### 7.1 文档更新
- [x] 7.1.1 更新 `docs/security-architecture.md`（已添加 X25519/Ed25519 说明）
- [x] 7.1.2 更新 `docs/api/contact-sharing.md`（已添加加密协议版本说明）
- [x] 7.1.3 创建迁移指南 `docs/guides/crypto-migration.md`（已创建）
- [x] 7.1.4 更新 API 文档（已创建：docs/api/key-management.md）
- [x] 7.1.5 更新用户指南（已更新：docs/user-guide/password-sharing.md）

### 7.2 发布准备
- [x] 7.2.1 编写发布说明（changelog）（已创建：docs/changelog/v3.6.0-crypto-migration.md）
- [x] 7.2.2 准备应用内通知文案（已创建：app/res/values/crypto_migration_strings.xml）
- [x] 7.2.3 准备迁移引导文案（已创建：app/res/values/crypto_migration_guide_content.md）
- [x] 7.2.4 准备回滚方案文档（已创建：docs/operations/crypto-migration-rollback.md）

### 7.3 监控准备
- [x] 7.3.1 添加迁移成功率埋点（已创建：AnalyticsManager.java）
- [x] 7.3.2 添加 v2.0/v3.0 使用比例埋点（已集成到 AnalyticsManager）
- [x] 7.3.3 添加性能监控埋点（已集成到 ShareEncryptionManager）

**总计**: 约 11 天

---

## Rollback 计划

如果迁移失败，可以安全回滚：

### 前端回滚
- [ ] 保留所有 RSA 密钥（不删除）
- [ ] 保留 `has_migrated_to_v3` 标识
- [ ] 版本协商自动回退到 v2.0
- [ ] 停止生成 v3.0 分享

### 后端回滚
- [ ] 保留 `x25519_public_key` 和 `ed25519_public_key` 字段（置为 NULL）
- [ ] 暂停 v3.0 API 调用

### 用户数据
- [ ] 所有密码库数据不受影响（使用同一个 DataKey）
- [ ] 历史分享链接继续有效