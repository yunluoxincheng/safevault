# contact-sharing Specification Delta

## ADDED Requirements

### Requirement: 联系人分享使用混合加密方案

系统 MUST 使用混合加密方案（RSA + AES）进行联系人分享，以支持任意大小的分享数据。

#### Scenario: 创建混合加密的分享包

**Given** 用户 A 已登录
**And** 用户 A 选择向用户 B 分享密码
**And** 用户 B 是用户 A 的好友
**When** 系统 `ShareEncryptionManager.createEncryptedPacket()` 被调用
**Then** 系统 SHALL 生成随机 AES-256 密钥
**And** 系统 SHALL 生成随机 12 字节 IV
**And** 系统 SHALL 用 AES-256-GCM 加密 ShareDataPacket
**And** 系统 SHALL 用用户 B 的 RSA 公钥加密 AES 密钥
**And** 系统 SHALL 用用户 A 的 RSA 私钥签名原始数据
**And** 系统 SHALL 返回版本为 "2.0" 的 EncryptedSharePacket
**And** EncryptedSharePacket SHALL 包含 encryptedData、encryptedAESKey、iv、signature 字段

#### Scenario: 解密混合加密的分享包

**Given** 用户 B 收到来自用户 A 的分享
**And** EncryptedSharePacket 版本为 "2.0"
**When** 系统 `ShareEncryptionManager.openEncryptedPacket()` 被调用
**Then** 系统 SHALL 用用户 B 的 RSA 私钥解密 AES 密钥
**And** 系统 SHALL 用解密的 AES 密钥和 IV 解密数据
**And** 系统 SHALL 用用户 A 的 RSA 公钥验证签名
**And** 系统 SHALL 返回 ShareDataPacket
**And** ShareDataPacket SHALL 包含完整的密码和权限信息

#### Scenario: 拒绝不支持的协议版本

**Given** EncryptedSharePacket 版本不是 "2.0"
**When** 系统 `ShareEncryptionManager.openEncryptedPacket()` 被调用
**Then** 系统 SHALL 记录警告日志
**And** 系统 SHALL 返回 null
**And** 系统 SHALL 提示用户"分享协议版本不兼容，请更新应用"

---

### Requirement: AES 密钥和 IV 生成

系统 MUST 为每次分享生成唯一的 AES 密钥和 IV，确保前向安全。

#### Scenario: 生成唯一的 AES 密钥

**Given** 系统 `ShareEncryptionManager.createEncryptedPacket()` 被调用
**When** 系统 `generateAESKey()` 被调用
**Then** 系统 SHALL 使用 KeyGenerator 生成 256 位 AES 密钥
**And** 系统 SHALL 使用 SecureRandom 确保随机性
**And** 每次调用 SHALL 返回不同的密钥

#### Scenario: 生成唯一的 IV

**Given** 系统 `ShareEncryptionManager.createEncryptedPacket()` 被调用
**When** 系统 `generateIV()` 被调用
**Then** 系统 SHALL 使用 SecureRandom 生成 12 字节 IV
**And** 每次调用 SHALL 返回不同的 IV

---

### Requirement: 验证加密包完整性

系统 MUST 验证 EncryptedSharePacket 的完整性和有效性。

#### Scenario: 验证版本 2.0 加密包

**Given** EncryptedSharePacket 版本为 "2.0"
**When** `encryptedPacket.isValid()` 被调用
**And** encryptedData 不为空
**And** encryptedAESKey 不为空
**And** iv 不为空
**And** signature 不为空
**And** senderId 不为空
**And** 分享未过期
**Then** `isValid()` SHALL 返回 true

#### Scenario: 拒绝缺少必需字段的加密包

**Given** EncryptedSharePacket 版本为 "2.0"
**When** `encryptedPacket.isValid()` 被调用
**And** encryptedData 为空或 encryptedAESKey 为空或 iv 为空或 signature 为空
**Then** `isValid()` SHALL 返回 false
**And** 系统 SHALL 记录错误日志

---

### Requirement: 签名验证防止数据篡改

系统 MUST 验证分享数据的数字签名，确保数据未被篡改。

#### Scenario: 验证有效签名

**Given** ShareDataPacket 包含原始数据
**And** EncryptedSharePacket 包含发送方签名
**And** 发送方公钥可获取
**When** `verifySignature(data, signature, senderPublicKey)` 被调用
**And** 数据未被篡改
**Then** 验证 SHALL 返回 true
**And** 系统 SHALL 记录 "签名验证成功" 日志

#### Scenario: 检测被篡改的数据

**Given** ShareDataPacket 被篡改
**Or** EncryptedSharePacket 中的签名被篡改
**When** `verifySignature(data, signature, senderPublicKey)` 被调用
**Then** 验证 SHALL 返回 false
**And** 系统 SHALL 记录 "签名验证失败" 警告

---

### Requirement: 错误处理和用户反馈

系统 MUST 正确处理加密过程中的各种错误，并向用户提供清晰的反馈。

#### Scenario: 处理 AES 密钥生成失败

**Given** 系统 `generateAESKey()` 执行失败
**When** 异常被抛出
**Then** 系统 SHALL 记录错误日志 "Failed to generate AES key"
**And** `createEncryptedPacket()` SHALL 返回 null

#### Scenario: 处理 RSA 加密失败

**Given** 系统 `encryptAESKeyWithRSA()` 执行失败
**When** 异常被抛出（如 InvalidKeyException）
**Then** 系统 SHALL 记录错误日志
**And** `createEncryptedPacket()` SHALL 返回 null
**And** 用户 SHALL 看到 "加密失败" 提示

#### Scenario: 处理解密失败

**Given** 系统 `decryptAESKeyWithRSA()` 或 `decryptWithAES()` 执行失败
**When** 异常被抛出（如 BadPaddingException, AEADBadTagException）
**Then** 系统 SHALL 记录错误日志
**And** `openEncryptedPacket()` SHALL 返回 null
**And** 用户 SHALL 看到 "解密失败" 提示

---

## MODIFIED Requirements

### Requirement: 用户只能分享密码给已添加的好友

系统 MUST 确保用户只能将密码分享给已经建立好友关系的联系人。

*（原有场景保持不变，以下补充新场景）*

#### Scenario: 创建混合加密的联系人分享

**Given** 用户 A 已登录
**And** 用户 B 是用户 A 的好友
**And** 用户 A 选择用户 B 并创建密码分享
**When** 系统 `createEncryptedPacket()` 被调用
**Then** 系统 SHALL 使用混合加密方案
**And** 系统 SHALL 生成唯一的 AES 密钥和 IV
**And** 分享创建成功
**And** EncryptedSharePacket 版本为 "2.0"

---

## Cross-References

- **相关规范**：`user-interaction-improvement` - 分享功能用户界面
- **依赖组件**：`ShareEncryptionManager`, `EncryptedSharePacket`, `ShareDataPacket`
- **安全规范**：加密方案符合 NIST 标准和行业最佳实践
