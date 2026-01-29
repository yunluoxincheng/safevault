# 混合加密方案设计文档

## 架构概述

### 组件关系图

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ShareActivity                                │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                   ShareEncryptionManager                         ││
│  │  ┌─────────────────────────────────────────────────────────────┐││
│  │  │  createEncryptedPacket()                                    │││
│  │  │  1. serializeShareDataPacket() → JSON (720B)               │││
│  │  │  2. generateAESKey() → 32B                                  │││
│  │  │  3. generateIV() → 12B                                      │││
│  │  │  4. encryptWithAES(data, key, iv) → ciphertext              │││
│  │  │  5. encryptAESKeyWithRSA(key, receiverPubKey) → encryptedKey│││
│  │  │  6. signShare(data, senderPrivKey) → signature              │││
│  │  │  7. 组装 EncryptedSharePacket (v2.0)                        │││
│  │  └─────────────────────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      EncryptedSharePacket                            │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  version: "2.0"                                                 ││
│  │  encryptedData: AES加密的ShareDataPacket (Base64)              ││
│  │  encryptedAESKey: RSA加密的AES密钥 (Base64)                     ││
│  │  iv: AES-GCM初始化向量 (Base64)                                 ││
│  │  signature: RSA-SHA256签名 (Base64)                             ││
│  │  senderId, createdAt, expireAt                                  ││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ReceiveShareActivity                              │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │                   ShareEncryptionManager                         ││
│  │  ┌─────────────────────────────────────────────────────────────┐││
│  │  │  openEncryptedPacket()                                      │││
│  │  │  1. 验证版本 (必须是 "2.0")                                  │││
│  │  │  2. decryptAESKeyWithRSA(encryptedKey, receiverPrivKey)     │││
│  │  │  3. decryptWithAES(encryptedData, aesKey, iv)               │││
│  │  │  4. deserializeShareDataPacket() → ShareDataPacket          │││
│  │  │  5. verifySignature(data, signature, senderPubKey)          │││
│  │  └─────────────────────────────────────────────────────────────┘││
│  └─────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

## 数据流详解

### 加密流程（发送方）

```
ShareDataPacket (明文)
├── senderId: "user_abc"
├── senderPublicKey: "MIIBIjANBgkqhki..." (400B)
├── password: {...}
└── permission: {...}
         │
         ▼ serializeShareDataPacket()
    JSON String (720B)
         │
         ▼ generateAESKey() + generateIV()
    AES Key (32B) + IV (12B)
         │
         ├──► encryptWithAES(data, key, iv)
         │    └──► ciphertext (~736B)
         │
         └──► encryptAESKeyWithRSA(key, receiverPubKey)
              └──► encryptedKey (256B) ──► Base64 (344B)
         │
         ▼ signShare(data, senderPrivKey)
    signature (256B) ──► Base64 (344B)
         │
         ▼ 组装 EncryptedSharePacket
    {
      version: "2.0",
      encryptedData: Base64(ciphertext),
      encryptedAESKey: Base64(encryptedKey),
      iv: Base64(iv),
      signature: Base64(signature),
      senderId: "user_abc",
      createdAt: 1234567890,
      expireAt: 0
    }
```

### 解密流程（接收方）

```
EncryptedSharePacket (接收)
├── version: "2.0"
├── encryptedData: "YWJjZGVmZ2hpams..."
├── encryptedAESKey: "TUlJRUl..."
├── iv: "MTIzNDU2Nzg5MDEy"
├── signature: "U2lnbmF0dXJl..."
└── senderId: "user_abc"
         │
         ▼ decryptAESKeyWithRSA(encryptedAESKey, receiverPrivKey)
    AES Key (32B)
         │
         ▼ decryptWithAES(encryptedData, aesKey, iv)
    JSON String (720B)
         │
         ▼ deserializeShareDataPacket()
    ShareDataPacket (明文)
         │
         ▼ verifySignature(data, signature, senderPubKey)
    true/false
```

## 安全分析

### 加密强度

| 组件 | 算法 | 密钥长度 | 安全性 |
|------|------|----------|--------|
| AES 数据加密 | AES-GCM | 256 位 | 高，业界标准 |
| RSA 密钥加密 | RSA-OAEP | 2048 位 | 高，112位安全强度 |
| 签名算法 | RSA-PSS | 2048 位 | 高，防篡改 |
| 随机数生成 | SecureRandom | - | 密码学安全 |

### 安全属性

- **机密性**：AES-256-GCM 提供数据机密性
- **完整性**：GCM 认证标签检测篡改
- **真实性**：RSA 签名验证发送方身份
- **前向安全**：每包独立的 AES 密钥和 IV
- **抗重放**：时间戳和过期时间检测

### 潜在威胁

| 威胁 | 缓解措施 |
|------|----------|
| 中间人攻击 | RSA 公钥必须从可信渠道获取 |
| 密钥泄露 | AES 密钥仅存在于内存，使用后清除 |
| 重放攻击 | 检查 createdAt 和 expireAt |
| 降级攻击 | 严格验证版本号必须是 "2.0" |

## 性能分析

### 计算复杂度

| 操作 | 复杂度 | 耗时估算 (RSA-2048) |
|------|--------|---------------------|
| AES-256 密钥生成 | O(1) | <1ms |
| AES-GCM 加密 (1KB) | O(n) | <1ms |
| RSA-OAEP 加密 (32B) | O(n³) | ~10ms |
| RSA-OAEP 解密 (32B) | O(n³) | ~50ms |
| RSA-SHA256 签名 | O(n³) | ~50ms |
| RSA-SHA256 验证 | O(n³) | ~2ms |

### 数据大小

| 项目 | 大小 |
|------|------|
| ShareDataPacket JSON | ~720 字节 |
| AES 密钥 | 32 字节 |
| IV | 12 字节 |
| 加密后的 AES 密钥 | 256 字节 → Base64 ~344 字节 |
| 加密后的数据 | ~736 字节 → Base64 ~984 字节 |
| 签名 | 256 字节 → Base64 ~344 字节 |
| **总计** | **~1700 字节** |

## 错误处理

### 异常处理策略

```java
try {
    // 加密/解密操作
} catch (InvalidKeyException e) {
    Log.e(TAG, "密钥无效", e);
    return null;
} catch (BadPaddingException e) {
    Log.e(TAG, "数据被篡改或密钥错误", e);
    return null;
} catch (IllegalBlockSizeException e) {
    Log.e(TAG, "数据块大小错误", e);
    return null;
} catch (AEADBadTagException e) {
    Log.e(TAG, "GCM 认证标签验证失败", e);
    return null;
}
```

### 错误消息

| 错误 | 用户消息 | 日志 |
|------|----------|------|
| AES 密钥生成失败 | "加密失败，请重试" | "Failed to generate AES key" |
| RSA 加密失败 | "加密失败" | "Failed to encrypt AES key with RSA" |
| 版本不匹配 | "分享协议版本不兼容，请更新应用" | "Unsupported packet version: X" |
| 签名验证失败 | "分享数据验证失败" | "Signature verification failed" |
| 数据解密失败 | "解密失败" | "Failed to decrypt share data" |

## 测试策略

### 单元测试

```java
@Test
public void testCreateEncryptedPacket() {
    ShareDataPacket data = createTestData();
    PublicKey receiverPub = getTestPublicKey();
    PrivateKey senderPriv = getTestPrivateKey();

    EncryptedSharePacket packet = encryptionManager.createEncryptedPacket(
        data, receiverPub, senderPriv
    );

    assertNotNull(packet);
    assertEquals("2.0", packet.getVersion());
    assertTrue(packet.isValid());
}

@Test
public void testOpenEncryptedPacket() {
    // 先创建加密包
    EncryptedSharePacket packet = createTestEncryptedPacket();

    // 解密
    ShareDataPacket decrypted = encryptionManager.openEncryptedPacket(
        packet, receiverPrivKey, senderPubKey
    );

    assertNotNull(decrypted);
    assertEquals(originalData.getTitle(), decrypted.getTitle());
}

@Test
public void testSignatureVerification() {
    // 篡改数据
    EncryptedSharePacket tampered = createTestEncryptedPacket();
    tampered.setEncryptedData("tampered_data");

    // 验证应该失败
    ShareDataPacket result = encryptionManager.openEncryptedPacket(
        tampered, receiverPrivKey, senderPubKey
    );

    assertNull(result);
}

@Test
public void testVersionValidation() {
    EncryptedSharePacket v1 = new EncryptedSharePacket();
    v1.setVersion("1.0");
    // ... 设置旧版本字段

    assertFalse(v1.isValid()); // 旧版本应该被拒绝
}
```

### 集成测试

1. **端到端测试**：ShareActivity → QR码/蓝牙 → ReceiveShareActivity
2. **跨设备测试**：不同 Android 版本（10, 11, 12, 13, 14）
3. **大数据测试**：分享包含长密码、长备注的数据
4. **边界测试**：空字段、特殊字符、Unicode

## 实现注意事项

### 1. AES 密钥管理

```java
// ✅ 正确：每次生成新的 AES 密钥
SecretKey aesKey = generateAESKey();  // 使用 SecureRandom

// ❌ 错误：重用 AES 密钥
SecretKey aesKey = getOrCreateCachedKey();  // 不要这样做！
```

### 2. IV 唯一性

```java
// ✅ 正确：每次生成新的 IV
byte[] iv = generateIV();  // 使用 SecureRandom

// ❌ 错误：使用固定 IV
byte[] iv = FIXED_IV;  // 严重安全漏洞！
```

### 3. GCM 认证标签

```java
// ✅ 正确：使用 GCMParameterSpec
GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

// ❌ 错误：不指定标签长度
cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
```

### 4. 密钥清除

```java
// 加密/解密完成后，敏感数据应该被清除
// 注意：Java 没有可靠的方式清除内存中的密钥
// 建议：尽快让密钥对象超出作用域，让 GC 回收
```
