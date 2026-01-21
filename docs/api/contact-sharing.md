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

## 安全说明

1. **好友验证**: 分享前会验证双方是否为好友关系（状态为 ACCEPTED）
2. **权限控制**: 支持细粒度权限设置（查看、保存、撤销）
3. **过期机制**: 所有分享都有过期时间，默认24小时
4. **加密传输**: 密码数据始终以加密形式传输和存储
5. **撤销保护**: 不可撤销的分享不能被撤销

---

**版本**: 2.2.0
**最后更新**: 2026-01-22
**重要变更**: 简化为仅支持联系人分享
