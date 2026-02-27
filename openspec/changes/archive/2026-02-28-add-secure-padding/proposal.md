# Change: 安全随机填充防止元数据泄露

## Why

当前 SafeVault 在加密密码字段时未做填充处理，攻击者可通过密文长度推断：
1. **密码长度**：通过密文长度反推明文长度
2. **字段内容**：区分用户名是否为邮箱、密码强度等
3. **使用模式**：分析用户行为模式

原有的基于 `String.length()` 的 Padding 方案存在：
- UTF-8 编码导致字符长度与字节长度不一致
- 使用 `\0` 填充仍可进行长度分析
- AES-GCM 加密的是 `byte[]` 而非 `String`

## What Changes

- [ ] 新增 `SecurePaddingUtil` 工具类
- [ ] 作用在 `byte[]` 而非 `String`
- [ ] 使用随机填充而非 `\0`
- [ ] 在 `PasswordManager` 中集成
- [ ] 支持字段级可选填充（敏感字段强制填充）

## Impact

- **Affected specs**: `crypto-security`
- **Affected code**:
  - `service/manager/PasswordManager.java`
  - `crypto/ShareEncryptionManager.java`

## Breaking Changes

**破坏性变更**：现有加密数据的格式发生变化。需要：
- 新数据使用随机填充
- 旧数据解密时自动识别并迁移
- 提供数据迁移脚本
