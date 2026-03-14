# Change: 移除 RSA 遗留实现，统一使用 X25519/Ed25519

## Why

当前项目存在 RSA-2048 和 X25519/Ed25519 双套密码实现：
- **RSA-2048**: 保留用于向后兼容，但存在性能和安全劣势
- **X25519/Ed25519**: v3.0+ 使用的现代密码算法

双套实现导致：
1. **代码复杂度高**: 需要维护两套加密/解密逻辑
2. **版本协商开销**: 每次分享需要判断对方支持的协议版本
3. **安全风险**: RSA-2048 相比 X25519 安全性较低
4. **性能损耗**: RSA 密钥生成慢约 100 倍，密钥尺寸大约 10 倍

当前项目已稳定运行，大部分用户已迁移到 v3.0 协议，可以移除 RSA 遗留实现。

## What Changes

### 删除的组件

- **BREAKING**: 删除 `RSAKeyManager.java` - RSA 密钥管理类
- **BREAKING**: 删除 `RSAEncryptionHelper.java` - RSA 加密工具类
- **BREAKING**: 删除 `LegacyShareProtocol.java` - 旧版分享协议
- **BREAKING**: 删除 `CryptoManager.java` 中的 RSA 相关方法

### 修改的组件

- **BREAKING**: 修改 `ShareEncryptionManager.java` - 移除 RSA 加密支持
- **BREAKING**: 修改 `SecureKeyStorageManager.java` - 移除 RSA 密钥存储
- **BREAKING**: 修改 `EncryptedSharePacketV2.java` - 标记为废弃
- **BREAKING**: 修改 `UserKeyInfo.java` - 移除 RSA 公钥字段

### 保留的组件

- `BouncyCastleX25519KeyManager.java` - API 32- 回退实现
- `BouncyCastleEd25519Signer.java` - API 33- 回退实现
- `EncryptedSharePacketV3.java` - 当前标准协议

### 数据清理

- 清理只使用 RSA 密钥的用户数据（无法解密）
- 清理 SharedPreferences 中的 RSA 密钥引用

## Impact

### 受影响的规格

- `crypto-algorithms` - 移除 RSA 相关要求
- `contact-sharing` - 更新分享协议要求

### 受影响的代码

**核心组件** (8 个文件):
- `SecureKeyStorageManager.java`
- `ShareEncryptionManager.java`
- `X25519KeyManager.java`
- `Ed25519Signer.java`
- `UserKeyInfo.java`
- `EncryptedSharePacketV3.java`
- `CryptoConstants.java`
- `BackendServiceImpl.java`

**删除组件** (4 个文件):
- `RSAKeyManager.java`
- `RSAEncryptionHelper.java`
- `LegacyShareProtocol.java`
- `EncryptedSharePacketV2.java` (标记废弃)

### Breaking Changes

1. **不再支持 RSA 协议**: 只使用 X25519/Ed25519 协议 v3.0
2. **旧版分享无法解密**: 使用 RSA 加密的旧分享将无法解密
3. **需要重新分享**: 用户需要重新分享使用旧协议的数据

### Non-Breaking Changes

- 已迁移到 v3.0 协议的用户不受影响
- 系统 API 回退机制保持不变

## Migration Plan

1. **迁移前检查**: 检查是否存在只使用 RSA 密钥的用户
2. **强制迁移**: 启动时检测 RSA 密钥，提示用户迁移
3. **数据清理**: 迁移完成后清理 RSA 相关数据
4. **版本更新**: 更新应用版本号，标记为 v4.0.0

## Open Questions

- 是否需要提供 RSA 数据导出工具？
- 是否需要提供迁移向导 UI？