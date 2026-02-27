# Change: 内存安全强化

## Why

当前 SafeVault 在密钥和敏感数据的内存管理上存在安全风险：
1. 使用 `String` 存储敏感数据（不可变，无法清零）
2. 密钥在内存中驻留时间过长
3. 应用后台时未及时清除敏感数据
4. 缺少显式内存清零（Zeroization）机制

这些漏洞可能导致：
- **Heap Dump 攻击**：通过内存转储获取密钥材料
- **冷启动攻击**：从设备内存中恢复敏感数据
- **GC 泄露**：String 不可变导致在堆中长期驻留

## What Changes

- [x] 新增 `SensitiveData` 工具类，包装 `byte[]` 并支持安全清零
- [x] 修改 `CryptoSession` 使用 `SensitiveData` 包装 DataKey
- [x] 在 `SecureKeyStorageManager` 中使用 `char[]` 替代 `String` 存储密码
- [x] 新增 `MemorySanitizer` 工具类，提供安全清零方法
- [x] 监听应用生命周期，后台时主动清除内存
- [x] 更新日志记录，确保不泄露敏感信息

## Impact

- **Affected specs**: `crypto-security`
- **Affected code**:
  - `security/CryptoSession.java`
  - `security/SecureKeyStorageManager.java`
  - `crypto/Argon2KeyDerivationManager.java`
  - `crypto/ShareEncryptionManager.java`
  - `service/manager/PasswordManager.java`

## Breaking Changes

无破坏性变更。所有修改为内部实现细节，不影响 API 接口。
