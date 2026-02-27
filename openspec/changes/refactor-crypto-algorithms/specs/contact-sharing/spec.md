# contact-sharing Spec Delta (X25519/Ed25519 迁移)

## ADDED Requirements

### Requirement: X25519/Ed25519 分享协议
系统 SHALL 支持 X25519/Ed25519 作为协议版本 3.0 的加密算法。

#### Scenario: 创建 v3.0 分享包
- **GIVEN** 发送方有 X25519/Ed25519 密钥对
- **AND** 接收方有 X25519 公钥
- **WHEN** 系统 `ShareEncryptionManager.createEncryptedPacket()` 被调用
- **THEN** 系统 SHALL 生成 ephemeral X25519 密钥对
- **AND** 系统 SHALL 用 ephemeral 私钥 + 接收方公钥执行 ECDH
- **AND** 系统 SHALL 用派生密钥加密 ShareDataPacket（AES-256-GCM）
- **AND** 系统 SHALL 用 Ed25519 私钥签名原始数据
- **AND** 系统 SHALL 返回版本为 "3.0" 的 EncryptedSharePacket
- **AND** EncryptedSharePacket SHALL 包含 ephemeralPublicKey、encryptedData、iv、signature 字段

#### Scenario: 解密 v3.0 分享包
- **GIVEN** 接收方收到 v3.0 EncryptedSharePacket
- **WHEN** 系统 `ShareEncryptionManager.openEncryptedPacket()` 被调用
- **THEN** 系统 SHALL 用本地 X25519 私钥 + ephemeralPublicKey 执行 ECDH
- **AND** 系统 SHALL 派生 AES 密钥
- **AND** 系统 SHALL 解密 encryptedData
- **AND** 系统 SHALL 用发送方 Ed25519 公钥验证签名
- **AND** 系统 SHALL 返回 ShareDataPacket

#### Scenario: Ephemeral Key 前向保密
- **GIVEN** 分享使用 ephemeral X25519 密钥
- **WHEN** 分享完成后
- **THEN** 系统 SHALL 立即丢弃 ephemeral 私钥
- **AND** 即使发送方长期私钥泄露，历史分享仍安全
- **AND** 系统 SHALL NOT 复用 ephemeral key

---

### Requirement: 协议版本自动选择
系统 SHALL 根据双方的公钥类型自动选择最佳协议版本。

#### Scenario: 优先使用 v3.0
- **GIVEN** 发送方有 X25519 公钥
- **AND** 接收方有 X25519 公钥
- **WHEN** 创建分享
- **THEN** 系统 SHALL 选择协议版本 3.0
- **AND** 系统 SHALL 使用 X25519/Ed25519

#### Scenario: 回退到 v2.0
- **GIVEN** 发送方有 X25519 公钥
- **AND** 接收方仅有 RSA 公钥
- **WHEN** 创建分享
- **THEN** 系统 SHALL 选择协议版本 2.0
- **AND** 系统 SHALL 使用 RSA-OAEP

#### Scenario: 不兼容的公钥类型
- **GIVEN** 接收方公钥类型未知
- **WHEN** 尝试创建分享
- **THEN** 系统 SHALL 抛出 `UnsupportedKeyException`
- **AND** 系统 SHALL 提示用户"对方不支持新版加密，请更新应用"

---

### Requirement: 密钥类型存储和查询
系统 SHALL 支持存储和查询多种类型的公钥。

#### Scenario: 上传新类型公钥
- **GIVEN** 用户生成 X25519/Ed25519 密钥对
- **WHEN** 上传公钥到服务器
- **THEN** 系统 SHALL 调用 POST /v1/users/keys
- **AND** 请求 SHALL 包含 `x25519_public_key` 和 `ed25519_public_key`
- **AND** 系统 SHALL 保持原有的 `public_key` (RSA) 字段

#### Scenario: 查询用户的公钥
- **WHEN** 系统需要获取用户的公钥
- **THEN** 系统 SHALL 调用 GET /v1/users/:id
- **AND** 响应 SHALL 包含 `public_key` (RSA)
- **AND** 响应 SHALL 包含 `x25519_public_key` (如果存在)
- **AND** 响应 SHALL 包含 `ed25519_public_key` (如果存在)

#### Scenario: 优先使用 X25519 公钥
- **GIVEN** 用户同时有 RSA 和 X25519 公钥
- **WHEN** 创建分享
- **THEN** 系统 SHALL 优先使用 X25519 公钥
- **AND** 系统 SHALL 回退到 RSA 如果 X25519 不可用

---

### Requirement: 密钥迁移用户引导
系统 SHALL 引导用户从 RSA 迁移到 X25519/Ed25519。

#### Scenario: 新用户自动使用 v3.0
- **GIVEN** 新用户注册或登录
- **WHEN** 生成密钥对
- **THEN** 系统 SHALL 默认生成 X25519/Ed25519 密钥对
- **AND** 系统 SHALL 不生成 RSA 密钥（除非需要向后兼容）

#### Scenario: 老用户迁移提示
- **GIVEN** 用户仅有 RSA 密钥
- **WHEN** 用户打开分享功能
- **THEN** 系统 SHALL 显示"升级加密"提示
- **AND** 系统 SHALL 说明 v3.0 的优势
- **AND** 系统 SHALL 提供"立即升级"按钮

#### Scenario: 迁移进度显示
- **WHEN** 用户执行密钥迁移
- **THEN** 系统 SHALL 显示迁移进度
- **AND** 系统 SHALL 说明步骤：生成密钥 → 上传公钥 → 完成
- **AND** 系统 SHALL 处理迁移失败情况

---

## MODIFIED Requirements

### Requirement: 联系人分享使用混合加密方案
系统 SHALL 支持多种加密算法进行联系人分享，包括 RSA+AES 混合加密（v2.0）和 X25519/Ed25519 现代加密（v3.0），并自动选择最佳版本。

#### Scenario: 创建 v2.0 混合加密的分享包
- **GIVEN** 协议版本为 2.0
- **AND** 双方使用 RSA 密钥
- **WHEN** 系统 `ShareEncryptionManager.createEncryptedPacket()` 被调用
- **THEN** 系统 SHALL 生成随机 AES-256 密钥
- **AND** 系统 SHALL 用接收方 RSA 公钥加密 AES 密钥
- **AND** 系统 SHALL 用发送方 RSA 私钥签名
- **AND** 系统 SHALL 返回版本为 "2.0" 的 EncryptedSharePacket

#### Scenario: 创建 v3.0 ECDH 加密的分享包
- **GIVEN** 协议版本为 3.0
- **AND** 双方使用 X25519/Ed25519 密钥
- **WHEN** 系统 `ShareEncryptionManager.createEncryptedPacket()` 被调用
- **THEN** 系统 SHALL 生成 ephemeral X25519 密钥对
- **AND** 系统 SHALL 用 ECDH 派生共享密钥
- **AND** 系统 SHALL 用共享密钥加密数据（AES-256-GCM）
- **AND** 系统 SHALL 用 Ed25519 私钥签名
- **AND** 系统 SHALL 返回版本为 "3.0" 的 EncryptedSharePacket

#### Scenario: 自动选择协议版本
- **WHEN** 创建分享
- **THEN** 系统 SHALL 检测双方的公钥类型
- **AND** 系统 SHALL 优先选择 v3.0 (X25519/Ed25519)
- **AND** 系统 SHALL 回退到 v2.0 (RSA) 如有必要
- **AND** 系统 SHALL 记录使用的协议版本

---

### Requirement: 拒绝不支持的协议版本
系统 SHALL 拒绝不支持的分享协议版本，并支持 v2.0 (RSA) 和 v3.0 (X25519/Ed25519) 两种协议。

#### Scenario: 接受 v2.0 和 v3.0
- **GIVEN** EncryptedSharePacket 版本为 "2.0" 或 "3.0"
- **WHEN** 系统 `ShareEncryptionManager.openEncryptedPacket()` 被调用
- **THEN** 系统 SHALL 根据版本选择解密算法
- **AND** v2.0 SHALL 使用 RSA-OAEP
- **AND** v3.0 SHALL 使用 X25519 ECDH

#### Scenario: 拒绝未知版本
- **GIVEN** EncryptedSharePacket 版本不是 "2.0" 或 "3.0"
- **WHEN** 系统 `ShareEncryptionManager.openEncryptedPacket()` 被调用
- **THEN** 系统 SHALL 记录警告日志
- **AND** 系统 SHALL 返回 null
- **AND** 系统 SHALL 提示用户"分享协议版本不兼容，请更新应用"

---

### Requirement: 错误处理和用户反馈
系统 SHALL 正确处理加密过程中的各种错误（包括 v3.0 的 ECDH、Ed25519 错误），并向用户提供清晰的反馈。

#### Scenario: 处理 ECDH 密钥交换失败
- **GIVEN** 系统 `performECDH()` 执行失败
- **WHEN** 异常被抛出
- **THEN** 系统 SHALL 记录错误日志 "Failed to perform ECDH"
- **AND** 系统 SHALL 回退到 RSA 如果可用
- **AND** 系统 SHALL 提示用户"加密失败"

#### Scenario: 处理 Ed25519 签名失败
- **GIVEN** 系统 `ed25519Sign()` 执行失败
- **WHEN** 异常被抛出
- **THEN** 系统 SHALL 记录错误日志
- **AND** 系统 SHALL 提示用户"签名失败"

#### Scenario: 处理解密失败（v3.0）
- **GIVEN** 系统 ECDH 或 AES-GCM 解密失败
- **WHEN** 异常被抛出
- **THEN** 系统 SHALL 记录错误日志
- **AND** 系统 SHALL 返回 null
- **AND** 系统 SHALL 提示用户"解密失败"
