# crypto-algorithms Specification - Delta Changes

## REMOVED Requirements

### Requirement: RSA-2048 Key Generation

**Reason**: 已迁移到 X25519/Ed25519 现代密码算法，RSA-2048 性能和安全性较低。

**Migration**: 使用 `X25519KeyManager` 替代 RSA 密钥管理。

### Requirement: RSA-OAEP Encryption for Share Packets

**Reason**: v2.0 协议已废弃，统一使用 v3.0 协议（X25519 ECDH + AES-GCM）。

**Migration**: 使用 `EncryptedSharePacketV3` 进行分享加密。

### Requirement: RSA Key Storage in SecureKeyStorageManager

**Reason**: 不再需要存储 RSA 密钥对。

**Migration**: `SecureKeyStorageManager` 只存储 X25519 和 Ed25519 密钥。

### Requirement: Protocol Version Negotiation (v2.0 vs v3.0)

**Reason**: 只支持 v3.0 协议，无需版本协商。

**Migration**: 移除版本检测逻辑，直接使用 v3.0 协议。

---

## ADDED Requirements

### Requirement: X25519 ECDH Key Exchange (Standard)

系统 MUST 使用 X25519 进行密钥交换，作为标准协议。

#### Scenario: Generate X25519 Key Pair

- **WHEN** 用户注册或迁移到 v3.0 协议
- **THEN** 系统 SHALL 生成 X25519 密钥对
- **AND** 私钥 SHALL 使用主密码加密存储
- **AND** 公钥 SHALL 上传到服务器

#### Scenario: Perform ECDH Key Derivation

- **WHEN** 与好友建立共享密钥
- **THEN** 系统 SHALL 使用 X25519 ECDH 计算共享密钥
- **AND** 使用 HKDF 派生最终加密密钥

### Requirement: Ed25519 Digital Signature (Standard)

系统 MUST 使用 Ed25519 进行数字签名，确保数据完整性。

#### Scenario: Sign Share Data

- **WHEN** 创建密码分享
- **THEN** 系统 SHALL 使用 Ed25519 私钥签名数据
- **AND** 签名 SHALL 附加到加密包中

#### Scenario: Verify Signature

- **WHEN** 接收密码分享
- **THEN** 系统 SHALL 使用发送方 Ed25519 公钥验证签名
- **AND** 验证失败 SHALL 拒绝解密

### Requirement: API Level Fallback Mechanism

系统 MUST 在低版本 Android API 上使用 Bouncy Castle 回退实现。

#### Scenario: X25519 Fallback on API 32-

- **WHEN** 设备 API 级别 < 33
- **THEN** 系统 SHALL 使用 Bouncy Castle 实现 X25519
- **AND** 功能与系统 API 实现一致

#### Scenario: Ed25519 Fallback on API 33-

- **WHEN** 设备 API 级别 < 34
- **THEN** 系统 SHALL 使用 Bouncy Castle 实现 Ed25519
- **AND** 功能与系统 API 实现一致

### Requirement: HKDF Key Derivation

系统 MUST 使用 HKDF 从 ECDH 共享密钥派生最终加密密钥。

#### Scenario: Derive Encryption Key with HKDF

- **WHEN** ECDH 共享密钥已计算
- **THEN** 系统 SHALL 使用 HKDF-SHA256 派生 256 位密钥
- **AND** 支持身份绑定（info 参数包含双方用户 ID）

### Requirement: Forward Secrecy Support

系统 MUST 支持前向保密，每次分享使用临时密钥。

#### Scenario: Ephemeral Key for Each Share

- **WHEN** 创建新分享
- **THEN** 系统 SHALL 生成临时 X25519 密钥对
- **AND** 临时公钥 SHALL 包含在加密包中
- **AND** 临时私钥 SHALL 在使用后立即清除