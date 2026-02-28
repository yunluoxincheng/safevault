# SafeVault 安全存储架构全面分析

## 一、架构概览

```
┌──────┬─────────────────────────────┬────────────────────────────────┐
│ 层级 │            前端             │              后端              │
├──────┼─────────────────────────────┼────────────────────────────────┤
│ 认证 │ Argon2id密钥派生 + 生物识别 │ JWT (RS256) + Argon2id密码哈希 │
├──────┼─────────────────────────────┼────────────────────────────────┤
│ 加密 │ AES-256-GCM + RSA-2048      │ 零知识存储（不解密）           │
├──────┼─────────────────────────────┼────────────────────────────────┤
│ 存储 │ AndroidKeyStore硬件保护     │ PostgreSQL + Redis             │
└──────┴─────────────────────────────┴────────────────────────────────┘
```

## 二、加密技术和算法

| 算法类型 | 用途 | 参数配置 |
|---------|------|----------|
| **Argon2id** | 密钥派生 (KDF) | 128MB内存, 3次迭代, 4并行, 输出256位, 盐值128位 |
| **AES-256-GCM** | 数据加密 | IV 96位，GCM Tag 128位 |
| **RSA-2048** | 密钥交换和签名 | OAEP填充，SHA256withRSA |
| **SHA-256** | 哈希计算 | - |

### Argon2id 配置详解

**前后端统一配置**
- 内存成本：131072KB (128MB)
- 迭代次数 (timeCost)：3
- 并行度 (parallelism)：4
- 输出长度：32 字节 (256 位)
- 盐值长度：16 字节 (128 位)

## 三、三层安全架构

```
┌─────────────────────────────────────────┐
│ Level 3: RSA 私钥层（被 DataKey 加密）   │
└──────────────────▲──────────────────────┘
                       │ DataKey
┌──────────────────┴──────────────────────┐
│ Level 2: DataKey 层（双重加密）          │
│ ① PasswordKey 加密 → 可跨设备恢复         │
│ ② DeviceKey 加密 → 本机快速解锁          │
└──────────────────▲──────────────────────┘
                       │
┌──────────────────┴──────────────────────┐
│ Level 1: 根层（密钥派生）                 │
│ PasswordKey: Argon2id(主密码+salt)      │
│ DeviceKey: AndroidKeyStore (生物识别)   │
└─────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────┐
│ Level 0: 会话管理层 (CryptoSession)      │
│ - DataKey 内存缓存                        │
│ - 5分钟超时自动锁定                       │
│ - SensitiveData 包装，自动清零           │
│ - 会话状态检查（isUnlocked）             │
└─────────────────────────────────────────┘
```

### 三层架构详解

#### Level 1: 根层（Root Keys）

**PasswordKey（密码派生密钥）**
- **生成方式**: Argon2id(主密码 + salt)
- **用途**: 加密 DataKey（可跨设备恢复版本）
- **特点**: 主密码相同，派生出的密钥必相同
- **参数**: 128MB 内存，3 次迭代，4 并行

**DeviceKey（设备密钥）**
- **存储位置**: AndroidKeyStore（硬件保护）
- **用途**: 加密 DataKey（本机快速解锁版本）
- **有效期**: 30秒（每次生物识别后重新获取）
- **特点**: 绑定设备，支持生物识别

#### Level 2: DataKey 层

**DataKey（数据加密密钥）**
- **类型**: 随机生成的256位密钥
- **存储方式**: 双重加密
  - 用 PasswordKey 加密 → 存储在 SharedPreferences（可云端备份）
  - 用 DeviceKey 加密 → 存储在 SharedPreferences（仅本地）
- **缓存**: CryptoSession（内存，5分钟超时）
- **用途**: 加密所有用户数据和 RSA 私钥

#### Level 3: RSA 私钥层

**RSA 密钥对**
- **算法**: RSA-2048
- **用途**: 密码分享、数字签名
- **存储**:
  - 私钥: 被 DataKey 加密后存储
  - 公钥: 明文存储（用于分享加密）

## 四、注册/登录/解锁流程

### 用户注册（本地初始化）

```
1. 用户输入主密码
   ↓
2. 生成随机 Salt (16字节)
   ↓
3. 派生 PasswordKey = Argon2id(主密码, salt)
   ↓
4. 生成随机 DataKey (256位)
   ↓
5. 生成 RSA-2048 密钥对
   ↓
6. 获取/创建 DeviceKey (AndroidKeyStore)
   ↓
7. 双重加密 DataKey 并保存
   • 用 PasswordKey 加密（云端备份）
   • 用 DeviceKey 加密（本地快速解锁）
   ↓
8. 用 DataKey 加密 RSA 私钥并保存
   ↓
9. 缓存 DataKey 到 CryptoSession
```

### 主密码解锁

```
1. 用户输入主密码
   ↓
2. 读取存储的 Salt
   ↓
3. 派生 PasswordKey = Argon2id(主密码, salt)
   ↓
4. 用 PasswordKey 解密 DataKey
   ↓
5. 缓存 DataKey 到 CryptoSession（会话解锁态，5分钟超时）
```

### 生物识别解锁

```
1. 用户触发生物识别认证
   ↓
2. 认证成功 → 获取 DeviceKey（30秒有效期）
   ↓
3. 用 DeviceKey 解密 DataKey
   ↓
4. 缓存 DataKey 到 CryptoSession（无需主密码）
```

### 云端登录流程

```
1. 用户输入邮箱和主密码
   ↓
2. 调用后端 API: POST /v1/auth/login-email
   请求: { email, masterPasswordHash }
   ↓
3. 后端验证（零知识）
   - 用户的加密私钥已存在？
   - 使用 Argon2id 验证主密码哈希
   ↓
4. 后端返回 JWT Token
   { accessToken, refreshToken, userId, ... }
   ↓
5. 前端保存 Token 到 TokenManager
   ↓
6. 下载加密的 RSA 私钥
   GET /v1/vault/private-key
   返回: { encryptedPrivateKey, iv, salt, authTag }
   ↓
7. 使用 BackupEncryptionManager 解密私钥
   privateKey = decryptCloudSync(..., masterPassword)
   ↓
8. 导入私钥到本地 SecureKeyStorageManager
   ↓
9. 派生或下载 DataKey
   - 如果是新设备: 生成新 DataKey 并上传
   - 如果是老设备: 从云端下载加密的 DataKey
   ↓
10. 缓存 DataKey 到 CryptoSession
   ↓
11. 进入 MainActivity（会话解锁态）
```

## 五、密码保存和加密流程

### 存储结构

```sql
CREATE TABLE encrypted_passwords (
    id INTEGER PRIMARY KEY,
    encrypted_title TEXT,      -- "v2:base64(IV):base64(密文)"
    encrypted_username TEXT,   -- 带安全随机填充
    encrypted_password TEXT,
    encrypted_url TEXT,
    encrypted_notes TEXT,
    updated_at INTEGER
);
```

### 加密流程（版本 2.0）

```
1. 检查会话状态（Guarded Execution 模式）
   ↓
2. 获取 DataKey
   ↓
3. 对每个字段进行：
   a) 明文转 UTF-8 字节
   b) 安全随机填充（块大小256字节，随机字节填充）
   c) 生成随机 IV (12字节)
   d) AES-256-GCM 加密
   e) 组装: "v2:" + base64(IV) + ":" + base64(密文)
   ↓
4. 保存到数据库
   ↓
5. 增加本地版本号
```

### 安全随机填充详解（SecurePaddingUtil）

**目的**: 防止通过密文长度推断明文长度的攻击

**填充算法**:

```
输入: "Gmail" (5 bytes)
     ↓
1. 计算填充长度
   blockSize = 256
   paddingLength = blockSize - (plaintextLength % blockSize)
   paddingLength = 256 - (5 % 256) = 251 bytes
     ↓
2. 生成随机填充字节
   padding = SecureRandom.nextBytes(251)
   // 注意：使用随机字节，不是 \0
     ↓
3. 最后1字节记录填充长度
   lastByte = 251 (0xFB)
     ↓
4. 组装
   paddedData = plaintext + padding + lastByte
   // 总长度: 5 + 250 + 1 = 256 bytes
     ↓
输出: 256 字节（密文长度恒定）
```

**去填充流程**:

```
输入: 256 bytes 密文解密后的数据
     ↓
1. 读取最后1字节
   paddingLength = data[255]  // = 251
     ↓
2. 计算明文长度
   plaintextLength = 256 - 1 - paddingLength  // = 5
     ↓
3. 提取明文
   plaintext = data[0..4]  // "Gmail"
     ↓
输出: "Gmail" (5 bytes)
```

### 数据流程示例

**示例: 加密码码条目标题 "Gmail"**

```
明文: "Gmail"
  ↓ (UTF-8 编码)
字节: [0x47, 0x6D, 0x61, 0x69, 0x6C]  // 5 字节
  ↓ (安全随机填充)
填充后: 256 字节（最后1字节 = 0xFB，表示填充251字节）
  ↓ (生成随机 IV)
IV: [0x3A, 0x7F, 0x9C, 0x12, 0x45, 0x88, 0x23, 0x11, 0x67, 0x99, 0x54, 0x32]
  ↓ (AES-256-GCM 加密)
密文: 272 字节（256 数据 + 16 authTag）
  ↓ (Base64 编码 + 组装)
结果: "v2:On/5cO6S12VohUiVWGmxEs:U2FsdGVkX1tJR3Z..."
  ↓ (存储到数据库)
encrypted_title: "v2:On/5cO6S12VohUiVWGmxEs:U2FsdGVkX1tJR3Z..."
```

### 解密流程

```
1. 解析格式: "v2:base64(IV):base64(密文)"
   ↓
2. Base64 解码 IV 和密文
   ↓
3. AES-256-GCM 解密（自动验证 authTag）
   ↓
4. 去除安全随机填充
   ↓
5. 转换为字符串
```

## 六、分享加密协议（混合加密 v2.0）

### 协议概述

**目的**: 端到端加密分享，防止 MITM（中间人）攻击

**加密方式**: RSA + AES 混合加密
- **AES-256-GCM**: 加密实际的分享数据（无大小限制）
- **RSA-OAEP**: 加密 AES 密钥（接收方公钥）
- **SHA256withRSA**: 数字签名验证发送方身份

### 加密流程

```
1. 序列化数据包为 JSON
   ShareDataPacket {
     version: "2.0",
     senderId: "user123",
     senderPublicKey: "MIIBIjANBg...",
     createdAt: 1234567890,
     expireAt: 1234567990,
     permission: { canView: true, canSave: true, revocable: true },
     password: { title: "...", username: "...", ... }
   }
   ↓
2. 生成随机 AES-256 密钥和 IV
   aesKey = KeyGenerator.getInstance("AES").generateKey()  // 32 字节
   iv = SecureRandom.nextBytes(12)  // 12 字节
   ↓
3. 用 AES-GCM 加密数据
   encryptedData = AES-GCM.encrypt(jsonBytes, aesKey, iv)
   ↓
4. 用接收方 RSA 公钥加密 AES 密钥
   encryptedAESKey = RSA-OAEP.encrypt(aesKey, receiverPublicKey)
   ↓
5. 用发送方 RSA 私钥签名原始数据
   signature = RSA-SHA256.sign(jsonBytes, senderPrivateKey)
   ↓
6. 组装 EncryptedSharePacket (v2.0)
   {
     version: "2.0",
     encryptedData: base64(encryptedData),
     encryptedAESKey: base64(encryptedAESKey),
     iv: base64(iv),
     signature: base64(signature),
     senderId: "user123",
     createdAt: 1234567890,
     expireAt: 1234567990
   }
```

### 解密流程

```
1. 验证版本是 "2.0"
   ↓
2. 用接收方 RSA 私钥解密 AES 密钥
   aesKey = RSA-OAEP.decrypt(encryptedAESKey, receiverPrivateKey)
   ↓
3. 用 AES 密钥和 IV 解密数据
   jsonBytes = AES-GCM.decrypt(encryptedData, aesKey, iv)
   ↓
4. 反序列化得到 ShareDataPacket
   data = JSON.parse(jsonBytes)
   ↓
5. 用发送方 RSA 公钥验证签名
   isValid = RSA-SHA256.verify(jsonBytes, signature, senderPublicKey)
   if (!isValid) throw SecurityException("签名验证失败")
   ↓
6. 返回 ShareDataPacket
```

### 安全特性

| 特性 | 说明 |
|-----|------|
| **端到端加密** | 只有接收方可以解密，服务器无法查看 |
| **防 MITM 攻击** | 数字签名验证发送方身份 |
| **前向安全** | AES 密钥每次随机生成 |
| **认证加密** | GCM 模式提供完整性验证 |
| **版本控制** | v2.0 与 v1.0 不兼容，强制升级 |

## 七、云端同步机制（零知识架构）

### 零知识架构数据流

```
前端                              后端
├─ 1. 本地加密数据                ┌─ 接收加密数据
│   encryptedData =              │  (无法解密)
│   AES-GCM.encrypt(data)        │
├─ 2. 上传到云端                 ├─ 存储到 user_vaults 表
│   POST /v1/vault/sync          │  - encrypted_data (TEXT)
│   { encryptedData,             │  - data_iv (VARCHAR 24)
│      dataIv,                   │  - data_auth_tag (VARCHAR 32)
│      dataAuthTag,              │  - salt (VARCHAR 32)
│      salt,                     │  - version (BIGINT)
│      clientVersion }           │
├────────────────────────────────┤
├─ 4. 下载时原样返回             ├─ 3. 下载请求
│   GET /v1/vault               │  GET /v1/vault
├─ 5. 本地解密                  │
│   data = AES-GCM.decrypt()    │
```

### 冲突检测

- **版本号机制**: clientVersion < serverVersion → 冲突
- **解决策略**:
  - 拒绝上传（默认）
  - 强制覆盖（用户确认）
  - 手动合并（高级选项）

## 八、密钥管理体系

### 密钥层级关系

```
主密码
 ├─→ Argon2id → PasswordKey → 加密 DataKey（可跨设备恢复）
 └─→ 生物识别 → DeviceKey → 加密 DataKey（本机快速解锁）
                            ↓
                        DataKey (随机256位)
                            ↓
                    加密 RSA-2048 私钥
```

### 密钥存储位置

| 密钥类型 | 存储位置 | 保护机制 |
|---------|---------|---------|
| **Salt** | SharedPreferences | 明文存储 |
| **PasswordKey** | 临时内存 | 派生后立即使用，不存储 |
| **DeviceKey** | AndroidKeyStore | 硬件保护 + 生物识别 |
| **DataKey (PasswordKey加密)** | SharedPreferences | AES-256-GCM + Argon2id |
| **DataKey (DeviceKey加密)** | SharedPreferences | AES-256-GCM + AndroidKeyStore |
| **DataKey (内存)** | CryptoSession | SensitiveData + 自动清零 |
| **RSA 私钥** | SharedPreferences | AES-256-GCM |
| **RSA 公钥** | SharedPreferences | 明文存储 |

## 九、核心安全类

### Android 前端

| 类名 | 路径 | 职责 |
|-----|------|------|
| **SecureKeyStorageManager** | security/ | 三层安全架构核心管理器 |
| **Argon2KeyDerivationManager** | crypto/ | Argon2id 密钥派生 |
| **CryptoSession** | security/ | 会话管理（DataKey 缓存） |
| **SessionGuard** | security/ | Guarded Execution 模式 |
| **ShareEncryptionManager** | crypto/ | 分享加密（混合加密） |
| **PasswordManager** | service/manager/ | 密码加密/解密 |
| **BackupEncryptionManager** | security/ | 备份加密管理 |

### Spring Boot 后端

| 类名 | 职责 |
|-----|------|
| **Argon2PasswordHasher** | Argon2id 密码哈希 |
| **JwtTokenProvider** | JWT Token 生成和验证（RS256） |
| **AuthService** | 认证服务 |
| **VaultService** | 密码库服务（零知识） |
| **UserVault** | 实体模型（加密数据存储） |

## 十、安全特性总结

| 特性 | 实现方式 | 安全等级 |
|-----|---------|---------|
| **零知识架构** | 服务器只存储加密数据，无法解密 | ★★★★★ |
| **端到端加密** | 客户端加密/解密，服务器透传 | ★★★★★ |
| **Argon2id KDF** | 抗 GPU/ASIC 攻击 | ★★★★★ |
| **AES-256-GCM** | 认证加密，防篡改 | ★★★★★ |
| **混合加密** | RSA + AES，兼顾安全和效率 | ★★★★★ |
| **AndroidKeyStore** | 硬件保护密钥 | ★★★★☆ |
| **安全随机填充** | 防止元数据泄露 | ★★★★☆ |
| **内存安全强化** | SensitiveData + 自动清零 | ★★★★☆ |
| **Guarded Execution** | 集中化安全边界 | ★★★★☆ |

## 十一、关键安全参数

| 参数 | 值 | 说明 |
|------|-----|------|
| **Argon2id timeCost** | 3 | 迭代次数 |
| **Argon2id memoryCost** | 131072KB (128MB) | 内存消耗 |
| **Argon2id parallelism** | 4 | 并行线程数 |
| **Argon2id outputLength** | 32 字节 (256 位) | 密钥长度 |
| **Argon2id saltLength** | 16 字节 (128 位) | 盐值长度 |
| **Access Token有效期** | 15分钟 | JWT访问令牌 |
| **Refresh Token有效期** | 7天 | JWT刷新令牌 |
| **DeviceKey有效期** | 30秒 | 硬件密钥 |
| **CryptoSession超时** | 5分钟 | 会话超时 |
| **剪贴板自动清除** | 30秒 | 复制安全 |

## 十二、安全威胁防护

| 威胁类型 | 防护措施 |
|---------|---------|
| **暴力破解** | Argon2id高成本派生，防认证风暴机制 |
| **中间人攻击** | HTTPS + 证书固定 |
| **重放攻击** | JWT时间戳 + Token自动刷新 |
| **内存转储** | DataKey内存缓存 + zeroize()安全清除 |
| **屏幕截图** | FLAG_SECURE防止截图 |
| **剪贴板泄露** | 30秒自动清除机制 |
| **设备丢失** | AndroidKeyStore硬件保护 + 生物识别 |
| **云端数据泄露** | 零知识架构，服务器仅存储密文 |

## 十三、相关文档

- [认证API文档](api/authentication.md)
- [数据库Schema](api/database-schema.md)
- [密码分享安全](api/contact-sharing.md)
- [用户指南](user-guide/password-sharing.md)
- [Guarded Execution 模式](plans/2026-02-08-guarded-execution-pattern.md)

## 十四、版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| 3.0 | 2026-02-28 | 添加 Level 0 会话管理层、云端登录流程、分享加密协议、安全随机填充详解、数据流程示例 |
| 2.0 | 2026-02-28 | 全面更新：三层安全架构、详细流程说明、云端同步机制 |
| 1.0 | 2026-02-08 | 初始版本，五层安全架构 |
