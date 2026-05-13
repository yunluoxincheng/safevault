# 设备管理 API 文档

## 概述

设备管理 API 提供用户设备列表查询、设备移除、账户删除等功能。

**Base URL**: `http://localhost:8080/api`

**认证方式**: X-User-Id Header 或 Authorization Bearer Token

---

## DeviceInfo 数据结构

```json
{
  "deviceId": "unique-device-id",
  "deviceName": "My Phone",
  "deviceType": "android",
  "osVersion": "Android 10",
  "lastActiveAt": "2026-01-14T10:30:00Z",
  "createdAt": "2026-01-01T08:00:00Z",
  "isCurrentDevice": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | string | 设备唯一标识符 |
| deviceName | string | 设备名称 |
| deviceType | string | 设备类型（android/ios/web） |
| osVersion | string | 操作系统版本 |
| lastActiveAt | string | 最后活跃时间（ISO 8601） |
| createdAt | string | 设备创建时间（ISO 8601） |
| isCurrentDevice | boolean | 是否为当前设备 |

---

## 1. 获取设备列表

### 请求

```http
GET /v1/auth/devices
X-User-Id: usr_abc123xyz
```

### 响应

**成功 (200 OK)**

```json
{
  "devices": [
    {
      "deviceId": "device-001",
      "deviceName": "My Phone",
      "deviceType": "android",
      "osVersion": "Android 10",
      "lastActiveAt": "2026-01-14T10:30:00Z",
      "createdAt": "2026-01-01T08:00:00Z",
      "isCurrentDevice": true
    },
    {
      "deviceId": "device-002",
      "deviceName": "Work Laptop",
      "deviceType": "web",
      "osVersion": "Windows 11",
      "lastActiveAt": "2026-01-13T15:20:00Z",
      "createdAt": "2026-01-05T09:00:00Z",
      "isCurrentDevice": false
    }
  ],
  "totalDevices": 2
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| devices | array | 设备列表 |
| totalDevices | int | 设备总数 |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 401 | UNAUTHORIZED | 用户未认证 |

---

## 2. 移除设备

### 请求

```http
DELETE /v1/auth/devices/{deviceId}
X-User-Id: usr_abc123xyz
```

### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | string | 是 | 要移除的设备ID |

### 响应

**成功 (200 OK)**

```json
{
  "success": true,
  "message": "设备已移除",
  "removedDeviceId": "device-002"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 是否成功 |
| message | string | 提示消息 |
| removedDeviceId | string | 被移除的设备ID |

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 400 | CANNOT_REMOVE_CURRENT_DEVICE | 不能移除当前设备 |
| 404 | DEVICE_NOT_FOUND | 设备不存在 |

---

## 3. 删除账户

### 请求

```http
DELETE /v1/account
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### 响应

**成功 (200 OK)**

```json
{
  "success": true,
  "message": "账户已删除"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 是否成功 |
| message | string | 提示消息 |

### 说明

- 此操作会永久删除用户账户及所有关联数据
- 删除后无法恢复
- 建议在前端进行二次确认

### 错误响应

| HTTP 状态码 | 错误码 | 说明 |
|------------|--------|------|
| 401 | UNAUTHORIZED | 用户未认证 |
| 403 | FORBIDDEN | 权限不足 |

---

## 使用场景

### 场景 1：查看所有登录设备

用户在"账户安全"页面查看所有已登录的设备列表。

```
前端调用 GET /v1/auth/devices
→ 显示设备列表（设备名、类型、最后活跃时间）
→ 标识当前设备
```

### 场景 2：移除陌生设备

用户发现不认识的设备，选择移除。

```
前端调用 DELETE /v1/auth/devices/{deviceId}
→ 显示确认对话框
→ 移除设备
→ 刷新设备列表
```

### 场景 3：注销账户

用户决定删除账户及所有数据。

```
前端调用 DELETE /v1/account
→ 二次确认（输入密码或确认）
→ 删除账户
→ 跳转到注册页
```

---

## 安全注意事项

1. **设备移除限制**：不允许移除当前登录的设备
2. **账户删除不可逆**：前端必须进行二次确认
3. **权限验证**：所有操作需要验证用户身份
4. **审计日志**：敏感操作应记录审计日志

---

## 错误码参考

| 错误码 | HTTP 状态 | 说明 |
|--------|---------|------|
| UNAUTHORIZED | 401 | 用户未认证或令牌无效 |
| DEVICE_NOT_FOUND | 404 | 设备不存在 |
| CANNOT_REMOVE_CURRENT_DEVICE | 400 | 不能移除当前设备 |
| FORBIDDEN | 403 | 权限不足 |

---

**版本**: 1.0.0
**最后更新**: 2026-01-14
