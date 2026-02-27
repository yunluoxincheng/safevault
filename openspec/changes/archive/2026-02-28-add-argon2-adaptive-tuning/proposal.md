# Change: Argon2 自适应性能调优

## Why

当前 SafeVault 使用固定的 Argon2id 参数（t=3, m=128MB, p=4），在低端设备上可能导致：
1. **OOM 崩溃**：128MB 内存超出设备可用内存
2. **显著卡顿**：单次哈希耗时超过 2 秒，用户体验差
3. **电池消耗**：高内存和 CPU 占用加速电量消耗

同时，让用户选择"性能模式"会导致 99% 用户选择低安全级别，降低系统整体安全性。

## What Changes

- [ ] 新增 `AdaptiveArgon2Config` 工具类，自动检测设备能力
- [ ] 设定最低安全下限（64MB, 2 次迭代, 2 并行）
- [ ] 根据设备可用内存和 CPU 核心数动态调优
- [ ] 初始化时一次性存储参数，避免重复计算
- [ ] 移除任何用户可选择的性能模式选项

## Impact

- **Affected specs**: `auth-security`
- **Affected code**:
  - `crypto/Argon2KeyDerivationManager.java`
  - `security/SecureKeyStorageManager.java`

## Breaking Changes

轻微破坏性变更：已有用户的 Argon2id 参数可能在更新后变化，但验证逻辑保持兼容（盐值不变）。
