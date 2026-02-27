# Change: 安全规格修复 - 添加第三阶段安全要求

## Why

`security-hardening-phase3` 变更已归档，但由于 delta 配置问题（使用了 MODIFIED 但目标标题不存在），规格文件未能更新。需要创建新变更来正确添加这些安全要求。

## What Changes

### 修复 android-security 规格
- 将 `JWT Token Algorithm` 作为新要求添加（使用 ADDED）
- 添加 `Device Management` 要求
- 添加 `Auto Token Refresh` 要求
- 添加 `Biometric Re-authentication` 要求

### 修复 backend-security 规格
- 将 `JWT Signature Algorithm` 作为新要求添加（使用 ADDED）
- 将 `Token Expiration Time` 作为新要求添加（使用 ADDED）
- 将 `HMAC Signature Verification` 作为新要求添加（使用 ADDED）
- 添加 `API Rate Limiting` 要求
- 添加 `Concurrent Login Control` 要求

### 创建 auth-security 规格
- 创建新的认证安全规格 (CREATE)
- 包含所有认证相关的安全要求
- 规格文件将直接创建而非使用 delta

## Impact

### 受影响的规格
- `android-security` - 添加 Token 管理要求
- `backend-security` - 添加 JWT 和认证安全要求
- `auth-security` - 创建新规格（认证安全）

## Non-Breaking Changes

- 仅添加新的安全要求
- 不修改现有要求
- 不影响现有代码
