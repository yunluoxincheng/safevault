# Change: 迁移到 X25519/Ed25519 加密算法

## Why

当前 SafeVault 使用 RSA-2048 进行端到端分享，存在以下限制：
1. **密钥尺寸大**：RSA 公钥 256 字节，私钥更大的存储开销
2. **性能较慢**：RSA 加密/解密比椭圆曲线慢 10-100 倍
3. **无前向保密**：一旦私钥泄露，所有历史分享数据可被解密
4. **无现代特性**：不支持 Double Ratchet 等现代协议

X25519 (ECDH) 和 Ed25519 (EdDSA) 是现代加密算法的行业标准：
- **密钥尺寸小**：公私钥各 32 字节
- **性能高**：比 RSA 快 10-100 倍
- **前向保密**：每次会话生成新的 ephemeral key
- **安全性**：256 位安全级别（等同于 RSA-3072）

## What Changes

- [ ] 新增 `X25519KeyManager` 和 `Ed25519Signer` 类
- [ ] 新增协议版本 3.0（X25519/Ed25519）
- [ ] 保持对版本 2.0（RSA）的向后兼容
- [ ] 密钥迁移工具（RSA → X25519/Ed25519）
- [ ] Bouncy Castle 库集成

## Impact

- **Affected specs**: `crypto-security`, `contact-sharing`
- **Affected code**:
  - `crypto/ShareEncryptionManager.java`
  - `security/SecureKeyStorageManager.java`
  - 新增 `crypto/X25519KeyManager.java`
  - 新增 `crypto/Ed25519Signer.java`

## Breaking Changes

**分阶段破坏性变更**：
- 阶段 1：新数据使用 3.0 格式，旧数据保持 2.0
- 阶段 2：提供迁移工具，用户可选择迁移
- 阶段 3：未来版本移除 RSA 支持
