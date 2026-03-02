# 联系人分享 API 文档

## 概述

联系人分享 API 提供基于好友关系的密码分享功能。用户只能与已添加的好友分享密码，确保分享的安全性和可控性。

**Base URL**: `http://localhost:8080/api`

**认证方式**: JWT Bearer Token 或 X-User-Id Header

**重要变更 (v2.2.0)**:
- 移除了直接链接分享 (DIRECT)
- 移除了附近用户分享 (NEARBY)
- 仅支持好友间的联系人分享
- 分享前需要确保双方已建立好友关系

---

## API 端点

### 1. 创建联系人分享

向指定好友分享密码。

```http
POST /v1/shares/contact
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**

```json
{
  "toUserId": "usr_target123",
  "passwordId": "pwd_abc456",
  "title": "Gmail 账户",
  "username": "user@gmail.com",
  "encryptedPassword": "BASE64_ENCODED_PASSWORD",
  "url": "https://gmail.com",
  "notes": "工作账户",
  "expiresInMinutes": 1440,
  "permission": {
    "canView": true,
    "canSave": true,
    "isRevocable": true
  }
}
```

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| toUserId | string | 是 | 接收方用户ID（必须是好友） |
| passwordId | string | 是 | 要分享的密码ID |
| title | string | 是 | 密码标题 |
| username | string | 否 | 用户名 |
| encryptedPassword | string | 是 | 加密的密码（Base64） |
| url | string | 否 | 网站URL |
| notes | string | 否 | 备注 |
| expiresInMinutes | integer | 否 | 过期时间（分钟），默认1440（24小时） |
| permission | object | 是 | 权限设置 |
| permission.canView | boolean | 是 | 是否允许查看 |
| permission.canSave | boolean | 是 | 是否允许保存 |
| permission.isRevocable | boolean | 是 | 是否可撤销 |

**响应 (200 OK)：**

```json
{
  "shareId": "share_789xyz",
  "passwordId": "pwd_abc456",
  "status": "PENDING",
  "createdAt": 1704067200,
  "expiresAt": 1704153600
}
```

**错误响应：**

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | CANNOT_SHARE_TO_SELF | 不能分享给自己 |
| 400 | NOT_FRIENDS | 只能分享给好友 |
| 400 | SHARE_ALREADY_EXISTS | 已存在对此密码的活跃分享 |
| 404 | USER_NOT_FOUND | 接收方用户不存在 |

---

### 2. 接收分享详情

获取分享的密码详情。

```http
GET /v1/shares/{shareId}
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| shareId | string | 是 | 分享ID |

**响应 (200 OK)：**

```json
{
  "shareId": "share_789xyz",
  "fromUserId": "usr_sender123",
  "fromDisplayName": "张三",
  "passwordId": "pwd_abc456",
  "passwordData": {
    "title": "Gmail 账户",
    "username": "user@gmail.com",
    "encryptedPassword": "BASE64_ENCODED_PASSWORD",
    "url": "https://gmail.com",
    "notes": "工作账户"
  },
  "permission": {
    "canView": true,
    "canSave": true,
    "isRevocable": true
  },
  "status": "PENDING",
  "createdAt": 1704067200,
  "expiresAt": 1704153600,
  "acceptedAt": null
}
```

---

### 3. 接受分享

接受并保存分享的密码。

```http
POST /v1/shares/{shareId}/accept
Authorization: Bearer {token}
```

**响应 (200 OK)：**

```json
{
  "shareId": "share_789xyz",
  "status": "ACCEPTED",
  "acceptedAt": 1704070800
}
```

---

### 4. 撤销分享

撤销之前创建的分享。

```http
DELETE /v1/shares/{shareId}
Authorization: Bearer {token}
```

**响应 (200 OK)：** 空响应体

---

### 5. 获取发送的分享列表

```http
GET /v1/shares/sent
Authorization: Bearer {token}
```

**响应 (200 OK)：**

```json
[
  {
    "shareId": "share_789xyz",
    "toUserId": "usr_target123",
    "toDisplayName": "李四",
    "passwordId": "pwd_abc456",
    "passwordTitle": "Gmail 账户",
    "status": "PENDING",
    "createdAt": 1704067200,
    "expiresAt": 1704153600
  }
]
```

---

### 6. 获取接收的分享列表

```http
GET /v1/shares/received
Authorization: Bearer {token}
```

**响应 (200 OK)：**

```json
[
  {
    "shareId": "share_789xyz",
    "fromUserId": "usr_sender123",
    "fromDisplayName": "张三",
    "passwordId": "pwd_abc456",
    "passwordData": {
      "title": "Gmail 账户",
      "username": "user@gmail.com",
      "encryptedPassword": "BASE64_ENCODED_PASSWORD",
      "url": "https://gmail.com",
      "notes": "工作账户"
    },
    "permission": {
      "canView": true,
      "canSave": true,
      "isRevocable": true
    },
    "status": "PENDING",
    "createdAt": 1704067200,
    "expiresAt": 1704153600,
    "acceptedAt": null
  }
]
```

---

## 数据模型

### ContactShareStatus

| 状态 | 说明 |
|------|------|
| PENDING | 待接收 |
| ACCEPTED | 已接受 |
| EXPIRED | 已过期 |
| REVOKED | 已撤销 |

### 状态转换流程

```
创建分享 → PENDING
    ↓
    ├─→ 接受 → ACCEPTED
    ├─→ 过期 → EXPIRED
    └─→ 撤销 → REVOKED
```

---

## WebSocket 通知

当有新的分享时，接收方会收到实时通知：

```json
{
  "type": "NEW_SHARE",
  "shareId": "share_789xyz",
  "fromUserId": "usr_sender123",
  "fromDisplayName": "张三",
  "message": "张三 向你分享了一个密码",
  "timestamp": 1704067200000
}
```

**通知类型：**
- `NEW_SHARE` - 新分享
- `SHARE_ACCEPTED` - 分享已被接受
- `SHARE_REVOKED` - 分享已被撤销
- `SHARE_EXPIRED` - 分享已过期

---

## 与旧版本 API 的变化

| 旧 API (v2.1) | 新 API (v2.2) | 变更说明 |
|----------------|----------------|----------|
| `POST /v1/shares` (type=DIRECT) | ❌ 已移除 | 直接链接分享已移除 |
| `POST /v1/shares` (type=NEARBY) | ❌ 已移除 | 附近用户分享已移除 |
| `POST /v1/shares` (type=USER_TO_USER) | `POST /v1/shares/contact` | 改名为联系人分享 |
| `POST /v1/discovery/register` | ❌ 已移除 | 位置注册已移除 |
| `GET /v1/discovery/nearby` | ❌ 已移除 | 附近用户查询已移除 |

---

## 加密协议版本

### v2.0 (RSA-2048)

**混合加密协议**:
- **AES-256-GCM**: 加密实际的分享数据
- **RSA-OAEP**: 加密 AES 密钥（接收方公钥）
- **SHA256withRSA**: 数字签名验证发送方身份

**数据包格式**:
```json
{
  "version": "2.0",
  "encryptedAESKey": "base64(RSA-OAEP encrypted AES key)",
  "iv": "base64(AES IV, 12 bytes)",
  "encryptedData": "base64(AES-GCM encrypted data)",
  "signature": "base64(RSA-SHA256 signature)"
}
```

### v3.0 (X25519/Ed25519)

**现代椭圆曲线协议**:
- **X25519 ECDH**: 密钥交换，支持 ephemeral key（前向保密）
- **HKDF-SHA256**: 从 ECDH 共享密钥派生 AES 密钥（身份绑定）
- **Ed25519 EdDSA**: 数字签名
- **AES-256-GCM**: 加密数据

**数据包格式**:
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

**优势**:
- 性能提升 10-100 倍
- 密钥尺寸减少 8 倍
- 支持前向保密（ephemeral key）
- 抗侧信道攻击（常数时间实现）

### 版本协商

系统会根据接收方的密钥信息自动选择协议版本：

| 发送方 | 接收方 | 使用协议 |
|--------|--------|----------|
| v2.0 RSA | v2.0 RSA | v2.0 |
| v3.0 X25519 | v2.0 RSA | v2.0 (回退) |
| v2.0 RSA | v3.0 X25519 | v2.0 (发送方限制) |
| v3.0 X25519 | v3.0 X25519 | v3.0 (最优) |

**查询用户密钥信息**:
```http
GET /v1/users/{userId}/keys
Authorization: Bearer {token}
```

**响应**:
```json
{
  "userId": "user123",
  "rsaPublicKey": "MIIBIjANBgkqhki...",
  "x25519PublicKey": "JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==",
  "ed25519PublicKey": "LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0",
  "keyVersion": "v2"
}
```

**上传 ECC 公钥（迁移时）**:
```http
POST /v1/users/me/ecc-public-keys
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体**:
```json
{
  "x25519PublicKey": "JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==",
  "ed25519PublicKey": "LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0",
  "keyVersion": "v2"
}
```

---

## 安全说明

1. **好友验证**: 分享前会验证双方是否为好友关系（状态为 ACCEPTED）
2. **权限控制**: 支持细粒度权限设置（查看、保存、撤销）
3. **过期机制**: 所有分享都有过期时间，默认24小时
4. **加密传输**: 密码数据始终以加密形式传输和存储
5. **撤销保护**: 不可撤销的分享不能被撤销
6. **版本协商**: 自动选择最优加密协议（v3.0 优先，v2.0 回退）
7. **前向保密**: v3.0 协议使用 ephemeral key，长期私钥泄露不影响历史分享
8. **身份绑定**: HKDF 派生密钥时混合双方身份，防止密钥混淆攻击
9. **防重放攻击**: v3.0 包含时间戳验证，过期检查
10. **Invalid Curve 防护**: 验证 ephemeral public key 有效性

---

**版本**: 2.2.0
**最后更新**: 2026-01-22
**重要变更**: 简化为仅支持联系人分享
