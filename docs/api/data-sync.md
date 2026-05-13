# 数据同步 API 文档

## 概述

数据同步 API 提供密码库云端存储、私钥备份、多设备同步等功能。所有数据采用端到端加密，服务端无法解密。

**Base URL**: `http://localhost:8080/api`

**认证方式**: X-User-Id Header

**加密算法**: AES-256-GCM

---

## 1. 上传私钥

### 请求

```http
POST /v1/vault/private-key
X-User-Id: usr_abc123xyz
Content-Type: application/json

{
  "encryptedPrivateKey": "BASE64_ENCODED_PRIVATE_KEY",
  "iv": "BASE64_ENCODED_IV",
  "salt": "BASE64_ENCODED_SALT",
  "version": "1.0"
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| encryptedPrivateKey | string | 是 | 加密的私钥（Base64） |
| iv | string | 是 | 加密初始化向量（Base64） |
| salt | string | 是 | 派生密钥盐值（Base64） |
| version | string | 是 | 版本号 |

### 响应

**成功 (200 OK)**

```json
{
  "success": true,
  "version": "1.0",
  "uploadedAt": "2026-01-14T10:30:00Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 是否成功 |
| version | string | 版本号 |
| uploadedAt | string | 上传时间（ISO 8601） |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | INVALID_ENCRYPTED_DATA | 加密数据无效 |
| 401 | UNAUTHORIZED | 用户未认证 |

---

## 2. 获取私钥

### 请求

```http
GET /v1/vault/private-key
X-User-Id: usr_abc123xyz
```

### 响应

**成功 (200 OK)**

```json
{
  "encryptedPrivateKey": "BASE64_ENCODED_PRIVATE_KEY",
  "iv": "BASE64_ENCODED_IV",
  "salt": "BASE64_ENCODED_SALT",
  "version": "1.0",
  "updatedAt": "2026-01-14T10:30:00Z"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| encryptedPrivateKey | string | 加密的私钥（Base64） |
| iv | string | 加密初始化向量（Base64） |
| salt | string | 派生密钥盐值（Base64） |
| version | string | 版本号 |
| updatedAt | string | 最后更新时间（ISO 8601） |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 404 | PRIVATE_KEY_NOT_FOUND | 私钥不存在 |

---

## 3. 密码库同步

### 请求

```http
POST /v1/vault/sync
X-User-Id: usr_abc123xyz
Content-Type: application/json

{
  "encryptedData": "BASE64_ENCODED_VAULT_DATA",
  "dataIv": "BASE64_ENCODED_IV",
  "dataAuthTag": "BASE64_ENCODED_AUTH_TAG",
  "clientVersion": 5,
  "forceSync": false
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| encryptedData | string | 是 | 加密的密码库数据（Base64） |
| dataIv | string | 是 | 加密初始化向量（Base64） |
| dataAuthTag | string | 是 | GCM 认证标签（Base64） |
| clientVersion | Long | 是 | 客户端版本号 |
| forceSync | boolean | 是 | 强制同步（true = 覆盖服务器数据） |

### 响应

**成功 - 无冲突 (200 OK)**

```json
{
  "success": true,
  "hasConflict": false,
  "serverVersion": 5,
  "clientVersion": 5,
  "newVersion": 6,
  "vault": {
    "vaultId": "vault_abc123",
    "userId": "usr_abc123xyz",
    "encryptedData": "BASE64_ENCODED_DATA",
    "dataIv": "BASE64_ENCODED_IV",
    "dataAuthTag": "BASE64_ENCODED_AUTH_TAG",
    "version": 6,
    "lastSyncedAt": "2026-01-14T10:30:00Z",
    "createdAt": "2026-01-01T08:00:00Z",
    "updatedAt": "2026-01-14T10:30:00Z"
  },
  "lastSyncedAt": "2026-01-14T10:30:00Z"
}
```

**响应 - 有冲突 (409 Conflict)**

```json
{
  "success": false,
  "hasConflict": true,
  "conflictMessage": "服务器版本更新，请先拉取最新数据",
  "serverVersion": 7,
  "clientVersion": 5,
  "newVersion": null,
  "serverVault": {
    "vaultId": "vault_abc123",
    "userId": "usr_abc123xyz",
    "encryptedData": "BASE64_ENCODED_SERVER_DATA",
    "dataIv": "BASE64_ENCODED_IV",
    "dataAuthTag": "BASE64_ENCODED_AUTH_TAG",
    "version": 7,
    "lastSyncedAt": "2026-01-14T10:25:00Z",
    "createdAt": "2026-01-01T08:00:00Z",
    "updatedAt": "2026-01-14T10:25:00Z"
  },
  "lastSyncedAt": "2026-01-14T10:25:00Z"
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 同步是否成功 |
| hasConflict | boolean | 是否存在版本冲突 |
| conflictMessage | string | 冲突描述（有冲突时） |
| serverVersion | Long | 服务器版本号（同步前） |
| clientVersion | Long | 客户端版本号（同步前） |
| newVersion | Long | 新版本号（同步后） |
| vault | object | 同步后的密码库数据 |
| serverVault | object | 服务器数据（冲突时） |
| lastSyncedAt | string | 最后同步时间 |

### VaultResponse 数据结构

| 字段 | 类型 | 说明 |
|------|------|------|
| vaultId | string | 密码库ID |
| userId | string | 用户ID |
| encryptedData | string | 加密的密码库数据（Base64） |
| dataIv | string | 加密初始化向量（Base64） |
| dataAuthTag | string | GCM 认证标签（Base64） |
| version | Long | 版本号 |
| lastSyncedAt | string | 最后同步时间（ISO 8601） |
| createdAt | string | 创建时间（ISO 8601） |
| updatedAt | string | 更新时间（ISO 8601） |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | INVALID_ENCRYPTED_DATA | 加密数据无效 |
| 409 | VERSION_CONFLICT | 版本冲突（需要先拉取） |

---

## 4. 获取密码库

### 请求

```http
GET /v1/vault
X-User-Id: usr_abc123xyz
```

### 响应

**成功 (200 OK)**

```json
{
  "vaultId": "vault_abc123",
  "userId": "usr_abc123xyz",
  "encryptedData": "BASE64_ENCODED_DATA",
  "dataIv": "BASE64_ENCODED_IV",
  "dataAuthTag": "BASE64_ENCODED_AUTH_TAG",
  "version": 5,
  "lastSyncedAt": "2026-01-14T10:30:00Z",
  "createdAt": "2026-01-01T08:00:00Z",
  "updatedAt": "2026-01-14T10:30:00Z"
}
```

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 404 | VAULT_NOT_FOUND | 密码库不存在 |

---

## 同步流程

### 正常同步流程

```
1. 客户端本地修改密码库 → version++
2. 客户端调用 POST /v1/vault/sync
3. 服务器验证 clientVersion == serverVersion
4. 服务器保存数据，version++
5. 返回新数据给客户端
```

### 冲突解决流程

```
1. 客户端调用 POST /v1/vault/sync
2. 服务器发现 clientVersion < serverVersion
3. 返回 409 Conflict + serverVault
4. 客户端下载 serverVault
5. 客户端合并两份数据
6. 客户端重新调用 POST /v1/vault/sync（合并后的数据）
```

### 强制同步流程

```
1. 客户端设置 forceSync = true
2. 服务器忽略版本检查
3. 服务器直接覆盖数据
4. 返回新数据给客户端

注意：强制同步会丢失服务器上的数据！
```

---

## 版本控制

### 版本号规则

- 版本号从 1 开始，每次同步递增
- 客户端和服务端各自维护版本号
- 同步成功后，两者版本号一致

### 冲突检测

```javascript
// 客户端逻辑
if (clientVersion < serverVersion) {
    // 需要先拉取服务器数据
    showConflictDialog();
} else if (clientVersion === serverVersion) {
    // 正常同步
    syncVault();
} else {
    // 异常情况，客户端版本号不应该大于服务器
    reportError();
}
```

---

## 安全设计

### 端到端加密

1. **客户端加密**：密码库数据在客户端使用 AES-256-GCM 加密
2. **服务端存储**：服务端只存储加密后的数据，无法解密
3. **密钥管理**：加密密钥由用户密码派生，不存储在服务端

### 数据完整性

1. **GCM 认证标签**：验证数据未被篡改
2. **版本控制**：防止数据覆盖
3. **签名验证**：私钥操作需要签名验证

### 私钥备份

1. **加密存储**：私钥使用用户密码加密后存储
2. **多设备同步**：新设备可以下载私钥备份
3. **版本管理**：支持私钥版本更新

---

## 使用场景

### 场景 1：首次登录

```
1. 用户在设备A登录
2. 客户端生成密钥对
3. 调用 POST /v1/vault/private-key 备份私钥
4. 调用 POST /v1/vault/sync 上传初始密码库
```

### 场景 2：多设备同步

```
1. 用户在设备B登录
2. 调用 GET /v1/vault/private-key 获取私钥
3. 调用 GET /v1/vault 获取密码库
4. 本地解密和使用
```

### 场景 3：冲突处理

```
1. 用户在设备A和设备B同时修改
2. 设备A先同步成功
3. 设备B同步时收到 409 Conflict
4. 设备B下载服务器数据
5. 用户选择合并策略
6. 重新同步合并后的数据
```

---

## 错误码参考

| 错误码 | HTTP 状态 | 说明 |
|--------|---------|------|
| UNAUTHORIZED | 401 | 用户未认证 |
| INVALID_ENCRYPTED_DATA | 400 | 加密数据无效 |
| VAULT_NOT_FOUND | 404 | 密码库不存在 |
| PRIVATE_KEY_NOT_FOUND | 404 | 私钥不存在 |
| VERSION_CONFLICT | 409 | 版本冲突 |

---

**版本**: 1.0.0
**最后更新**: 2026-01-14
