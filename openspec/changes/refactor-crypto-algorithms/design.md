# X25519/Ed25519 迁移 - 技术设计

## Context

SafeVault 当前使用 RSA-2048-OAEP 进行端到端分享：

```java
// 当前实现 (v2.0)：
public EncryptedSharePacket createEncryptedPacket(
    ShareDataPacket data,
    PublicKey receiverPublicKey,  // RSA-2048
    PrivateKey senderPrivateKey  // RSA-2048
) {
    // 1. 生成随机 AES 密钥
    SecretKey aesKey = generateAESKey();

    // 2. 用 RSA 公钥加密 AES 密钥
    byte[] encryptedAesKey = rsaEncrypt(aesKey, receiverPublicKey);

    // 3. 用发送方 RSA 私钥签名
    byte[] signature = rsaSign(data, senderPrivateKey);

    // 4. 组装 EncryptedSharePacket v2.0
    return new EncryptedSharePacket("2.0", encryptedAesKey, ...);
}
```

**RSA 限制**：
- 公钥尺寸：256 字节 (2048 位)
- 私钥尺寸：~1.2 KB (PKCS8)
- 加密速度：~10ms (手机)
- 解密速度：~50ms (手机)

## Goals / Non-Goals

**Goals**:
- 迁移到现代椭圆曲线算法 (X25519/Ed25519)
- 实现前向保密
- 提升性能和减少密钥尺寸
- 保持向后兼容

**Non-Goals**:
- 不强制迁移（用户可选择）
- 不立即移除 RSA 支持
- 不改变分享协议的基本流程

## Decisions

### Decision 1: 使用 X25519/Ed25519

**选择**:
- **X25519** (Curve25519 ECDH)：密钥交换
- **Ed25519** (Curve25519 EdDSA)：数字签名

**原因**:
- 行业标准：Signal、WhatsApp、Wire 等使用
- 性能：比 RSA 快 10-100 倍
- 密钥尺寸：公私钥各 32 字节
- 安全性：256 位安全级别
- 前向保密：支持 ephemeral key

**替代方案**:
- RSA-3072：密钥更大、更慢
- P-256 (NIST)：有后门嫌疑

### Decision 2: 协议版本 3.0

**选择**: 定义新的协议版本 3.0

**原因**:
- 保持向后兼容 (v2.0 = RSA)
- 明确的版本标识
- 支持版本协商

**EncryptedSharePacketV3 结构**：
```java
public class EncryptedSharePacketV3 {
    private String version = "3.0";
    private byte[] ephemeralPublicKey;  // 发送方 X25519 ephemeral public key (32 bytes)
    private byte[] encryptedData;        // AES-256-GCM 加密的数据
    private byte[] iv;                   // AES IV (12 bytes)
    private byte[] signature;            // Ed25519 签名 (64 bytes)
}
```

### Decision 3: 加密流程

**选择**: 使用 Ephemeral Diffie-Hellman

**流程**：
```
1. Alice 生成 ephemeral X25519 密钥对
2. Alice 用 ephemeral 私钥 + Bob 长期公钥 → 共享密钥
3. 用共享密钥派生 AES 密钥
4. 用 AES 加密数据
5. 用 Ed25519 私钥签名
6. 发送 ephemeral public key + 加密数据 + 签名
```

**优势**：
- 每次分享使用新的 ephemeral key
- 即使 Alice 长期私钥泄露，历史分享仍安全（前向保密）

```java
public EncryptedSharePacketV3 createEncryptedPacketV3(
    ShareDataPacket data,
    PublicKey receiverX25519PublicKey,
    PrivateKey senderEd25519PrivateKey,
    KeyPair senderEphemeralX25519KeyPair
) {
    // 1. ECDH 密钥交换
    byte[] sharedSecret = x25519KeyManager.performECDH(
        senderEphemeralX25519KeyPair.getPrivate(),
        receiverX25519PublicKey
    );

    // 2. 派生 AES 密钥
    SecretKey aesKey = hkdf.deriveKey(sharedSecret, "safevault-sharing");

    // 3. 加密数据
    byte[] iv = generateIV();
    byte[] encryptedData = aesGcmEncrypt(data, aesKey, iv);

    // 4. 签名
    byte[] signature = ed25519Signer.sign(data, senderEd25519PrivateKey);

    // 5. 组装
    return new EncryptedSharePacketV3(
        "3.0",
        senderEphemeralX25519KeyPair.getPublic().getEncoded(),
        encryptedData,
        iv,
        signature
    );
}
```

### Decision 4: 版本协商

**选择**: 根据对方的公钥类型自动选择版本

**逻辑**：
```java
public String detectProtocolVersion(PublicKey receiverPublicKey) {
    if (receiverPublicKey instanceof X25519PublicKey) {
        return "3.0";  // 双方都支持 X25519
    } else if (receiverPublicKey instanceof RSAPublicKey) {
        return "2.0";  // 回退到 RSA
    } else {
        throw new UnsupportedKeyException();
    }
}
```

### Decision 5: 密钥存储

**选择**: 同时存储 RSA 和 X25519/Ed25519 密钥

**数据结构**：
```java
// SharedPreferences 存储：
{
    // RSA 密钥 (v2.0)
    "rsa_public_key": "BASE64_ENCODED_RSA_PUBLIC_KEY",
    "rsa_private_key_encrypted": "ENCRYPTED_RSA_PRIVATE_KEY",

    // X25519 密钥 (v3.0)
    "x25519_public_key": "BASE64_ENCODED_X25519_PUBLIC_KEY",
    "x25519_private_key_encrypted": "ENCRYPTED_X25519_PRIVATE_KEY",

    // Ed25519 密钥 (v3.0)
    "ed25519_public_key": "BASE64_ENCODED_ED25519_PUBLIC_KEY",
    "ed25519_private_key_encrypted": "ENCRYPTED_ED25519_PRIVATE_KEY",

    "key_version": "v3"  // 当前活跃的密钥版本
}
```

### Decision 6: 迁移策略

**选择**: 非强制迁移，用户可选择

**阶段**：
1. **阶段 1**（当前版本）：新用户默认生成 X25519/Ed25519 密钥
2. **阶段 2**：提供迁移工具，老用户可选择迁移
3. **阶段 3**：发布通知，建议迁移
4. **阶段 4**（未来版本）：新连接强制使用 v3.0

**迁移流程**：
```java
public void migrateToX25519() {
    // 1. 检查是否已有 X25519 密钥
    if (hasX25519Keys()) return;

    // 2. 生成新的 X25519/Ed25519 密钥对
    KeyPair x25519KeyPair = x25519KeyManager.generateKeyPair();
    KeyPair ed25519KeyPair = ed25519Signer.generateKeyPair();

    // 3. 加密存储私钥
    encryptAndSavePrivateKey(x25519KeyPair.getPrivate(), dataKey);
    encryptAndSavePrivateKey(ed25519KeyPair.getPrivate(), dataKey);

    // 4. 上传公钥到服务器
    uploadPublicKeysToServer(x25519KeyPair.getPublic(), ed25519KeyPair.getPublic());

    // 5. 更新版本标识
    prefs.edit().putString("key_version", "v3").apply();
}
```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Bouncy Castle 库体积大 | APK 增加 ~5MB | 使用 ProGuard 精简 |
| 后端不支持 v3.0 | 新功能无法使用 | 同步更新后端 |
| 迁移失败 | 用户无法使用新功能 | 保留 RSA 密钥备份 |
| Ephemeral key 生成耗时 | 轻微延迟 | ~1ms，可接受 |
| 兼容性问题 | 与旧版本无法分享 | 版本协商，回退到 RSA |

## Migration Plan

### Phase 1: 依赖和工具类（2 天）
- 添加 Bouncy Castle
- 创建 `X25519KeyManager` 和 `Ed25519Signer`

### Phase 2: ShareEncryptionManager 扩展（2 天）
- 实现 v3.0 加密流程
- 版本协商逻辑

### Phase 3: 后端集成（2 天）
- 数据库扩展
- API 更新

### Phase 4: 密钥迁移（2 天）
- 迁移工具
- UI 向导

### Phase 5: 测试（1 天）
- 单元测试
- 集成测试
- 性能测试

### Rollback
如果迁移失败，回退到 RSA (v2.0)，保留密钥备份。

## Open Questions

1. **是否支持同时使用多种算法？**
   - 建议：是的，支持版本协商
   - 新用户默认 v3.0，老用户可选迁移

2. **是否需要前向保密的 Double Ratchet？**
   - 当前方案：单次 ephemeral key
   - 可选：未来实现完整的 Double Ratchet 协议

3. **Bouncy Castle 是否有更轻量的替代？**
   - 可选：使用 libsodium（需要 NDK）
   - 权衡：纯 Java vs JNI 性能
