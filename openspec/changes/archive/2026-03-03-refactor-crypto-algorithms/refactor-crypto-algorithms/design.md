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
- **无前向保密**：长期私钥泄露后，历史分享可被解密

**密钥层级关系**（迁移不影响）：
```
主密码
 ├─→ Argon2id → PasswordKey → 加密 DataKey
 └─→ 生物识别 → DeviceKey → 加密 DataKey
                            ↓
                        DataKey (随机256位)
                            ↓
        ┌───────────────────┼───────────────────┐
        ↓                   ↓                   ↓
    RSA 私钥           X25519 私钥        Ed25519 私钥
    (v2.0)            (v3.0)             (v3.0)
```

## Goals / Non-Goals

**Goals**:
- 迁移到现代椭圆曲线算法 (X25519/Ed25519)
- 实现前向保密（前向安全）
- 提升性能和减少密钥尺寸
- 保持向后兼容（RSA 密钥保留用于过渡期）

**Non-Goals**:
- 不强制迁移（用户可选择）
- 不立即移除 RSA 支持
- 不改变 DataKey 加密方式
- 不改变密码库数据的加密方式
- 不实现完整的 Double Ratchet 协议（未来可选）

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
- 抗侧信道攻击：常数时间实现

**替代方案**:
| 方案 | 密钥尺寸 | 速度 | 问题 |
|------|---------|------|------|
| RSA-3072 | 384B | 慢 | 无前向保密，密钥大 |
| P-256 (NIST) | 64B | 中 | 有后门嫌疑，历史争议 |
| P-384 (NIST) | 96B | 慢 | 同 P-256 |

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
    private long createdAt;              // Unix 时间戳（毫秒），防重放
    private long expireAt;               // 过期时间（毫秒）
    private String senderId;             // 发送方用户 ID
}
```

### Decision 3: HKDF 参数配置

**选择**: 使用 HKDF (HMAC-SHA256) 从 ECDH 共享密钥派生 AES 密钥

**参数**:
- **哈希算法**: HMAC-SHA256
- **输出长度**: 32 字节 (256 位 AES-256)
- **Info 参数**: `"safevault-sharing\0senderId\0receiverId"` (身份绑定)

```java
// HKDF 派生密钥的完整流程
public SecretKey deriveAESKeyFromSharedSecret(
    byte[] sharedSecret,     // ECDH 共享密钥 (32 bytes)
    String senderId,         // 发送方用户 ID
    String receiverId        // 接收方用户 ID
) {
    // 构建 info 参数，混合双方身份（防止密钥混淆攻击）
    String info = "safevault-sharing\0" + senderId + "\0" + receiverId;

    // 使用 HKDF-SHA256 派生密钥
    byte[] derivedKey = HKDF.fromHmacSha256()
        .expand(sharedSecret, info.getBytes(StandardCharsets.UTF_8), 32);

    return new SecretKeySpec(derivedKey, "AES");
}
```

**为什么需要身份绑定**:
- 纯 ECDH 不包含身份信息，可能遭受密钥混淆攻击
- 通过 info 参数混合 senderId 和 receiverId，确保密钥与特定通信对绑定

### Decision 4: 加密流程

**选择**: 使用 Ephemeral Diffie-Hellman

**完整流程**：
```
1. Alice 生成 ephemeral X25519 密钥对 (每次分享都生成新的)
2. Alice 用 ephemeral 私钥 + Bob 长期公钥 → ECDH 共享密钥
3. 用 HKDF 从共享密钥派生 AES 密钥 (混合双方身份)
4. 将 ShareDataPacket 序列化为 JSON (Gson)
5. 对原始 JSON 字节数组签名 (Ed25519)
6. 用 AES-256-GCM 加密 JSON 字节数组
7. 组装 EncryptedSharePacketV3
```

**优势**：
- 每次分享使用新的 ephemeral key
- 即使 Alice 长期私钥泄露，历史分享仍安全（前向保密）

```java
public EncryptedSharePacketV3 createEncryptedPacketV3(
    ShareDataPacket data,                    // 原始数据包
    PublicKey receiverX25519PublicKey,       // 接收方长期公钥
    PrivateKey senderEd25519PrivateKey,      // 发送方长期私钥（签名）
    String senderId,                         // 发送方用户 ID
    String receiverId,                       // 接收方用户 ID
    long expireAt                            // 过期时间
) throws Exception {
    // 1. 生成 ephemeral X25519 密钥对（每次分享都生成新的）
    KeyPair ephemeralKeyPair = x25519KeyManager.generateKeyPair();

    // 2. ECDH 密钥交换
    byte[] sharedSecret = x25519KeyManager.performECDH(
        ephemeralKeyPair.getPrivate(),
        receiverX25519PublicKey
    );

    // 3. HKDF 派生 AES 密钥（混合双方身份）
    SecretKey aesKey = hkdfManager.deriveAESKey(sharedSecret, senderId, receiverId);

    // 4. 序列化数据包为 JSON
    Gson gson = new Gson();
    String json = gson.toJson(data);
    byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);

    // 5. 对原始 JSON 签名（不是对密文签名）
    byte[] signature = ed25519Signer.sign(jsonBytes, senderEd25519PrivateKey);

    // 6. AES-256-GCM 加密
    byte[] iv = generateSecureRandom(12);  // 12 bytes IV for GCM
    byte[] encryptedData = aesGcmEncrypt(jsonBytes, aesKey, iv);

    // 7. 组装数据包
    long now = System.currentTimeMillis();
    return new EncryptedSharePacketV3(
        "3.0",
        ephemeralKeyPair.getPublic().getEncoded(),  // 32 bytes
        encryptedData,
        iv,                                         // 12 bytes
        signature,                                  // 64 bytes
        now,
        expireAt,
        senderId
    );
}
```

### Decision 5: 解密流程

```java
public ShareDataPacket openEncryptedPacketV3(
    EncryptedSharePacketV3 packet,
    PrivateKey receiverX25519PrivateKey,      // 接收方长期私钥
    PublicKey senderX25519PublicKey,          // 发送方长期公钥（未使用，保留用于验证）
    PublicKey senderEd25519PublicKey,         // 发送方 Ed25519 公钥（验证签名）
    String senderId,                          // 发送方用户 ID
    String receiverId                         // 接收方用户 ID
) throws Exception {
    // 1. 验证时间戳（防重放攻击）
    long now = System.currentTimeMillis();
    if (now > packet.expireAt) {
        throw new SecurityException("分享已过期");
    }
    if (Math.abs(now - packet.createdAt) > MAX_TIMESTAMP_DRIFT) {
        throw new SecurityException("时间戳异常");
    }

    // 2. 验证 ephemeral public key 是有效的曲线点
    x25519KeyManager.validatePublicKey(packet.ephemeralPublicKey);

    // 3. ECDH 密钥交换（用接收方私钥 + 发送方 ephemeral 公钥）
    byte[] sharedSecret = x25519KeyManager.performECDH(
        receiverX25519PrivateKey,
        x25519KeyManager.decodePublicKey(packet.ephemeralPublicKey)
    );

    // 4. HKDF 派生 AES 密钥（与加密时相同的 info）
    SecretKey aesKey = hkdfManager.deriveAESKey(sharedSecret, senderId, receiverId);

    // 5. AES-256-GCM 解密
    byte[] jsonBytes = aesGcmDecrypt(packet.encryptedData, aesKey, packet.iv);

    // 6. Ed25519 验证签名（对原始 JSON 验证）
    boolean isValid = ed25519Signer.verify(
        jsonBytes,
        packet.signature,
        senderEd25519PublicKey
    );
    if (!isValid) {
        throw new SecurityException("签名验证失败");
    }

    // 7. 反序列化 JSON
    Gson gson = new Gson();
    return gson.fromJson(new String(jsonBytes, StandardCharsets.UTF_8), ShareDataPacket.class);
}
```

### Decision 6: 版本协商

**选择**: 基于接收方的密钥版本自动选择协议版本

**版本协商矩阵**：

| 发送方 | 接收方 | 使用的协议 | 说明 |
|--------|--------|-----------|------|
| v2.0 RSA | v2.0 RSA | v2.0 RSA | 双方都只有 RSA |
| v3.0 X25519 | v2.0 RSA | v2.0 RSA | 回退到 RSA |
| v2.0 RSA | v3.0 X25519 | v2.0 RSA | 发送方只有 RSA |
| v3.0 X25519 | v3.0 X25519 | v3.0 X25519 | 双方都支持 X25519 |

**实现逻辑**：
```java
public String detectProtocolVersion(String receiverUserId) throws Exception {
    // 1. 从服务器查询接收方的密钥信息
    UserKeyInfo receiverKeys = backendService.getUserKeyInfo(receiverUserId);

    // 2. 检查对方是否有 X25519 公钥
    if (receiverKeys.getX25519PublicKey() != null) {
        return "3.0";  // 优先使用 v3.0
    }

    // 3. 回退到 RSA
    if (receiverKeys.getRsaPublicKey() != null) {
        return "2.0";
    }

    throw new SecurityException("接收方没有可用的公钥");
}
```

**服务器返回的密钥信息**：
```java
public class UserKeyInfo {
    private String userId;
    private String rsaPublicKey;       // v2.0 (可选)
    private String x25519PublicKey;    // v3.0
    private String ed25519PublicKey;   // v3.0
    private String keyVersion;         // "v2" 或 "v3"
}
```

### Decision 7: 密钥存储

**选择**: 同时存储 RSA 和 X25519/Ed25519 密钥（都由同一个 DataKey 加密）

**密钥层级不变**：
```
DataKey (由 PasswordKey 或 DeviceKey 派生)
    ├─→ 加密 RSA 私钥 (v2.0)
    ├─→ 加密 X25519 私钥 (v3.0)
    └─→ 加密 Ed25519 私钥 (v3.0)
```

**SharedPreferences 存储结构**：
```java
// SharedPreferences 存储内容：
{
    // ========== RSA 密钥 (v2.0) - 保留用于兼容 ==========
    "rsa_public_key": "BASE64_ENCODED_RSA_PUBLIC_KEY",  // 256 bytes
    "rsa_private_key_encrypted": "ENCRYPTED_RSA_PRIVATE_KEY",  // ~1.2KB 加密后

    // ========== X25519 密钥 (v3.0) ==========
    "x25519_public_key": "BASE64_ENCODED_X25519_PUBLIC_KEY",  // 32 bytes
    "x25519_private_key_encrypted": "ENCRYPTED_X25519_PRIVATE_KEY",  // 32 bytes 加密后

    // ========== Ed25519 密钥 (v3.0) ==========
    "ed25519_public_key": "BASE64_ENCODED_ED25519_PUBLIC_KEY",  // 32 bytes
    "ed25519_private_key_encrypted": "ENCRYPTED_ED25519_PRIVATE_KEY",  // 32 bytes 加密后

    // ========== 版本标识 ==========
    "key_version": "v3",  // 当前活跃的密钥版本
    "has_migrated_to_v3": "true"  // 是否已完成迁移
}
```

**加密存储方式不变**（使用现有的 DataKey）：
```java
// 迁移时生成新密钥，用同一个 DataKey 加密
public void migrateAsymmetricKeys(byte[] dataKey) throws Exception {
    // 生成新密钥对
    KeyPair x25519KeyPair = x25519KeyManager.generateKeyPair();
    KeyPair ed25519KeyPair = ed25519Signer.generateKeyPair();

    // 用同一个 DataKey 加密（与 RSA 私钥相同的方式）
    byte[] encryptedX25519Priv = aesGcmEncrypt(
        x25519KeyPair.getPrivate().getEncoded(),
        dataKey,
        generateIV()
    );

    byte[] encryptedEd25519Priv = aesGcmEncrypt(
        ed25519KeyPair.getPrivate().getEncoded(),
        dataKey,
        generateIV()
    );

    // 保存到 SharedPreferences
    prefs.edit()
        .putString("x25519_private_key_encrypted", base64Encode(encryptedX25519Priv))
        .putString("ed25519_private_key_encrypted", base64Encode(encryptedEd25519Priv))
        .putString("x25519_public_key", base64Encode(x25519KeyPair.getPublic().getEncoded()))
        .putString("ed25519_public_key", base64Encode(ed25519KeyPair.getPublic().getEncoded()))
        .putString("key_version", "v3")
        .putBoolean("has_migrated_to_v3", true)
        .apply();
}
```

### Decision 8: 迁移策略

**选择**: 非强制迁移，用户可选择

**迁移阶段**：

| 阶段 | 描述 | RSA 状态 |
|------|------|---------|
| **阶段 1** (当前版本) | 新用户默认生成 X25519/Ed25519 + RSA 密钥 | 保留 |
| **阶段 2** | 提供迁移工具，老用户可选择迁移 | 保留 |
| **阶段 3** | 发布应用内通知，建议迁移 | 保留 |
| **阶段 4** (未来版本) | 新连接强制使用 v3.0，RSA 仅用于解密历史分享 | 保留 |
| **阶段 5** (未来版本) | 弃用并移除 RSA | 移除 |

**新用户注册流程**：
```java
// 新用户注册时同时生成所有密钥
public void initializeCryptoKeys(byte[] dataKey) throws Exception {
    // 1. 生成 RSA-2048 密钥对（v2.0 兼容）
    KeyPair rsaKeyPair = rsaKeyManager.generateKeyPair();

    // 2. 生成 X25519 密钥对（v3.0）
    KeyPair x25519KeyPair = x25519KeyManager.generateKeyPair();

    // 3. 生成 Ed25519 密钥对（v3.0）
    KeyPair ed25519KeyPair = ed25519Signer.generateKeyPair();

    // 4. 用同一个 DataKey 加密所有私钥
    saveEncryptedKey("rsa_private_key", rsaKeyPair.getPrivate(), dataKey);
    saveEncryptedKey("x25519_private_key", x25519KeyPair.getPrivate(), dataKey);
    saveEncryptedKey("ed25519_private_key", ed25519KeyPair.getPrivate(), dataKey);

    // 5. 保存公钥（明文）
    savePublicKey("rsa_public_key", rsaKeyPair.getPublic());
    savePublicKey("x25519_public_key", x25519KeyPair.getPublic());
    savePublicKey("ed25519_public_key", ed25519KeyPair.getPublic());

    // 6. 设置版本标识
    prefs.edit().putString("key_version", "v3").apply();
}
```

**老用户迁移流程**：
```java
public void migrateToX25519(String masterPassword) throws Exception {
    // 1. 检查是否已迁移
    if (prefs.getBoolean("has_migrated_to_v3", false)) {
        return;  // 已迁移，直接返回
    }

    // 2. 解锁获取 DataKey（DataKey 不变）
    CryptoSession session = secureStorage.unlock(masterPassword);
    byte[] dataKey = session.getDataKey();

    // 3. 生成新的 X25519/Ed25519 密钥对
    KeyPair x25519KeyPair = x25519KeyManager.generateKeyPair();
    KeyPair ed25519KeyPair = ed25519Signer.generateKeyPair();

    // 4. 用同一个 DataKey 加密新私钥
    saveEncryptedKey("x25519_private_key", x25519KeyPair.getPrivate(), dataKey);
    saveEncryptedKey("ed25519_private_key", ed25519KeyPair.getPrivate(), dataKey);

    // 5. 保存公钥
    savePublicKey("x25519_public_key", x25519KeyPair.getPublic());
    savePublicKey("ed25519_public_key", ed25519KeyPair.getPublic());

    // 6. 上传公钥到服务器
    backendService.uploadPublicKeys(new PublicKeyUploadRequest(
        prefs.getString("rsa_public_key"),   // 已有的 RSA 公钥
        prefs.getString("x25519_public_key"),
        prefs.getString("ed25519_public_key"),
        "v3"
    ));

    // 7. 更新版本标识
    prefs.edit()
        .putString("key_version", "v3")
        .putBoolean("has_migrated_to_v3", true)
        .apply();
}
```

### Decision 9: Android 集成策略

**选择**: 优先使用系统 API，回退到 Bouncy Castle

**Android 版本支持**：
| API 级别 | Android 版本 | X25519/Ed25519 支持 |
|---------|-------------|---------------------|
| 34+ | Android 14+ | 原生支持 (`XDH` KeyAgreement, `EdDSA` Signature) |
| 29-33 | Android 10-13 | 部分支持，需要 Bouncy Castle |
| < 29 | Android < 10 | 需要完整的 Bouncy Castle |

**实现方式**：
```java
public interface X25519KeyManager {
    KeyPair generateKeyPair() throws Exception;
    byte[] performECDH(PrivateKey privateKey, PublicKey publicKey) throws Exception;
    void validatePublicKey(byte[] encodedKey) throws Exception;
    PublicKey decodePublicKey(byte[] encodedKey) throws Exception;
}

// 根据 Android 版本选择实现
public class X25519KeyManagerFactory {
    public static X25519KeyManager create(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {  // API 33+
            return new SystemX25519KeyManager();
        } else {
            // 初始化 Bouncy Castle
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
            return new BouncyCastleX25519KeyManager();
        }
    }
}
```

**Gradle 依赖**：
```gradle
dependencies {
    // Bouncy Castle（仅在 API < 33 时使用）
    implementation 'org.bouncycastle:bcprov-jdk18on:1.78.1'

    // ProGuard 规则会自动精简
}
```

**ProGuard 规则**（减小 APK 体积）：
```proguard
# 只保留 X25519/Ed25519 相关类
-keep class org.bouncycastle.crypto.** { *; }
-keep class org.bouncycastle.math.ec.** { *; }
-dontwarn org.bouncycastle.**
```

### Decision 10: 后端数据库 Schema 变更

**选择**: 扩展现有表，添加新公钥字段

**users 表扩展**：
```sql
-- 添加 X25519/Ed25519 公钥字段
ALTER TABLE users ADD COLUMN x25519_public_key TEXT;
ALTER TABLE users ADD COLUMN ed25519_public_key TEXT;
ALTER TABLE users ADD COLUMN key_version VARCHAR(10) DEFAULT 'v2';

-- 添加索引
CREATE INDEX idx_users_key_version ON users(key_version);
```

**完整的 users 表结构**：
```sql
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,

    -- RSA 公钥 (v2.0) - 保留
    rsa_public_key TEXT,

    -- X25519 公钥 (v3.0)
    x25519_public_key TEXT,

    -- Ed25519 公钥 (v3.0)
    ed25519_public_key TEXT,

    -- 密钥版本标识
    key_version VARCHAR(10) DEFAULT 'v2',

    -- 其他字段...
    display_name VARCHAR(100),
    avatar_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**API 变更**：

**GET /v1/users/{userId}/keys** - 获取用户密钥信息
```json
{
  "userId": "user123",
  "rsaPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQE...",
  "x25519PublicKey": "JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==",
  "ed25519PublicKey": "LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0",
  "keyVersion": "v3"
}
```

**POST /v1/users/keys** - 上传公钥（迁移时）
```json
{
  "rsaPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQE...",
  "x25519PublicKey": "JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==",
  "ed25519PublicKey": "LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0",
  "keyVersion": "v3"
}
```

### Decision 11: 安全常量配置

```java
public class CryptoConstants {
    // 时间戳相关
    public static final long MAX_TIMESTAMP_DRIFT = 10 * 60 * 1000;  // 10 分钟

    // AES 配置
    public static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    public static final int AES_KEY_SIZE = 256;
    public static final int GCM_IV_SIZE = 12;
    public static final int GCM_TAG_SIZE = 16;

    // HKDF 配置
    public static final String HKDF_HASH_ALGORITHM = "HmacSHA256";
    public static final int HKDF_OUTPUT_SIZE = 32;  // AES-256

    // X25519 配置
    public static final int X25519_KEY_SIZE = 32;

    // Ed25519 配置
    public static final int ED25519_SIGNATURE_SIZE = 64;
    public static final int ED25519_PUBLIC_KEY_SIZE = 32;
    public static final int ED25519_PRIVATE_KEY_SIZE = 32;

    // 密钥信息前缀
    public static final String HKDF_INFO_PREFIX = "safevault-sharing\0";
}
```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Bouncy Castle 库体积大 | APK 增加 ~3-5MB | ProGuard 精简，API 33+ 使用系统 API |
| 后端不支持 v3.0 | 新功能无法使用 | 同步更新后端，版本协商回退到 RSA |
| 迁移失败 | 用户无法使用新功能 | 保留 RSA 密钥备份，可重试迁移 |
| SharedPreferences 大小限制 | 密钥数据可能超限 | 当前总大小约 2KB，远低于限制 |
| 兼容性问题 | 与旧版本无法分享 | 版本协商，自动回退到 RSA |
| 密钥混淆攻击 | 错误的通信对 | HKDF info 参数混合双方身份 |
| 重放攻击 | 旧分享被重复使用 | 时间戳验证，过期检查 |
| Invalid Curve 攻击 | 恶意公钥导致密钥泄露 | 验证 ephemeral public key 有效性 |

## Migration Plan

### Phase 1: 前端依赖和工具类（2 天）

- [ ] 添加 Bouncy Castle 依赖
- [ ] 创建 `X25519KeyManager` 接口和实现
- [ ] 创建 `Ed25519Signer` 接口和实现
- [ ] 创建 `HKDFManager` 工具类
- [ ] 实现版本检测工厂类
- [ ] 添加 ProGuard 规则

### Phase 2: ShareEncryptionManager 扩展（2 天）

- [ ] 实现 `createEncryptedPacketV3()` 方法
- [ ] 实现 `openEncryptedPacketV3()` 方法
- [ ] 实现版本协商逻辑 `detectProtocolVersion()`
- [ ] 添加时间戳验证
- [ ] 添加密钥验证（invalid curve 防护）
- [ ] 单元测试

### Phase 3: 后端集成（2 天）

- [ ] 数据库 Schema 变更（users 表）
- [ ] 实现 GET /v1/users/{userId}/keys API
- [ ] 实现 POST /v1/users/keys API
- [ ] 更新用户注册逻辑（生成新密钥）
- [ ] API 文档更新
- [ ] 集成测试

### Phase 4: 密钥迁移工具（2 天）

- [ ] 实现 `migrateToX25519()` 方法
- [ ] 实现 `initializeCryptoKeys()` 方法（新用户）
- [ ] 创建迁移 UI 向导
- [ ] 添加迁移进度和错误处理
- [ ] 保留 RSA 密钥备份逻辑
- [ ] 测试迁移和回滚

### Phase 5: 测试和优化（2 天）

- [ ] 单元测试（X25519、Ed25519、HKDF）
- [ ] 集成测试（端到端分享流程）
- [ ] 兼容性测试（v2.0/v3.0 混合场景）
- [ ] 性能测试（加密/解密速度）
- [ ] 安全测试（重放攻击、密钥混淆等）
- [ ] APK 体积优化检查

### Phase 6: 发布和监控（1 天）

- [ ] 发布内部测试版
- [ ] 收集用户反馈
- [ ] 监控迁移成功率
- [ ] 监控 v2.0/v3.0 使用比例
- [ ] 准备回滚方案

**总计**: 约 11 天

### Rollback 计划

如果迁移失败，可以安全回滚：

1. **前端回滚**:
   - 保留所有 RSA 密钥
   - 保留 `has_migrated_to_v3` 标识
   - 版本协商自动回退到 v2.0

2. **后端回滚**:
   - 保留 x25519_public_key 和 ed25519_public_key 字段
   - 新字段置为 NULL 不影响现有功能

3. **用户数据**:
   - 所有密码库数据不受影响（使用同一个 DataKey）
   - 历史分享链接继续有效

## Open Questions

### Q1: 是否需要强制迁移？

**当前方案**: 非强制，用户可选

**讨论**:
- 优点：用户体验好，平滑过渡
- 缺点：过渡期可能较长（6-12 个月）

**建议**: 保持非强制，但通过以下方式鼓励迁移：
- 应用内通知
- 迁移完成奖励（如徽章）
- 在设置中显示密钥版本状态

### Q2: 是否需要实现 Double Ratchet？

**当前方案**: 单次 ephemeral key

**讨论**:
- Double Ratchet 提供更强的前向保密和后向保密
- 但实现复杂度显著增加
- 对于单次密码分享场景，单次 ephemeral key 已足够

**建议**: 暂不实现，未来如有实时消息功能需求时再考虑

### Q3: 何时可以移除 RSA 支持？

**当前方案**: 未定义

**建议**:
- 等待至少 90% 的用户完成迁移
- 发布至少 2 个主要版本后
- 提前 6 个月通知用户
- 提供紧急回滚通道

### Q4: 是否需要迁移历史分享链接？

**当前方案**: 不迁移，保留 v2.0 格式

**讨论**:
- 历史分享链接已加密，无法重新加密
- 保留 v2.0 解密能力

**建议**: 历史链接保持原样，新分享使用 v3.0

## 附录

### A. 数据包格式对比

**v2.0 RSA**:
```json
{
  "version": "2.0",
  "encryptedAESKey": "base64(RSA-OAEP encrypted AES key)",
  "iv": "base64(AES IV)",
  "encryptedData": "base64(AES-GCM encrypted data)",
  "signature": "base64(RSA-SHA256 signature)"
}
```

**v3.0 X25519**:
```json
{
  "version": "3.0",
  "ephemeralPublicKey": "base64(X25519 ephemeral public key, 32 bytes)",
  "iv": "base64(AES IV, 12 bytes)",
  "encryptedData": "base64(AES-GCM encrypted data)",
  "signature": "base64(Ed25519 signature, 64 bytes)",
  "createdAt": 1234567890000,
  "expireAt": 1234567990000,
  "senderId": "user123"
}
```

### B. 性能对比（估算）

| 操作 | RSA-2048 | X25519/Ed25519 | 提升 |
|------|---------|----------------|------|
| 密钥生成 | ~50ms | ~1ms | 50x |
| 加密 | ~10ms | ~1ms | 10x |
| 解密 | ~50ms | ~1ms | 50x |
| 签名 | ~10ms | ~0.1ms | 100x |
| 验证 | ~1ms | ~0.1ms | 10x |
| 公钥大小 | 256B | 32B | 8x |
| 私钥大小 | ~1.2KB | 32B | 37.5x |

### C. 安全性对比

| 特性 | RSA-2048 | X25519/Ed25519 |
|------|---------|----------------|
| 安全级别 | ~112 位 | 128 位 |
| 前向保密 | 无 | 有（ephemeral key） |
| 抗量子攻击 | 弱 | 弱（都需升级到后量子算法） |
| 侧信道防护 | 依赖实现 | 常数时间实现 |
| 密钥混淆攻击 | 有风险 | 通过身份绑定防护 |