# X25519/Ed25519 迁移 - 实施清单

## 1. 依赖集成

- [ ] 1.1 添加 Bouncy Castle 依赖
  - [ ] 在 `build.gradle` 中添加 `bcprov-jdk18on`
  - [ ] 验证库版本兼容性

- [ ] 1.2 创建 Provider 注册
  - [ ] 在应用启动时注册 Bouncy Castle Provider
  - [ ] 处理 Provider 注册失败情况

## 2. 核心工具类实现

- [ ] 2.1 创建 `X25519KeyManager`
  - [ ] 实现 `generateKeyPair()` 方法
  - [ ] 实现 `getPublicKey()` 和 `getPrivateKey()`
  - [ ] 实现 `performECDH(remotePublicKey)` 方法

- [ ] 2.2 创建 `Ed25519Signer`
  - [ ] 实现 `generateKeyPair()` 方法
  - [ ] 实现 `sign(data)` 方法
  - [ ] 实现 `verify(signature, data, publicKey)` 方法

- [ ] 2.3 密钥序列化
  - [ ] 实现 `encodePublicKey(PublicKey)` → Base64
  - [ ] 实现 `decodePublicKey(Base64)` → PublicKey
  - [ ] 实现 `encodePrivateKey(PrivateKey)` → Base64
  - [ ] 实现 `decodePrivateKey(Base64)` → PrivateKey

## 3. ShareEncryptionManager 扩展

- [ ] 3.1 新增协议版本 3.0
  - [ ] 定义 `EncryptedSharePacketV3` 数据结构
  - [ ] 使用 X25519 执行 ECDH 密钥交换
  - [ ] 使用 Ed25519 执行数字签名

- [ ] 3.2 版本协商
  - [ ] 检测发送方和接收方支持的版本
  - [ ] 优先使用双方支持的最新版本（3.0 > 2.0）

- [ ] 3.3 保持 2.0 兼容
  - [ ] 保留现有 RSA 加密逻辑
  - [ ] 根据对方公钥类型选择算法

## 4. SecureKeyStorageManager 扩展

- [ ] 4.1 存储新密钥对
  - [ ] 生成 X25519 密钥对用于密钥交换
  - [ ] 生成 Ed25519 密钥对用于签名
  - [ ] 加密存储私钥（使用 DataKey）

- [ ] 4.2 读取密钥
  - [ ] 支持读取 X25519 私钥
  - [ ] 支持读取 Ed25519 私钥
  - [ ] 保持 RSA 密钥读取兼容

## 5. 密钥迁移工具

- [ ] 5.1 创建 `KeyMigrationService`
  - [ ] `migrateFromRsaToX25519()` 方法
  - [ ] 解密 RSA 私钥
  - [ ] 生成新的 X25519/Ed25519 密钥对
  - [ ] 加密存储新私钥

- [ ] 5.2 UI 迁移向导
  - [ ] 创建 `KeyMigrationActivity`
  - [ ] 显示迁移进度
  - [ ] 处理迁移失败回滚

- [ ] 5.3 云端密钥更新
  - [ ] 上传新的公钥到服务器
  - [ ] 更新 `user_private_keys` 表

## 6. 后端集成

- [ ] 6.1 数据库扩展
  - [ ] `users` 表新增 `x25519_public_key` 字段
  - [ ] `users` 表新增 `ed25519_public_key` 字段
  - [ ] 保持 `public_key` (RSA) 字段兼容

- [ ] 6.2 API 扩展
  - [ ] GET /v1/users/:id 支持返回两种公钥
  - [ ] POST /v1/users/keys 支持上传新公钥
  - [ ] 版本协商逻辑

## 7. 测试和验证

- [ ] 7.1 单元测试
  - [ ] 测试 X25519 密钥生成
  - [ ] 测试 ECDH 密钥交换
  - [ ] 测试 Ed25519 签名和验证
  - [ ] 测试密钥序列化

- [ ] 7.2 集成测试
  - [ ] 测试版本 3.0 分享流程
  - [ ] 测试版本 2.0/3.0 互操作
  - [ ] 测试密钥迁移完整性

- [ ] 7.3 性能测试
  - [ ] 对比 RSA vs X25519 加密速度
  - [ ] 对比密钥尺寸
  - [ ] 验证性能提升

## 8. 文档和发布

- [ ] 8.1 更新文档
  - [ ] 更新加密算法说明
  - [ ] 更新 API 文档
  - [ ] 创建迁移指南

- [ ] 8.2 发布说明
  - [ ] 说明新加密算法优势
  - [ ] 指导用户迁移密钥
  - [ ] 说明向后兼容性
