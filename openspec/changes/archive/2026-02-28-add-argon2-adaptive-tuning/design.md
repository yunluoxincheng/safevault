# Argon2 自适应性能调优 - 技术设计

## Context

SafeVault 使用 Argon2id 作为唯一密钥派生算法，当前固定参数配置：
- 时间成本（迭代次数）：3
- 内存成本：128MB (131072 KB)
- 并行度：4

在低端设备（如 2GB RAM）上，128MB 内存占用可能导致：
- 应用崩溃（OOM）
- 系统杀后台进程
- 用户感知卡顿

## Goals / Non-Goals

**Goals**:
- 系统自动根据设备能力调优参数
- 设定最低安全下限，防止参数过低
- 用户无感知，无需选择
- 高端设备享受更高安全性

**Non-Goals**:
- 不让用户选择性能模式
- 不低于 OWASP 建议的最小值
- 不影响已有用户数据

## Decisions

### Decision 1: 最低安全下限

**选择**: 设定最低下限为 64MB 内存、2 次迭代、2 并行

**原因**:
- 64MB 仍高于 OWASP 最低建议（64MB = 65536 KB）
- 2 次迭代保持合理的抗暴力破解能力
- 2 并行适应双核设备

**替代方案**:
- 更低下限（32MB）：安全性不足
- 固定参数：低端设备无法使用

```java
// 最低安全下限（OWASP 建议的调整值）
private static final int MIN_MEMORY_KB = 65536;     // 64MB
private static final int MIN_ITERATIONS = 2;
private static final int MIN_PARALLELISM = 2;

// 标准配置（当前值）
private static final int STANDARD_MEMORY_KB = 131072;  // 128MB
private static final int STANDARD_ITERATIONS = 3;
private static final int STANDARD_PARALLELISM = 4;
```

### Decision 2: 参数计算逻辑

**选择**: 使用设备可用内存的 25% 作为内存成本上限

**原因**:
- 避免占用过多内存导致系统杀进程
- 为系统和其他应用留出空间
- 25% 是安全且保守的比例

**计算公式**:
```java
int availableMemoryKB = memoryClassMB * 1024;  // 系统返回的单位是 MB
int memoryKB = Math.max(MIN_MEMORY_KB,
                       Math.min(availableMemoryKB / 4, STANDARD_MEMORY_KB));

int cpuCores = Runtime.getRuntime().availableProcessors();
int parallelism = Math.max(MIN_PARALLELISM,
                          Math.min(cpuCores, STANDARD_PARALLELISM));

int iterations = (memoryKB >= STANDARD_MEMORY_KB) ?
                 STANDARD_ITERATIONS : MIN_ITERATIONS;
```

### Decision 3: 参数一次性存储

**选择**: 在应用首次启动时计算并存储参数

**原因**:
- 避免每次派生密钥时重新计算
- 保证同一设备上参数一致
- SharedPreferences 读取速度快

**替代方案**:
- 每次计算：不必要的开销

```java
public static void initializeParameters(Context context) {
    Argon2Parameters params = getOptimalParameters(context);

    SharedPreferences prefs = context.getSharedPreferences("argon2_config", Context.MODE_PRIVATE);
    prefs.edit()
        .putInt("memory_cost", params.getMemory())
        .putInt("time_cost", params.getIterations())
        .putInt("parallelism", params.getParallelism())
        .apply();
}

public static Argon2Parameters getStoredParameters(Context context) {
    SharedPreferences prefs = context.getSharedPreferences("argon2_config", Context.MODE_PRIVATE);
    return new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withMemoryAsKB(prefs.getInt("memory_cost", STANDARD_MEMORY_KB))
        .withIterations(prefs.getInt("time_cost", STANDARD_ITERATIONS))
        .withParallelism(prefs.getInt("parallelism", STANDARD_PARALLELISM))
        .build();
}
```

### Decision 4: 不让用户选择

**选择**: 完全自动，不提供任何用户选项

**原因**:
- 用户无法评估安全风险
- 99% 用户会选择低安全性
- 专业工具不应将安全责任转嫁给用户

**替代方案**:
- 用户选择模式：已否决

## Risks / Trade-offs

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 参数更新后验证失败 | 旧数据无法解密 | 保持盐值不变，仅参数变化 |
| 最低下限仍过高 | 部分设备无法使用 | 测试 2GB 设备，必要时降至 48MB |
| 参数存储被篡改 | 安全性降低 | SharedPreferences 本地存储，篡改风险低 |

## Migration Plan

### Phase 1: 工具类开发（1 天）
- 创建 `AdaptiveArgon2Config`
- 实现设备检测和参数计算

### Phase 2: 集成（0.5 天）
- 修改 `Argon2KeyDerivationManager`
- 修改 `SecureKeyStorageManager`

### Phase 3: 测试（0.5 天）
- 高端设备测试
- 低端设备测试

### Rollback
如果出现问题，恢复固定参数配置。

## Open Questions

1. **是否需要手动重置参数？**
   - 当前方案：首次启动自动计算
   - 可选：开发者选项中提供"重新计算参数"按钮

2. **是否记录实际参数到日志？**
   - 建议：记录用于安全审计
   - 格式："Argon2id: m=64MB,t=2,p=2"
