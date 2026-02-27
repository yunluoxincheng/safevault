# crypto-security Spec Delta (X25519/Ed25519 迁移)

## ADDED Requirements

### Requirement: X25519 ECDH 密钥交换
系统 SHALL 支持 X25519 (Curve25519 ECDH) 进行密钥交换。

#### Scenario: 生成 X25519 密钥对
- **WHEN** 系统生成 X25519 密钥对
- **THEN** 系统 SHALL 使用 Bouncy Castle Provider
- **AND** 系统 SHALL 生成 32 字节公钥
- **AND** 系统 SHALL 生成 32 字节私钥
- **AND** 系统 SHALL 安全存储私钥（使用 DataKey 加密）

#### Scenario: 执行 ECDH 密钥交换
- **GIVEN** 本地 X25519 私钥和远程 X25519 公钥
- **WHEN** 执行 ECDH 操作
- **THEN** 系统 SHALL 计算 Diffie-Hellman 共享密钥
- **AND** 系统 SHALL 返回 32 字节共享密钥
- **AND** 系统 SHALL 使用 HKDF 派生加密密钥

#### Scenario: Ephemeral Key 生成
- **WHEN** 创建端到端分享
- **THEN** 系统 SHALL 生成新的 ephemeral X25519 密钥对
- **AND** 系统 SHALL 在单次分享中使用
- **AND** 系统 SHALL 使用完毕后立即丢弃

---

### Requirement: Ed25519 数字签名
系统 SHALL 支持 Ed25519 (Curve25519 EdDSA) 进行数字签名。

#### Scenario: 生成 Ed25519 密钥对
- **WHEN** 系统生成 Ed25519 密钥对
- **THEN** 系统 SHALL 使用 Bouncy Castle Provider
- **AND** 系统 SHALL 生成 32 字节公钥
- **AND** 系统 SHALL 生成 32 字节私钥（或 64 字节种子）
- **AND** 系统 SHALL 安全存储私钥

#### Scenario: Ed25519 签名
- **GIVEN** Ed25519 私钥和待签名数据
- **WHEN** 执行签名操作
- **THEN** 系统 SHALL 使用 Ed25519 算法
- **AND** 系统 SHALL 返回 64 字节签名
- **AND** 系统 SHALL 包含 SHA-512 哈希

#### Scenario: Ed25519 验证
- **GIVEN** Ed25519 公钥、数据和签名
- **WHEN** 执行验证操作
- **THEN** 系统 SHALL 验证签名有效性
- **AND** 签名有效时 SHALL 返回 true
- **AND** 签名无效时 SHALL 返回 false

---

### Requirement: 协议版本 3.0 (X25519/Ed25519)
系统 SHALL 支持协议版本 3.0，使用 X25519/Ed25519 加密。

#### Scenario: 创建 v3.0 加密包
- **GIVEN** 发送方有 X25519/Ed25519 密钥对
- **AND** 接收方有 X25519 公钥
- **WHEN** 创建 EncryptedSharePacket
- **THEN** 系统 SHALL 生成 ephemeral X25519 密钥对
- **AND** 系统 SHALL 执行 ECDH 密钥交换
- **AND** 系统 SHALL 用派生密钥加密数据（AES-256-GCM）
- **AND** 系统 SHALL 用 Ed25519 私钥签名
- **AND** 系统 SHALL 返回版本为 "3.0" 的加密包

#### Scenario: 解密 v3.0 加密包
- **GIVEN** EncryptedSharePacket 版本为 "3.0"
- **AND** 包含 ephemeral X25519 公钥
- **WHEN** 解密加密包
- **THEN** 系统 SHALL 用本地 X25519 私钥和 ephemeral 公钥执行 ECDH
- **AND** 系统 SHALL 派生 AES 密钥
- **AND** 系统 SHALL 解密数据
- **AND** 系统 SHALL 用 Ed25519 公钥验证签名

---

### Requirement: 加密算法版本协商
系统 SHALL 根据双方支持的算法版本自动选择最佳版本。

#### Scenario: 双方都支持 v3.0
- **GIVEN** 发送方支持 v2.0 (RSA) 和 v3.0 (X25519)
- **AND** 接收方支持 v3.0
- **WHEN** 创建分享
- **THEN** 系统 SHALL 选择 v3.0
- **AND** 系统 SHALL 使用 X25519/Ed25519

#### Scenario: 一方仅支持 v2.0
- **GIVEN** 发送方支持 v2.0 和 v3.0
- **AND** 接收方仅支持 v2.0 (RSA)
- **WHEN** 创建分享
- **THEN** 系统 SHALL 选择 v2.0
- **AND** 系统 SHALL 使用 RSA-OAEP

#### Scenario: 不支持的密钥类型
- **GIVEN** 接收方公钥类型未知
- **WHEN** 尝试创建分享
- **THEN** 系统 SHALL 抛出异常
- **AND** 系统 SHALL 提示用户"对方不支持新版加密"

---

### Requirement: 密钥迁移到 X25519/Ed25519
系统 SHALL 支持从 RSA 迁移到 X25519/Ed25519。

#### Scenario: 生成新密钥对
- **WHEN** 用户执行密钥迁移
- **THEN** 系统 SHALL 生成 X25519 密钥对
- **AND** 系统 SHALL 生成 Ed25519 密钥对
- **AND** 系统 SHALL 加密存储私钥
- **AND** 系统 SHALL 保留 RSA 密钥作为备份

#### Scenario: 上传新公钥到服务器
- **GIVEN** 新生成的 X25519/Ed25519 公钥
- **WHEN** 完成密钥生成
- **THEN** 系统 SHALL 上传公钥到服务器
- **AND** 系统 SHALL 更新 `users` 表的 `x25519_public_key` 和 `ed25519_public_key` 字段
- **AND** 系统 SHALL 保持 `public_key` (RSA) 字段不变

#### Scenario: 迁移失败回滚
- **GIVEN** 密钥迁移过程中发生错误
- **WHEN** 检测到迁移失败
- **THEN** 系统 SHALL 删除新生成的密钥
- **AND** 系统 SHALL 保持 RSA 密钥可用
- **AND** 系统 SHALL 记录错误日志

---

### Requirement: 多算法密钥存储
系统 SHALL 同时存储 RSA 和 X25519/Ed25519 密钥。

#### Scenario: 存储多种密钥类型
- **WHEN** 用户同时有 RSA 和 X25519/Ed25519 密钥
- **THEN** 系统 SHALL 在 SharedPreferences 中分别存储
- **AND** 系统 SHALL 使用不同的键名区分
- **AND** 系统 SHALL 记录当前活跃的密钥版本

#### Scenario: 读取密钥时指定类型
- **WHEN** 读取密钥进行加密操作
- **THEN** 系统 SHALL 根据协议版本选择对应密钥
- **AND** v2.0 SHALL 使用 RSA 密钥
- **AND** v3.0 SHALL 使用 X25519/Ed25519 密钥

---

## MODIFIED Requirements

### Requirement: RSA Padding Scheme
RSA 加密 SHALL 使用 OAEP 填充方案，仅用于协议版本 2.0；协议版本 3.0 SHALL 使用 X25519/Ed25519 替代。

#### Scenario: OAEP加密参数 (v2.0)
- **WHEN** 使用 RSA 加密（协议版本 2.0）
- **THEN** SHALL 使用 RSA/ECB/OAEPWithSHA-256AndMGF1Padding
- **AND** SHALL 使用 SHA-256 作为摘要算法
- **AND** SHALL 使用 MGF1 作为掩码生成函数

#### Scenario: X25519 替代 RSA (v3.0)
- **WHEN** 使用协议版本 3.0
- **THEN** 系统 SHALL NOT 使用 RSA
- **AND** 系统 SHALL 使用 X25519 ECDH 密钥交换
- **AND** 系统 SHALL 使用 Ed25519 数字签名

---

### Requirement: Encryption Algorithm Versioning
加密数据 SHALL 包含算法版本标识，支持 v2 (RSA) 和 v3 (X25519/Ed25519) 两种版本，并优先使用 v3。

#### Scenario: 版本标识格式
- **WHEN** 创建加密数据包
- **THEN** SHALL 包含版本字段
- **AND** 版本格式应为"vN"（N为数字）
- **AND** 当前版本 SHALL 支持 "v2" (RSA) 和 "v3" (X25519/Ed25519)
- **AND** 新数据 SHALL 优先使用 "v3"

#### Scenario: 版本协商
- **WHEN** 两端协商加密算法
- **THEN** SHALL 交换支持的版本列表
- **AND** SHALL 优先选择双方支持的最新版本
- **AND** 如果无共同版本，应拒绝操作

#### Scenario: 版本升级策略
- **GIVEN** 新版本加密算法可用（v3.0）
- **WHEN** 创建新的加密数据
- **THEN** SHALL 优先使用 v3.0
- **AND** SHALL 保持 v2.0 数据可解密
- **AND** 应提供密钥迁移工具
