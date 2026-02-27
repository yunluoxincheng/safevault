# 安全随机填充 - 技术设计

## Context

SafeVault 当前使用 AES-256-GCM 加密每个密码字段，但未做填充处理：

```java
// 当前实现：
public String encryptField(String plaintext) {
    byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
    // 直接加密，不做填充
    byte[] ciphertext = cipher.doFinal(plaintextBytes);
    return Base64.encodeToString(ciphertext);
}
```

**问题**：
- 明文 "abc" (3 字节) → 密文 ~15 字节
- 明文 "password" (8 字节) → 密文 ~20 字节
- 攻击者可通过密文长度推断明文长度

## Goals / Non-Goals

**Goals**:
- 防止通过密文长度推断明文长度
- 防止元数据泄露（密码强度、字段类型）
- 使用随机填充增加熵

**Non-Goals**:
- 不完全隐藏数据长度（块大小内仍可推断）
- 不显著增加数据库大小（最多 256 字节/字段）

## Decisions

### Decision 1: 块大小设为 256 字节

**选择**: 填充目标长度为 256 字节的倍数

**原因**:
- 大多数密码字段 < 256 字节
- 一次填充即可隐藏长度
- 数据库增长可控（每个字段最多 +256 字节）

**替代方案**:
- 512 字节：更安全但数据库增长大
- 128 字节：对长密码可能需要两次填充

### Decision 2: 随机填充而非 `\0`

**选择**: 使用 `SecureRandom` 生成随机填充字节

**原因**:
- `\0` 填充可被统计分析识别
- 随机填充增加熵，防止模式分析
- `SecureRandom` 是加密安全的随机数生成器

**替代方案**:
- `\0` 填充：简单但可预测
- 固定模式填充：仍可分析

```java
public static byte[] pad(byte[] plaintext) {
    int paddingLength = BLOCK_SIZE - (plaintext.length % BLOCK_SIZE);
    int targetLength = plaintext.length + paddingLength;

    byte[] padded = new byte[targetLength];

    // 1. 复制原始数据
    System.arraycopy(plaintext, 0, padded, 0, plaintext.length);

    // 2. 填充随机字节（关键：不是 \0）
    random.nextBytes(padded, plaintext.length, paddingLength);

    // 3. 在最后一个字节写入填充长度
    padded[targetLength - 1] = (byte) paddingLength;

    return padded;
}
```

### Decision 3: 作用在 byte[] 而非 String

**选择**: Padding 操作作用在 UTF-8 字节上

**原因**:
- AES-GCM 加密的是 `byte[]`
- UTF-8 编码导致字符长度 ≠ 字节长度
- String.length() 无法准确反映加密数据长度

**替代方案**:
- String.length()：不正确，UTF-8 字符可能 1-4 字节

### Decision 4: 版本标识支持兼容

**选择**: 在加密数据中包含版本标识

**原因**:
- 支持旧数据无缝迁移
- 新旧格式共存

**实现**：
```java
// 格式：version:iv:ciphertext
// v1 = 无填充
// v2 = 带随机填充

public String encryptField(String plaintext, boolean usePadding) {
    byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
    byte[] paddedBytes = usePadding ? SecurePaddingUtil.pad(plaintextBytes) : plaintextBytes;

    // 加密...
    byte[] ciphertext = cipher.doFinal(paddedBytes);

    // 组合：v2:iv:ciphertext
    return "v2:" + Base64.encodeToString(iv) + ":" + Base64.encodeToString(ciphertext);
}
```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 数据库大小增长 | 每个字段最多 +256 字节 | 可接受，现代手机存储充足 |
| 迁移失败 | 旧数据无法解密 | 保留备份，支持回滚 |
| 填充长度错误被篡改 | unpad() 抛出异常 | 验证填充长度范围 (1, BLOCK_SIZE] |

## Migration Plan

### Phase 1: 工具类（0.5 天）
- 创建 `SecurePaddingUtil`
- 编写单元测试

### Phase 2: 集成（1 天）
- 修改 `PasswordManager`
- 添加版本标识支持

### Phase 3: 迁移（1 天）
- 创建迁移工具
- 集成到应用启动流程

### Phase 4: 测试（0.5 天）
- 测试迁移完整性
- 测试回滚场景

### Rollback
如果迁移失败，恢复旧代码，保留备份。

## Open Questions

1. **是否所有字段都填充？**
   - 建议：仅填充敏感字段（密码、用户名、备注）
   - URL 和标签可不填充（非高度敏感）

2. **块大小是否可配置？**
   - 当前方案：固定 256 字节
   - 可选：未来允许高级用户配置
