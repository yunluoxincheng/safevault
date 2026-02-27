# 分享时安全码验证 - 技术设计

## Context

SafeVault 的联系人分享功能使用 RSA 公钥加密分享数据。当前缺少用户身份验证机制：

```
当前流程：
Alice ──────> 服务器 <────── Bob
  (上传公钥)    (下载公钥)
```

**风险**：如果服务器被攻破或 DNS 劫持，攻击者可替换公钥：
```
攻击流程：
Alice ───> 恶意服务器 <─── Bob
         (替换公钥)
```

## Goals / Non-Goals

**Goals**:
- 用户可通过线下渠道验证对方身份
- 检测公钥变化（MITM 攻击迹象）
- 参考行业最佳实践（Signal、WhatsApp）

**Non-Goals**:
- 不强制验证（用户可选择跳过）
- 不改变现有加密协议
- 不增加服务器负载

## Decisions

### Decision 1: 短指纹格式（人类可读）

**选择**: 5 组 2 位数字（共 10 位）

**原因**:
- 易于口头传达（电话验证）
- 易于核对（面对面时）
- 参考 Signal 的安全码设计

**示例**：`12-34-56-78-90`

```java
public String generateShortFingerprint(PublicKey publicKey) {
    byte[] publicKeyBytes = publicKey.getEncoded();
    byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes);

    // 取前 4 字节
    int[] digits = new int[5];
    for (int i = 0; i < 4; i++) {
        digits[i] = (hash[i] & 0xFF) % 100;  // 0-99
    }
    digits[4] = ((hash[0] & 0xFF) + (hash[1] & 0xFF) + (hash[2] & 0xFF) + (hash[3] & 0xFF)) % 100;

    // 格式化为 12-34-56-78-90
    return String.format("%02d-%02d-%02d-%02d-%02d",
        digits[0], digits[1], digits[2], digits[3], digits[4]);
}
```

### Decision 2: 长指纹格式（完整验证）

**选择**: 完整 SHA-256 哈希，十六进制显示

**原因**:
- 64 字符十六进制字符串
- 支持复制粘贴到其他渠道
- 高安全性（碰撞概率极低）

**示例**：
```
a1b2c3d4e5f6...7890abcdef1234
```

### Decision 3: 首次分享强制验证

**选择**: 用户首次向某好友分享时，显示安全码验证对话框

**原因**:
- 建立验证习惯
- 防止用户永久忽略安全

**替代方案**:
- 完全可选：可能导致 99% 用户永远不验证

```java
// 分享前检查
if (!isVerified(recipientUsername, recipientPublicKey)) {
    showSafetyNumberDialog(recipientUsername, recipientPublicKey, () -> {
        // 用户确认后继续分享
        createEncryptedPacket(...);
    });
}
```

### Decision 4: 公钥变化警告

**选择**: 如果已验证用户的公钥发生变化，警告用户

**原因**:
- 可能的 MITM 攻击
- 用户重新注册导致密钥更换
- 需要重新验证

**警告界面**：
```
⚠️ 安全警告

用户 [Bob] 的安全码已变化。

如果 Bob 没有重新安装应用或更换设备，
这可能是中间人攻击。

旧安全码：12-34-56-78-90
新安全码：98-76-54-32-10

[联系用户验证]  [重新验证]
```

### Decision 5: 验证状态存储

**选择**: 本地数据库存储已验证的安全码

**数据结构**：
```java
@Entity(tableName = "verified_safety_numbers")
public class VerifiedSafetyNumber {
    private String username;           // 好友用户名
    private String publicKey;          // Base64 编码的公钥
    private String shortFingerprint;   // 短指纹
    private String fullFingerprint;    // 长指纹
    private long verifiedAt;           // 验证时间戳
    private String deviceId;           // 设备标识
}
```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 用户忽略验证 | 安全功能形同虚设 | 首次强制，后续可选 |
| 指纹碰撞 | 错误验证 | SHA-256 碰撞概率极低 |
| UI 复杂度增加 | 用户困惑 | 简洁设计，清晰的文案 |
| 数据库增长 | 存储占用 | 每个好友 ~1KB |

## Migration Plan

### Phase 1: 工具类和数据库（1 天）
- 创建 `SafetyNumberManager`
- 创建数据库表和 DAO

### Phase 2: UI 组件（1.5 天）
- 创建验证对话框
- 创建安全码详情页面

### Phase 3: 集成（1 天）
- 修改分享流程
- 添加公钥变化检测

### Phase 4: 测试（0.5 天）
- 单元测试
- UI 测试

### Rollback
如果出现问题，移除验证步骤，保留指纹生成功能供未来使用。

## Open Questions

1. **是否需要 QR 码验证？**
   - 当前方案：口头或视觉核对数字
   - 可选：未来添加 QR 码扫描快速验证

2. **是否支持"盲目信任"？**
   - 建议：允许，但标记为"未验证"
   - 在 UI 上显示警告图标
