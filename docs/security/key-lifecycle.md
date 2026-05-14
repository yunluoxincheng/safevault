# SafeVault 密钥生命周期规范

> 本文档定义 SafeVault 的密钥生成时序、会话状态机和密钥销毁规则。
> 基于 stabilize-auth-key-lifecycle OpenSpec 变更。

## 1. 密钥层级

| 密钥 | 类型 | 生成时机 | 存储位置 | 用途 |
|------|------|---------|---------|------|
| PasswordKey | 临时 AES-256 | 注册/解锁时从主密码派生 | 仅内存 | 加密 DataKey |
| DataKey | AES-256 | 注册时随机生成 | 内存（UNLOCKED 时）+ 双重加密存储 | 加密密码库条目 |
| DeviceKey | AES-256 SecretKey | 生物识别 enrollment 时 | AndroidKeyStore | 快速解锁 DataKey |
| RSA Keypair | RSA-2048 | 注册时生成 | 公钥明文 + 私钥 DataKey 加密存储 | 密码分享 |
| JWT Access Token | RS256 | 登录/刷新时签发 | SharedPreferences | API 认证 |
| JWT Refresh Token | RS256 + jti/family | 登录/刷新时签发 | SharedPreferences | Token 续期 |

## 2. 密钥生成时序（注册流程）

```
1. salt = generateSalt()                              ← 16 字节随机盐
2. PasswordKey = Argon2id(password, salt)             ← Argon2id 派生
3. DataKey = generateDataKey()                        ← 随机 256-bit AES
4. RSA Keypair = generateRSAKeyPair()                 ← RSA-2048
5. DeviceKey = getOrCreateDeviceKey()                 ← AndroidKeyStore AES-256
6. encryptAndSaveDataKey(DataKey, PasswordKey, DeviceKey)  ← 双重加密
7. encryptAndSaveRsaPrivateKey(RSAPrivate, DataKey)        ← DataKey 加密 RSA 私钥
8. SessionGuard.unlockWithDataKey(DataKey)                  ← 进入 UNLOCKED
9. secureWipe(PasswordKey)                                  ← 清除 PasswordKey 明文
```

## 3. 会话状态机

```
UNINITIALIZED ──→ UNLOCKED (注册流程直达)
     │
     ↓ (异常路径)
 REGISTERED ──→ LOGGED_IN ──→ UNLOCKED ──→ LOCKED ──→ LOGGED_OUT
                                ↑              │
                                └──────────────┘ (重新解锁)
```

| 状态 | 含义 | DataKey 在内存 | JWT 有效 |
|------|------|---------------|---------|
| UNINITIALIZED | 未注册 | 否 | 否 |
| REGISTERED | 有密钥材料但无 JWT | 否 | 否 |
| LOGGED_IN | 有 JWT 但 DataKey 未加载 | 否 | 是 |
| UNLOCKED | DataKey 在内存中 | 是 | 是 |
| LOCKED | DataKey 已清除 | 否 | 是（可能） |
| LOGGED_OUT | 已登出 | 否 | 否 |

### 合法状态转换

| 从 | 到 | 触发条件 |
|----|----|---------|
| UNINITIALIZED | UNLOCKED | 注册成功 |
| UNINITIALIZED | REGISTERED | 密钥初始化完成但 JWT 获取失败 |
| REGISTERED | LOGGED_IN | 登录获取 JWT |
| REGISTERED | UNLOCKED | 登录 + 解锁（快速路径） |
| LOGGED_IN | UNLOCKED | 主密码验证成功，DataKey 加载 |
| LOGGED_IN | LOGGED_OUT | 登出 |
| UNLOCKED | LOCKED | 后台超时/手动锁定/内存压力 |
| UNLOCKED | LOGGED_OUT | 主动登出 |
| LOCKED | UNLOCKED | 主密码或生物识别重新解锁 |
| LOCKED | LOGGED_OUT | JWT 过期或主动登出 |
| LOGGED_OUT | LOGGED_IN | 重新登录 |
| LOGGED_OUT | UNLOCKED | 重新登录 + 解锁（快速路径） |

## 4. 生物识别前置条件

**ENROLLMENT**（首次启用）：
- 仅需 PasswordKeyEncryptedDataKey 存在（主密码路径完整）
- 不需要 DeviceKey 已存在（enrollment 会创建新 DeviceKey）

**UNLOCK**（生物识别解锁）：
- DeviceKey 存在且未失效
- DeviceKeyEncryptedDataKey 存在

## 5. 密钥销毁规则

| 密钥 | 销毁时机 | 销毁方式 |
|------|---------|---------|
| PasswordKey 明文 | DataKey 加密/解密后 | MemorySanitizer.secureWipe() |
| DataKey 明文 | 会话锁定/登出/内存压力 | SensitiveData.close() |
| DeviceKey | 用户撤销或 enrollment 重新生成 | AndroidKeyStore delete |
| JWT/Refresh Token | 登出 | 本地清除 + 服务端撤销 |

## 6. Refresh Token 轮换

### 数据模型

每次签发 refresh token 创建 `RefreshTokenRecord`：
- `jti`: token 唯一标识
- `family`: token family 标识（首次签发时 family = jti）
- `rotated`: 是否已被轮换替换
- `revoked`: 是否已撤销

### 轮换流程

1. 客户端调用 `/auth/refresh` 提供有效 refresh token
2. 后端验证签名和过期时间
3. 查找 token 记录，检查是否已 rotated
4. 如已 rotated → **重用检测**：撤销整个 family
5. 如未 rotated → 标记旧 token 为 rotated，签发新 token（同 family，新 jti）
6. 客户端用 `commit()` 同步保存新 token

### 重用检测

当 rotated=true 的 token 被重用时：
- 撤销该 family 下所有 refresh token
- 返回 401 错误
- 客户端清除本地 token 并跳转登录页

### RevokedToken vs RefreshTokenRecord

- **RevokedToken**: access token denylist（登出时拒绝旧 access token）
- **RefreshTokenRecord**: refresh token 生命周期追踪（轮换、重用检测、family 撤销）
