# 密钥管理 API 文档

## 概述

密钥管理 API 提供用户密钥信息的查询和上传功能，用于支持协议版本协商和密钥迁移。

**Base URL**: `http://localhost:8080/api`

**认证方式**: JWT Bearer Token

---

## API 端点

### 1. 获取用户密钥信息

获取指定用户的所有公钥信息，用于版本协商。

```http
GET /v1/users/{userId}/keys
Authorization: Bearer {token}
```

**路径参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 是 | 用户 ID |

**响应 (200 OK)：**

```json
{
  "userId": "user123",
  "rsaPublicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFj8C3A5K8J9N5B5p8H2K2M3N6L7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0P1Q2R3S4T5U6V7W8X9Y0Z1A2B3C4D5E6F7G8H9I0J1K2L3M4N5O6P7Q8R9S0T1U2V3W4X5Y6Z7A8B9C0D1E2F3G4H5I6J7K8L9M0N1O2P3Q4R5S6T7U8V9W0X1Y2Z3A4B5C6D7E8F9G0H1I2J3K4L5M6N7O8P9Q0R1S2T3U4V5W6X7Y8Z9A0B1C2D3E4F5G6H7I8J9K0L1M2N3O4P5Q6R7S8T9U0V1W2X3Y4Z5A6B7C8D9E0F1G2H3I4J5K6L7M8N9O0QwIDAQAB",
  "x25519PublicKey": "JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==",
  "ed25519PublicKey": "LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0",
  "keyVersion": "v2"
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | string | 用户 ID |
| rsaPublicKey | string | RSA-2048 公钥（Base64 编码），v2.0 协议使用 |
| x25519PublicKey | string | X25519 公钥（Base64 编码），v3.0 协议使用 |
| ed25519PublicKey | string | Ed25519 公钥（Base64 编码），v3.0 协议使用 |
| keyVersion | string | 密钥版本（"v1" = RSA only, "v2" = ECC） |

**错误响应：**

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 404 | USER_NOT_FOUND | 用户不存在 |

---

### 2. 上传 ECC 公钥

上传用户的 X25519/Ed25519 椭圆曲线公钥到服务器，用于密钥迁移。

```http
POST /v1/users/me/ecc-public-keys
Authorization: Bearer {token}
Content-Type: application/json
```

**请求体：**

```json
{
  "x25519PublicKey": "JBFzNz8GnZ2F1Q3B5cWx5a3Zhc2l3ZXJ0eXVpb3Bhcw==",
  "ed25519PublicKey": "LoJw5p9W2XyP8Q7rN3k5m2J9h8G4f1C6d3E5a2B1c8F7A9d2C4e6G0",
  "keyVersion": "v2"
}
```

**请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| x25519PublicKey | string | 是 | X25519 公钥（Base64 编码，32 字节） |
| ed25519PublicKey | string | 是 | Ed25519 公钥（Base64 编码，32 字节） |
| keyVersion | string | 是 | 密钥版本（"v2" 表示已迁移到 ECC） |

**响应 (200 OK)：**

```json
{
  "success": true,
  "message": "ECC 公钥上传成功",
  "uploadedAt": "2026-03-03T10:30:00"
}
```

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 操作是否成功 |
| message | string | 结果消息 |
| uploadedAt | string | 上传时间（ISO 8601 格式） |

**错误响应：**

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | INVALID_PUBLIC_KEY | 公钥格式无效 |
| 400 | INVALID_KEY_SIZE | 公钥大小不正确 |
| 401 | UNAUTHORIZED | 未认证或 Token 无效 |

---

## 版本协商

系统使用以下版本协商矩阵自动选择协议版本：

| 发送方 | 接收方 | 使用协议 | 说明 |
|--------|--------|----------|------|
| v2.0 RSA | v2.0 RSA | v2.0 RSA | 双方都只有 RSA |
| v3.0 X25519 | v2.0 RSA | v2.0 RSA | 回退到 RSA |
| v2.0 RSA | v3.0 X25519 | v2.0 RSA | 发送方只有 RSA |
| v3.0 X25519 | v3.0 X25519 | v3.0 X25519 | 双方都支持 X25519 |

**协商逻辑：**

1. 查询接收方的密钥信息（`GET /v1/users/{userId}/keys`）
2. 检查接收方是否有 X25519 公钥
3. 如果双方都有 X25519 公钥，使用 v3.0
4. 否则回退到 v2.0

---

## 密钥格式

### RSA 公钥 (v2.0)

- **算法**: RSA-2048
- **格式**: X.509 编码（Base64）
- **大小**: 约 256 字节
- **用途**: RSA-OAEP 加密、SHA256withRSA 签名

### X25519 公钥 (v3.0)

- **算法**: Curve25519 ECDH
- **格式**: 原始 32 字节（Base64 编码）
- **大小**: 32 字节
- **用途**: ECDH 密钥交换

### Ed25519 公钥 (v3.0)

- **算法**: Curve25519 EdDSA
- **格式**: 原始 32 字节（Base64 编码）
- **大小**: 32 字节
- **用途**: Ed25519 数字签名

---

## 安全注意事项

1. **公钥验证**: 客户端应验证公钥大小是否正确（X25519/Ed25519: 32 字节）
2. **版本混淆**: 使用 `keyVersion` 字段验证对方的密钥版本
3. **TLS 加密**: 所有 API 调用必须通过 HTTPS
4. **Token 验证**: 服务器应验证 JWT Token 的有效性和权限

---

## 数据库 Schema

```sql
-- users 表相关字段
ALTER TABLE users ADD COLUMN x25519_public_key TEXT;
ALTER TABLE users ADD COLUMN ed25519_public_key TEXT;
ALTER TABLE users ADD COLUMN key_version VARCHAR(10) DEFAULT 'v1';
CREATE INDEX idx_users_key_version ON users(key_version);
```

---

## 相关文档

- [安全架构文档](../security-architecture.md)
- [联系人分享 API](contact-sharing.md)
- [加密算法迁移指南](../guides/crypto-migration.md)

---

**版本**: 1.0.0
**最后更新**: 2026-03-03
**状态**: 稳定