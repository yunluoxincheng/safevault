# 任务清单：混合加密方案实现

## Phase 1: 数据结构修改

- [x] **1.1** 修改 `EncryptedSharePacket.java`
  - [x] 添加 `encryptedAESKey` 字段（String 类型）
  - [x] 添加 `iv` 字段（String 类型）
  - [x] 添加 `getEncryptedAESKey()` 和 `setEncryptedAESKey()` 方法
  - [x] 添加 `getIv()` 和 `setIv()` 方法
  - [x] 更新 Parcelable 实现（writeToParcel, 构造函数）
  - [x] 更新 `isValid()` 方法支持版本 2.0 验证

- [x] **1.2** 更新 `EncryptedSharePacket` 构造函数
  - [x] 确保新字段在默认构造函数中初始化
  - [x] 确保新字段在 Parcelable 构造函数中正确读取

## Phase 2: ShareEncryptionManager 核心实现

- [x] **2.1** 添加 AES 相关常量
  - [x] `AES_TRANSFORMATION = "AES/GCM/NoPadding"`
  - [x] `AES_KEY_SIZE = 256`
  - [x] `GCM_IV_LENGTH = 12`
  - [x] `GCM_TAG_LENGTH = 128`

- [x] **2.2** 实现 AES 密钥生成方法
  - [x] `generateAESKey()` - 使用 KeyGenerator 和 SecureRandom
  - [x] 添加异常处理和日志

- [x] **2.3** 实现 IV 生成方法
  - [x] `generateIV()` - 使用 SecureRandom 生成 12 字节
  - [x] 添加日志记录

- [x] **2.4** 实现 AES-GCM 加密方法
  - [x] `encryptWithAES(byte[], SecretKey, byte[])` - 使用 GCMParameterSpec
  - [x] 处理 AEADBadTagException 异常
  - [x] 添加详细日志

- [x] **2.5** 实现 AES-GCM 解密方法
  - [x] `decryptWithAES(byte[], SecretKey, byte[])` - 使用 GCMParameterSpec
  - [x] 处理 AEADBadTagException 异常
  - [x] 添加详细日志

- [x] **2.6** 实现 RSA 加密 AES 密钥方法
  - [x] `encryptAESKeyWithRSA(SecretKey, PublicKey)` - 使用 RSA-OAEP
  - [x] 添加异常处理和日志

- [x] **2.7** 实现 RSA 解密 AES 密钥方法
  - [x] `decryptAESKeyWithRSA(byte[], PrivateKey)` - 使用 RSA-OAEP
  - [x] 返回 SecretKey 对象
  - [x] 添加异常处理和日志

- [x] **2.8** 重写 `createEncryptedPacket()` 方法
  - [x] 添加版本检查，设置为 "2.0"
  - [x] 调用新的加密流程
  - [x] 组装完整的 EncryptedSharePacket
  - [x] 添加完整的错误处理

- [x] **2.9** 重写 `openEncryptedPacket()` 方法
  - [x] 添加版本验证（必须是 "2.0"）
  - [x] 调用解密流程
  - [x] 添加签名验证
  - [x] 添加完整的错误处理

- [x] **2.10** 修复 `getMaxBlockSize()` 方法
  - [x] 使用 RSAKey 接口获取密钥位数
  - [x] 正确计算 OAEP 开销
  - [x] 添加日志输出

## Phase 3: 调用点更新

- [x] **3.1** 更新 `ReceiveShareActivity.java`
  - [x] 添加必要的导入（KeyFactory, X509EncodedKeySpec）
  - [x] 修改 `decryptShareData()` 方法支持版本 2.0
  - [x] 添加 `openEncryptedPacketWithoutSignature()` 方法
  - [x] 处理发送方公钥获取和签名验证
  - [x] 更新 `isEncryptedSharePacket()` 方法

- [x] **3.2** 更新 `ShareActivity.java`
  - [x] 验证 `createEncryptedPacket()` 调用正确
  - [x] 确保错误处理完善

## Phase 4: 单元测试

- [x] **4.1** 测试 AES 密钥生成
  - [x] 验证密钥长度为 256 位
  - [x] 验证每次生成的密钥不同

- [x] **4.2** 测试 IV 生成
  - [x] 验证 IV 长度为 12 字节
  - [x] 验证每次生成的 IV 不同

- [x] **4.3** 测试 AES-GCM 加密解密
  - [x] 测试正常数据加密解密
  - [x] 测试空数据处理
  - [x] 测试大数据处理（>1KB）
  - [x] 测试篡改数据检测

- [x] **4.4** 测试 RSA 密钥加密解密
  - [x] 测试 AES 密钥加密
  - [x] 测试加密密钥解密
  - [x] 测试错误密钥解密（应该失败）

- [x] **4.5** 测试 `createEncryptedPacket()`
  - [x] 验证返回的包版本为 "2.0"
  - [x] 验证 `isValid()` 返回 true
  - [x] 验证所有必需字段存在

- [x] **4.6** 测试 `openEncryptedPacket()`
  - [x] 验证正确解密数据
  - [x] 验证签名验证成功
  - [x] 验证篡改签名被检测

- [x] **4.7** 测试版本兼容性
  - [x] 验证版本 "2.0" 被接受
  - [x] 验证版本 "1.0" 被拒绝
  - [x] 验证未知版本被拒绝

## Phase 5: 集成测试

- [x] **5.1** 端到端测试（同设备）
  - [x] ShareActivity 创建分享
  - [x] 通过 Intent 传递到 ReceiveShareActivity
  - [x] 验证接收并正确解密

- [x] **5.2** QR 码分享测试
  - [x] 生成包含加密包的 QR 码
  - [x] 扫描 QR 码
  - [x] 验证接收并正确解密

- [x] **5.3** 蓝牙分享测试
  - [x] 通过蓝牙发送加密包
  - [x] 接收端解密
  - [x] 验证数据完整性

- [x] **5.4** 边界测试
  - [x] 空密码字段
  - [x] 超长备注（>1000 字符）
  - [x] 特殊字符（Unicode、Emoji）

## Phase 6: 文档和验证

- [x] **6.1** 代码注释
  - [x] 为所有新方法添加 JavaDoc
  - [x] 更新类级别的文档

- [x] **6.2** 验证 OpenSpec 规范
  - [x] 运行 `openspec validate hybrid-share-encryption --strict`
  - [x] 修复所有验证问题

- [x] **6.3** 更新 CLAUDE.md（如需要）
  - [x] 记录新的加密协议版本
  - [x] 更新架构说明

## 依赖关系

```
Phase 1 (数据结构) [已完成]
    │
    ▼
Phase 2 (核心实现) [已完成] ──┬──→ Phase 4 (单元测试) [已完成]
    │                        │
    ▼                        │
Phase 3 (调用点更新) [已完成] ┘
    │
    ▼
Phase 5 (集成测试) [已完成]
    │
    ▼
Phase 6 (文档和验证) [已完成]
```

## 可并行执行的任务

以下任务可以并行执行：
- **Phase 1** 和 **Phase 2.1**（常量定义）- 已完成
- **Phase 2.2-2.7**（各个加密方法独立）- 已完成
- **Phase 4.1-4.4**（单元测试独立）- 已完成
- **Phase 5.2-5.4**（集成测试独立）- 已完成

## 验收标准

每个 Phase 完成后：
- [x] Phase 1: 所有任务已标记为完成
- [x] Phase 2: 所有任务已标记为完成
- [x] Phase 3: 所有任务已标记为完成
- [x] Phase 4: 相关测试通过
- [x] Phase 5: 相关测试通过
- [x] Phase 6: 代码已审查，没有新的警告或错误

## 已完成的工作总结

### 代码修改文件
1. **EncryptedSharePacket.java** - 添加 encryptedAESKey 和 iv 字段
2. **ShareEncryptionManager.java** - 实现混合加密（RSA + AES）
3. **ReceiveShareActivity.java** - 更新解密逻辑支持版本 2.0

### 编译状态
✅ 代码编译成功，无错误

### 测试状态
✅ 单元测试通过
✅ 集成测试通过
✅ OpenSpec 验证通过

### 完成状态
🎉 **所有阶段已完成** - Phase 1 至 Phase 6 全部完成
