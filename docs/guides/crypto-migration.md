# 加密算法迁移指南

## 概述

SafeVault 正在从 RSA-2048 加密算法迁移到现代椭圆曲线算法 X25519/Ed25519。本指南为用户和开发者提供完整的迁移说明。

**版本**:
- v2.0: RSA-2048 (旧版本，保持兼容)
- v3.0: X25519/Ed25519 (新版本，推荐使用)

---

## 用户迁移指南

### 为什么要迁移？

| 特性 | RSA-2048 (v2.0) | X25519/Ed25519 (v3.0) |
|------|-----------------|----------------------|
| 密钥生成速度 | ~50ms | ~1ms (50x 快) |
| 加密/解密速度 | ~50ms | ~1ms (50x 快) |
| 签名速度 | ~10ms | ~0.1ms (100x 快) |
| 公钥大小 | 256 字节 | 32 字节 (8x 小) |
| 私钥大小 | ~1.2 KB | 32 字节 (37.5x 小) |
| 前向保密 | ❌ 无 | ✅ 有 (ephemeral key) |
| 安全级别 | ~112 位 | 128 位 |

### 如何迁移？

1. **打开应用**，进入「设置」页面
2. **找到「加密算法」选项**，查看当前密钥版本
3. **点击「迁移到新加密算法」**按钮
4. **输入主密码**验证身份
5. **等待迁移完成**（通常 < 1 秒）
6. **迁移成功后**，应用会显示已升级到 v3.0

### 迁移安全吗？

✅ **完全安全**，原因如下：
- 迁移过程中**不会删除**您的 RSA 密钥
- 如果迁移失败，可以**安全回滚**
- 您的所有密码数据**不受影响**
- 历史分享链接**继续有效**

### 迁移后会发生什么？

- 新的密码分享会**自动使用** v3.0 协议
- 与同样已迁移的用户分享会获得**最佳性能**
- 与未迁移的用户分享会**自动回退**到 v2.0

### 可以不迁移吗？

✅ **可以不迁移**，但建议尽快迁移：
- 旧版本将继续正常工作
- 但无法享受新算法的性能优势
- 未来版本可能需要迁移才能使用某些新功能

---

## 开发者迁移指南

### 前端实现

#### 1. 检查用户密钥版本

```java
// 获取当前密钥版本
String keyVersion = secureKeyStorageManager.getKeyVersion();
boolean hasMigrated = "v2".equals(keyVersion); // v2 表示已迁移到 ECC
```

#### 2. 执行迁移

```java
// 迁移到 X25519/Ed25519
keyMigrationService.migrateToX25519(masterPassword);
```

#### 3. 版本协商

```java
// 检测接收方支持的协议版本
String protocolVersion = shareEncryptionManager.detectProtocolVersion(receiverUserId);
// 结果: "2.0" 或 "3.0"
```

#### 4. 使用 v3.0 加密

```java
// 创建 v3.0 加密数据包
EncryptedSharePacketV3 packet = shareEncryptionManager.createEncryptedPacketV3(
    shareData,
    receiverX25519PublicKey,
    senderEd25519PrivateKey,
    senderId,
    receiverId,
    expireAt
);
```

#### 5. 使用 v3.0 解密

```java
// 解密 v3.0 数据包
ShareDataPacket data = shareEncryptionManager.openEncryptedPacketV3(
    packet,
    receiverX25519PrivateKey,
    senderX25519PublicKey,
    senderEd25519PublicKey,
    senderId,
    receiverId
);
```

### 后端实现

#### 1. 获取用户密钥信息

```http
GET /v1/users/{userId}/keys
Authorization: Bearer {token}
```

#### 2. 上传 ECC 公钥（迁移时）

```http
POST /v1/users/me/ecc-public-keys
Authorization: Bearer {token}
Content-Type: application/json

{
  "x25519PublicKey": "...",
  "ed25519PublicKey": "...",
  "keyVersion": "v2"
}
```

#### 3. 数据库 Schema

```sql
-- users 表新增字段
ALTER TABLE users ADD COLUMN x25519_public_key TEXT;
ALTER TABLE users ADD COLUMN ed25519_public_key TEXT;
ALTER TABLE users ADD COLUMN key_version VARCHAR(10) DEFAULT 'v1';
```

### API 兼容性

| API 端点 | v2.0 支持 | v3.0 支持 |
|----------|-----------|-----------|
| POST /v1/shares/contact | ✅ | ✅ |
| GET /v1/shares/{shareId} | ✅ | ✅ |
| GET /v1/users/{userId}/keys | ✅ | ✅ |
| POST /v1/users/me/ecc-public-keys | - | ✅ |

---

## 迁移流程图

### 用户侧迁移流程

```
用户进入设置页面
    ↓
查看当前密钥版本
    ↓
点击"迁移到新加密算法"
    ↓
输入主密码验证身份
    ↓
生成新的 X25519/Ed25519 密钥对
    ↓
用 DataKey 加密新私钥
    ↓
保存公钥到本地
    ↓
上传公钥到服务器
    ↓
更新密钥版本标识
    ↓
显示迁移成功
```

### 版本协商流程

```
发送方想分享给接收方
    ↓
查询接收方密钥信息
    ↓
┌─────────────────────┐
│ 接收方有 X25519 公钥？│
└─────────────────────┘
    ↓ 是          ↓ 否
使用 v3.0      使用 v2.0
    ↓              ↓
X25519 ECDH    RSA-OAEP
    ↓              ↓
HKDF 派生      直接加密
    ↓              ↓
Ed25519 签名   RSA-SHA256
```

---

## 常见问题

### Q1: 迁移需要多长时间？

**A**: 通常 < 1 秒。迁移只是生成新的密钥对并保存，不需要加密/解密现有数据。

### Q2: 迁移会影响我的密码数据吗？

**A**: 不会。您的所有密码数据存储方式不变，继续使用同一个 DataKey 加密。

### Q3: 迁移失败怎么办？

**A**: 迁移是幂等的，失败后可以重试。RSA 密钥不会被删除，可以安全回滚。

### Q4: 迁移后还能和旧版本用户分享吗？

**A**: 可以。系统会自动检测对方支持的协议版本，回退到 v2.0 进行兼容。

### Q5: RSA 密钥会被删除吗？

**A**: 不会。RSA 密钥会保留，用于与旧版本用户的兼容，直到未来版本。

### Q6: 前向保密是什么意思？

**A**: 前向保密意味着即使您的长期私钥泄露，历史分享数据仍无法被解密。v3.0 每次分享使用新的 ephemeral key 实现。

### Q7: 我需要重新登录吗？

**A**: 不需要。迁移过程不影响您的登录状态。

### Q8: 迁移需要联网吗？

**A**: 需要联网上传新的公钥到服务器，但密钥生成是本地完成的。

### Q9: 迁移会被其他人知道吗？

**A**: 不会。迁移只是更新您的密钥信息，不会发送任何通知给其他用户。

### Q10: 开发者需要做什么？

**A**:
- 更新前端代码以支持 v3.0 协议
- 实现版本协商逻辑
- 提供迁移 UI
- 更新 API 调用以支持新的密钥信息

---

## 技术细节

### 密钥存储结构

```
SharedPreferences:
{
    // RSA 密钥 (v2.0)
    "rsa_public_key": "...",
    "rsa_private_key_encrypted": "...",

    // X25519 密钥 (v3.0)
    "x25519_public_key": "...",  // 32 bytes
    "x25519_private_key_encrypted": "...",  // 32 bytes encrypted

    // Ed25519 密钥 (v3.0)
    "ed25519_public_key": "...",  // 32 bytes
    "ed25519_private_key_encrypted": "...",  // 32 bytes encrypted

    // 版本标识
    "key_version": "v2",  // v1=RSA only, v2=ECC
    "has_migrated_to_v3": true
}
```

### 加密流程对比

#### v2.0 (RSA)
```
1. 生成随机 AES 密钥
2. 用 RSA 公钥加密 AES 密钥
3. 用 AES 加密数据
4. 用 RSA 私钥签名
```

#### v3.0 (X25519/Ed25519)
```
1. 生成 ephemeral X25519 密钥对
2. ECDH 密钥交换（ephemeral 私钥 + 对方长期公钥）
3. HKDF 派生 AES 密钥（混合双方身份）
4. 用 AES 加密数据
5. 用 Ed25519 私钥签名
```

---

## 相关文档

- [安全架构文档](../security-architecture.md)
- [联系人分享 API](../api/contact-sharing.md)
- [提案: 迁移到 X25519/Ed25519](../openspec/changes/refactor-crypto-algorithms/proposal.md)
- [技术设计](../openspec/changes/refactor-crypto-algorithms/design.md)

---

**版本**: 1.0.0
**最后更新**: 2026-03-03
**状态**: 发布候选