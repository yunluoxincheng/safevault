# 设计文档：安全规格修复

## 背景

`security-hardening-phase3` 变更（2026-02-07归档）实现了以下安全加固：
1. JWT从HS256升级到RS256
2. Token过期时间调整（15分钟/7天）
3. 完整HMAC签名验证
4. API速率限制
5. 并发登录控制

由于原始变更的delta文件使用了 `MODIFIED` 操作，但目标规格中不存在相应标题，导致规格未能更新。

## 问题分析

### 原始 delta 配置错误

| 规格 | 操作 | 标题 | 状态 |
|------|------|------|------|
| android-security | MODIFIED | JWT Token Algorithm | ❌ 不存在 |
| android-security | MODIFIED | Session Timeout | ✅ 存在 |
| backend-security | MODIFIED | JWT Signature Algorithm | ❌ 不存在 |
| backend-security | MODIFIED | Token Expiration Time | ❌ 不存在 |
| backend-security | MODIFIED | HMAC Signature Verification | ❌ 不存在 |
| auth-security | CREATE | (新规格) | ✅ 正确 |

## 解决方案

创建新变更 `spec-security-fix`，将所有操作改为 `ADDED`（auth-security 除外，保持 CREATE）。

### 修正后的配置

| 规格 | 操作 | 标题 | 说明 |
|------|------|------|------|
| android-security | ADDED | JWT Token Algorithm | 添加新的Token算法要求 |
| android-security | ADDED | Session Timeout (Updated) | 更新会话超时时间 |
| android-security | ADDED | Device Management | 添加设备管理要求 |
| android-security | ADDED | Auto Token Refresh | 添加自动刷新要求 |
| android-security | ADDED | Biometric Re-authentication | 添加生物识别重认证 |
| backend-security | ADDED | JWT Signature Algorithm | 添加JWT算法要求 |
| backend-security | ADDED | Token Expiration Time | 添加Token过期时间要求 |
| backend-security | ADDED | HMAC Signature Verification | 添加HMAC验证要求 |
| backend-security | ADDED | API Rate Limiting | 添加速率限制要求 |
| backend-security | ADDED | Concurrent Login Control | 添加并发控制要求 |
| auth-security | CREATE | (新规格) | 创建认证安全规格 |

## 实施计划

1. 验证变更结构有效性
2. 执行归档操作
3. 验证规格更新成功

## 预期结果

归档后，以下规格将包含新的安全要求：

### android-security
- JWT Token Algorithm
- Device Management
- Auto Token Refresh
- Biometric Re-authentication

### backend-security
- JWT Signature Algorithm
- Token Expiration Time
- HMAC Signature Verification
- API Rate Limiting
- Concurrent Login Control
- Device Management API
- Rate Limiting Configuration

### auth-security (新规格)
- JWT Algorithm Security
- Token Lifecycle Management
- Multi-Device Session Management
- Authentication Attempt Rate Limiting
- Signature-Based Request Authentication
- Secure Credential Storage
- Authentication Event Logging
- Secure Token Transmission
- Token Revocation
- Cross-Site Request Forgery Protection
