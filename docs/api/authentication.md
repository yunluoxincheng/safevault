# 认证 API 文档

## 概述

认证 API 提供基于邮箱的用户注册、登录、登出等功能。采用两步注册流程：先验证邮箱，再设置密码和密钥。

**Base URL**: `http://localhost:8080/api`

**认证方式**: JWT Bearer Token 或 X-User-Id Header

**注册状态追踪**: 系统会追踪用户注册状态，超时未完成注册的用户会被自动清理（默认5分钟）。

---

## 1. 邮箱注册（第一步）

### 请求

```http
POST /v1/auth/register-email
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "johndoe"
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 用户邮箱地址 |
| username | string | 是 | 用户名（3-20字符） |

### 响应

**成功 (200 OK)**

```json
{
  "message": "验证邮件已发送",
  "email": "user@example.com",
  "emailSent": true,
  "expiresInSeconds": 600
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| message | string | 提示消息 |
| email | string | 注册的邮箱 |
| emailSent | boolean | 邮件是否发送成功 |
| expiresInSeconds | long | 验证码过期时间（秒） |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | INVALID_EMAIL | 邮箱格式无效 |
| 409 | EMAIL_ALREADY_EXISTS | 邮箱已被注册 |
| 409 | USERNAME_ALREADY_EXISTS | 用户名已被占用 |

---

## 2. 验证邮箱

### 请求

```http
POST /v1/auth/verify-email
Content-Type: application/json

{
  "token": "123456"
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| token | string | 是 | 邮箱中的6位验证码 |

### 响应

**成功 (200 OK)**

```json
{
  "success": true,
  "message": "邮箱验证成功",
  "email": "user@example.com",
  "username": "johndoe"
}
```

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | INVALID_TOKEN | 验证码格式无效 |
| 400 | TOKEN_EXPIRED | 验证码已过期 |
| 400 | TOKEN_MISMATCH | 验证码错误 |

---

## 3. 重发验证邮件

### 请求

```http
POST /v1/auth/resend-verification
Content-Type: application/json

{
  "email": "user@example.com"
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 注册时使用的邮箱 |

### 响应

**成功 (200 OK)**

```json
{
  "message": "验证邮件已重新发送",
  "email": "user@example.com",
  "emailSent": true,
  "expiresInSeconds": 600
}
```

---

## 4. 完成注册（第二步）

### 请求

```http
POST /v1/auth/complete-registration
Content-Type: application/json

{
  "email": "user@example.com",
  "username": "johndoe",
  "passwordVerifier": "BASE64_ENCODED_VERIFIER",
  "salt": "BASE64_ENCODED_SALT",
  "publicKey": "BASE64_ENCODED_PUBLIC_KEY",
  "encryptedPrivateKey": "BASE64_ENCODED_PRIVATE_KEY",
  "privateKeyIv": "BASE64_ENCODED_IV",
  "deviceId": "unique-device-id"
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 已验证的邮箱 |
| username | string | 是 | 用户名 |
| passwordVerifier | string | 是 | SRP 密码验证器（Base64） |
| salt | string | 是 | 密码盐值（Base64） |
| publicKey | string | 是 | 用户公钥（Base64） |
| encryptedPrivateKey | string | 是 | 加密的私钥（Base64） |
| privateKeyIv | string | 是 | 私钥加密 IV（Base64） |
| deviceId | string | 是 | 设备唯一标识符 |

### 响应

**成功 (200 OK)**

```json
{
  "success": true,
  "message": "注册成功",
  "userId": "usr_abc123xyz",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "displayName": "johndoe"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 是否成功 |
| message | string | 提示消息 |
| userId | string | 用户ID |
| accessToken | string | JWT 访问令牌 |
| refreshToken | string | JWT 刷新令牌 |
| displayName | string | 显示名称 |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | EMAIL_NOT_VERIFIED | 邮箱未验证 |
| 400 | INVALID_REGISTRATION_STATUS | 注册状态无效（用户不在 EMAIL_VERIFIED 状态） |
| 400 | REGISTRATION_TIMEOUT | 注册超时（用户验证邮箱后超过配置时间未完成注册） |
| 400 | REGISTRATION_ALREADY_COMPLETED | 注册已完成，请直接登录 |
| 400 | INVALID_CRYPTO_DATA | 加密数据无效 |

---

## 5. 邮箱登录

### 请求

```http
POST /v1/auth/login-by-email
Content-Type: application/json

{
  "email": "user@example.com",
  "deviceId": "unique-device-id",
  "deviceName": "My Phone",
  "derivedKeySignature": "SIGNATURE_FROM_DERIVED_KEY",
  "timestamp": 1704067200000,
  "deviceType": "android",
  "osVersion": "Android 10"
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 用户邮箱 |
| deviceId | string | 是 | 设备唯一标识符 |
| deviceName | string | 是 | 设备名称 |
| derivedKeySignature | string | 是 | 派生密钥签名 |
| timestamp | Long | 是 | 时间戳（毫秒） |
| deviceType | string | 是 | 设备类型（android/ios/web） |
| osVersion | string | 是 | 操作系统版本 |

### 响应

**成功 (200 OK)**

```json
{
  "userId": "usr_abc123xyz",
  "email": "user@example.com",
  "username": "johndoe",
  "displayName": "John Doe",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 86400,
  "emailVerified": true,
  "devices": [
    {
      "deviceId": "device-001",
      "deviceName": "My Phone",
      "deviceType": "android",
      "lastLogin": "2026-01-14T10:30:00Z"
    }
  ],
  "isNewDevice": false,
  "message": "登录成功"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | string | 用户ID |
| email | string | 用户邮箱 |
| username | string | 用户名 |
| displayName | string | 显示名称 |
| accessToken | string | JWT 访问令牌 |
| refreshToken | string | JWT 刷新令牌 |
| expiresIn | Long | 令牌过期时间（秒） |
| emailVerified | boolean | 邮箱是否已验证 |
| devices | array | 用户设备列表 |
| isNewDevice | boolean | 是否为新设备 |
| message | string | 提示消息 |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 401 | INVALID_CREDENTIALS | 邮箱或签名错误 |
| 401 | EMAIL_NOT_VERIFIED | 邮箱未验证 |

---

## 6. 登出

### 请求

```http
POST /v1/auth/logout
X-User-Id: usr_abc123xyz
Content-Type: application/json

{
  "deviceId": "unique-device-id",
  "timestamp": 1704067200000
}
```

### 请求参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | string | 是 | 设备ID |
| timestamp | Long | 是 | 时间戳（毫秒） |

### 响应

**成功 (200 OK)**

```json
{
  "success": true,
  "message": "登出成功"
}
```

---

## 通用错误响应格式

```json
{
  "errorCode": "ERROR_CODE",
  "message": "错误描述",
  "timestamp": 1704067200000
}
```

---

## 认证方式

### Bearer Token

```http
GET /v1/protected-endpoint
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### User ID Header

```http
GET /v1/protected-endpoint
X-User-Id: usr_abc123xyz
```

---

## 流程图

### 注册流程

```
用户输入邮箱 → 发送验证邮件 → 输入验证码
     ↓
验证成功 → 设置密码 → 生成密钥对 → 完成注册

注册状态转换：
Redis (PendingUser) → 验证邮箱 → DB (EMAIL_VERIFIED) → 完成注册 → DB (ACTIVE)
                               ↓                    ↓
                         10分钟过期              5分钟超时清理
```

### 注册状态说明

| 状态 | 说明 | 超时处理 |
|------|------|----------|
| EMAIL_VERIFIED | 邮箱已验证，等待设置密码 | 验证后5分钟内未完成注册将被自动删除 |
| ACTIVE | 注册完成，可以正常使用 | 无超时限制 |

### 登录流程

```
输入邮箱 → 生成派生密钥 → 计算签名 → 登录
     ↓
返回 Token + 设备列表
```

---

**版本**: 1.1.0
**最后更新**: 2026-02-03
**变更**:
- 新增注册状态追踪功能
- 新增 `REGISTRATION_TIMEOUT` 和 `INVALID_REGISTRATION_STATUS` 错误码
- 添加注册状态说明和超时清理机制
