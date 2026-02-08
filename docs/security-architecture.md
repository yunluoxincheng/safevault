# SafeVault 安全存储架构

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

## 二、前端五层安全架构（Android）

```
┌─────────────────────────────────────────────────────────────────┐
│ Level 4: 认证管理层 (BiometricAuthManager)                        │
│     - 生物识别/主密码/PIN决策                                     │
│     - 防认证风暴机制                                              │
├─────────────────────────────────────────────────────────────────┤
│ Level 3: 数据层 (RSA密钥对)                                      │
│     - RSA-2048私钥被DataKey加密存储                              │
│     - 公钥明文存储，用于分享                                      │
├─────────────────────────────────────────────────────────────────┤
│ Level 2: 中间层 (Vault Key)                                     │
│     - 双重加密：PasswordKey版本 + DeviceKey版本                  │
│     - 支持云端备份和本地快速解锁                                  │
├─────────────────────────────────────────────────────────────────┤
│ Level 1: 根钥层 (Root Keys)                                     │
│     - PasswordKey: Argon2id派生(timeCost=3, 64MB)               │
│     - DeviceKey: AndroidKeyStore硬件保护，30秒有效期              │
├─────────────────────────────────────────────────────────────────┤
│ Level 0: 会话层 (DataKey)                                       │
│     - DataKey内存缓存，5分钟超时                                 │
│     - 使用zeroize()安全清除                                      │
└─────────────────────────────────────────────────────────────────┘
```

## 三、核心组件对应

| 前端组件 | 职责 | 位置 |
|---------|------|------|
| `SecureKeyStorageManager` | 密钥生成、加密存储、生物识别解锁 | `com.ttt.safevault.security` |
| `CryptoSession` | 会话状态管理（Level 0） | `com.ttt.safevault.security` |
| `BiometricAuthManager` | 认证决策（Level 4） | `com.ttt.safevault.security` |
| `TokenManager` | JWT自动刷新（剩余5分钟触发） | `com.ttt.safevault.network` |

## 四、关键安全参数

| 参数 | 值 | 说明 |
|------|-----|------|
| **Argon2id timeCost** | 3 | 迭代次数 |
| **Argon2id memoryCost** | 64MB (前端) / 128MB (后端) | 内存消耗 |
| **Argon2id parallelism** | 4 | 并行线程数 |
| **Access Token有效期** | 15分钟 | JWT访问令牌 |
| **Refresh Token有效期** | 7天 | JWT刷新令牌 |
| **DeviceKey有效期** | 30秒 | 硬件密钥 |
| **CryptoSession超时** | 5分钟 | 会话超时 |
| **剪贴板自动清除** | 30秒 | 复制安全 |

## 五、零知识架构核心原则

```
前端加密 → 后端存储（不解密）→ 前端解密
```

### 核心特性

1. **服务器只存储加密后的数据** - 后端永远不接触明文密码
2. **服务器无法获取用户明文密码** - 零知识证明架构
3. **所有加密/解密操作在客户端完成** - 前端负责全部密码学操作

### 加密流程

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   用户输入   │ →→→ │  前端加密   │ →→→ │  后端存储   │
│  (明文密码)  │     │ (AES-256)   │     │  (密文)     │
└─────────────┘     └─────────────┘     └─────────────┘
                                            ↓
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   用户查看   │ ←←← │  前端解密   │ ←←← │  后端传输   │
│  (明文密码)  │     │ (AES-256)   │     │  (密文)     │
└─────────────┘     └─────────────┘     └─────────────┘
```

## 六、密码学算法详解

### 6.1 Argon2id 密钥派生

```java
// 用于从主密码派生根密钥
Algorithm: Argon2id
- timeCost: 3
- memoryCost: 64MB (64 * 1024 KB)
- parallelism: 4
- outputLength: 32 bytes (256 bits)
- saltLength: 16 bytes (128 bits)
```

### 6.2 AES-256-GCM 加密

```java
// 用于加密密码数据
Algorithm: AES-256-GCM
- Key Size: 256 bits
- Mode: GCM (Galois/Counter Mode)
- IV Length: 12 bytes (96 bits)
- Tag Length: 16 bytes (128 bits)
```

### 6.3 RSA-2048 密钥对

```java
// 用于密码分享和数字签名
Algorithm: RSA
- Key Size: 2048 bits
- Padding: OAEP (Optimal Asymmetric Encryption Padding)
- Signature: SHA256withRSA
```

## 七、密钥层级关系

```
主密码/PIN
    │ Argon2id (timeCost=3, 64MB)
    ↓
PasswordKey (32 bytes)
    │
    ├──→ VaultKey (PasswordKey版本) →→→ RSA私钥
    │                                         │
    │                                         ├──→ 加密数据
    │                                         └──→ 数字签名
    │
    └──→ DataKey (内存缓存，5分钟超时)
            │
            └──→ AES-256加密/解密操作


DeviceKey (AndroidKeyStore, 30秒)
    │
    └──→ VaultKey (DeviceKey版本) →→→ RSA私钥
                                               │
                                               └──→ 快速解锁
```

## 八、安全威胁防护

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

## 九、数据流向图

### 注册流程

```
用户输入用户名/密码
    ↓
前端：Argon2id派生 → PasswordKey
    ↓
前端：生成RSA密钥对
    ↓
前端：用PasswordKey加密RSA私钥
    ↓
前端：发送用户名 + Argon2id参数 + 加密的RSA私钥 + 公钥
    ↓
后端：Argon2id (128MB) 哈希密码并存储
    ↓
后端：存储加密的RSA私钥和公钥
```

### 登录流程

```
用户输入主密码/PIN/生物识别
    ↓
前端：Argon2id派生 PasswordKey
    ↓
前端：解密 RSA私钥
    ↓
前端：生成 DataKey 并缓存
    ↓
前端：使用DataKey解密数据
    ↓
后端：验证JWT Token
```

### 数据存储流程

```
用户输入/编辑密码
    ↓
前端：使用DataKey进行AES-256-GCM加密
    ↓
前端：传输加密数据到后端
    ↓
后端：存储密文（不解密）
    ↓
后端：返回密文到前端
    ↓
前端：使用DataKey解密并显示
```

## 十、相关文档

- [认证API文档](../api/authentication.md)
- [数据库Schema](../api/database-schema.md)
- [密码分享安全](../api/contact-sharing.md)
- [用户指南](../user-guide/password-sharing.md)

## 十一、版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| 1.0 | 2026-02-08 | 初始版本，五层安全架构 |
