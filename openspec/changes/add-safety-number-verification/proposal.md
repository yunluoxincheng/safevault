# Change: 分享时安全码验证防止 MITM 攻击

## Why

当前 SafeVault 的联系人分享功能缺少身份验证机制，存在中间人攻击风险：

1. **服务器被攻破**：攻击者替换用户的公钥
2. **DNS 劫持**：用户被引导到恶意服务器
3. **首次分享风险**：用户首次添加好友时无法验证对方身份

Signal、WhatsApp 等应用使用"安全码"（Safety Number）机制，用户可通过线下渠道（面对面、电话）验证对方身份。

## What Changes

- [ ] 新增 `SafetyNumberManager` 管理安全指纹
- [ ] 生成短指纹（5 组 2 位数字）和长指纹（SHA-256 哈希）
- [ ] 分享前显示安全码对比界面
- [ ] 支持标记"已验证"状态
- [ ] 支持重新验证（公钥变化时提示）

## Impact

- **Affected specs**: `contact-sharing`
- **Affected code**:
  - `ui/share/ShareActivity.java`
  - `crypto/ShareEncryptionManager.java`
  - 新增 `security/SafetyNumberManager.java`

## Breaking Changes

无破坏性变更。安全码验证为可选功能，不影响现有分享流程。
