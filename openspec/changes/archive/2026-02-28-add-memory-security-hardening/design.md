# 内存安全强化 - 技术设计

## Context

SafeVault 使用 Java 的 `String` 和 `byte[]` 存储密钥和密码等敏感数据。Java String 不可变，无法显式清零，导致敏感数据在堆中长期驻留直到 GC 回收。

### 当前问题
```java
// 问题代码示例：
public SecretKey derivePasswordKey(String masterPassword, String saltBase64) {
    // String masterPassword 无法清零，在堆中驻留
    return deriveKey(...);
}

// 问题代码示例：
public class CryptoSession {
    private SecretKey dataKey; // 无法控制何时被 GC

    public void clear() {
        dataKey = null; // 仅清空引用，实际密钥仍在内存
    }
}
```

## Goals / Non-Goals

**Goals**:
- 最小化敏感数据在内存中的驻留时间
- 使用后立即清零内存
- 防止敏感数据泄露到日志
- 应用后台时主动清除内存

**Non-Goals**:
- 不修改外部 API（所有修改为内部实现）
- 不影响性能（清零操作耗时 <1ms）
- 不依赖第三方库（纯 Java 实现）

## Decisions

### Decision 1: 使用 SensitiveData 包装类

**选择**: 创建泛型包装类 `SensitiveData<T>` 支持 `byte[]` 和 `char[]`

**原因**:
- 类型安全：编译时检查类型
- AutoCloseable：支持 try-with-resources
- 透明包装：不改变上层 API

**替代方案**:
- 直接修改所有使用点：工作量太大，易遗漏
- 使用第三方库（如 Tink）：增加依赖

```java
public class SensitiveData<T> implements AutoCloseable {
    private T data;
    private final boolean isArray;

    public SensitiveData(T data) {
        this.data = data;
        this.isArray = (data instanceof byte[] || data instanceof char[]);
    }

    public T get() {
        return data;
    }

    @Override
    public void close() {
        if (data instanceof byte[]) {
            Arrays.fill((byte[]) data, (byte) 0);
        } else if (data instanceof char[]) {
            Arrays.fill((char[]) data, '\0');
        }
        data = null;
    }

    // 禁止序列化
    private void writeObject(ObjectOutputStream out) throws IOException {
        throw new NotSerializableException("SensitiveData cannot be serialized");
    }
}
```

### Decision 2: 多轮清零而非单次填充

**选择**: 先填充随机数据，再清零

**原因**:
- 防止内存分析工具通过"全零"模式识别已清零区域
- 增加攻击者从内存转储中提取密钥的难度

**替代方案**:
- 直接填充零：简单但可预测

```java
public static void secureWipe(byte[] data) {
    if (data == null) return;
    // 第一轮：填充随机数据
    new SecureRandom().nextBytes(data);
    // 第二轮：清零
    Arrays.fill(data, (byte) 0);
}
```

### Decision 3: 使用 onTrimMemory() 触发清零

**选择**: 监听 Android 生命周期回调

**原因**:
- Android 标准 API
- 系统主动通知内存压力
- 比 GC 更可靠的触发时机

**替代方案**:
- 定时器：可能延迟清零
- 手动触发：依赖用户操作

```java
@Override
public void onTrimMemory(int level) {
    if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
        // 应用进入后台
        cryptoSession.clear();
    }
}
```

### Decision 4: 密码参数使用 char[] 而非 String

**选择**: 修改 API 接受 `char[]` 参数

**原因**:
- `char[]` 可被清零
- `String` 不可变且驻留在字符串池

**替代方案**:
- 保持 String API：无法清零

**迁移策略**:
```java
// 旧 API（保留但标记废弃）
@Deprecated
public SecretKey derivePasswordKey(String masterPassword, String saltBase64) {
    return derivePasswordKey(masterPassword.toCharArray(), saltBase64);
}

// 新 API
public SecretKey derivePasswordKey(char[] masterPassword, String saltBase64) {
    try {
        return deriveKeyInternal(masterPassword, saltBase64);
    } finally {
        MemorySanitizer.secureWipe(masterPassword); // 立即清零
    }
}
```

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| SecretKey.getEncoded() 返回新数组 | 无法直接清零 | 依赖 KeyStore 硬件保护 |
| JVM 优化可能清零操作被优化掉 | 清零失效 | 使用 volatile 或 sun.misc.Unsafe |
| Android 序列化可能泄露敏感数据 | PendingIntent/Bundle 泄露 | 禁止 SensitiveData 序列化 |
| 性能开销（清零 + 随机填充） | 每次加密/解密额外耗时 <1ms | 可接受 |

## Migration Plan

### Phase 1: 工具类（1 天）
- 创建 `SensitiveData<T>`
- 创建 `MemorySanitizer`
- 编写单元测试

### Phase 2: 核心改造（2 天）
- 修改 `CryptoSession`
- 修改 `SecureKeyStorageManager`
- 修改 `Argon2KeyDerivationManager`

### Phase 3: 生命周期集成（0.5 天）
- 创建 `ApplicationLifecycleWatcher`
- 注册到 `SafeVaultApplication`

### Phase 4: 审计和测试（0.5 天）
- 审计所有日志输出
- 编写集成测试

### Rollback
如果出现问题，恢复到原有实现，仅保留工具类供未来使用。

## Open Questions

1. **是否需要防御 JVM 优化？**
   - 当前方案使用 `Arrays.fill()` 可能被 JVM 优化掉
   - 可选：使用 `sun.misc.Unsafe`（非标准 API）

2. **SecretKey 如何清零？**
   - `SecretKey.getEncoded()` 返回新数组副本
   - 无法直接清零 KeyStore 内部存储
   - 当前方案：依赖硬件保护，清零外部副本

3. **是否需要内存泄漏检测？**
   - 可选：集成 LeakCanary
   - 验证 `SensitiveData` 实例被正确释放
