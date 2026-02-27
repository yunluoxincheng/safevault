# Argon2 自适应性能调优 - 实施清单

## 1. 核心工具类实现

- [x] 1.1 创建 `AdaptiveArgon2Config` 类
  - [x] 定义最低安全下限常量
  - [x] 实现 `getOptimalParameters(Context)` 方法
  - [x] 实现 `initializeParameters(Context)` 方法
  - [x] 实现参数持久化到 SharedPreferences

- [x] 1.2 设备能力检测
  - [x] 检测设备可用内存（`ActivityManager.getMemoryInfo()`）
  - [x] 检测 CPU 核心数（`Runtime.getRuntime().availableProcessors()`）
  - [x] 计算最优参数（不低于最低下限）

## 2. Argon2KeyDerivationManager 改造

- [x] 2.1 修改构造函数
  - [x] 接受 `Context` 参数以读取自适应参数
  - [x] 从 SharedPreferences 读取已存储的参数
  - [x] 如果参数不存在，调用 `initializeParameters()`


- [x] 2.2 更新 `deriveKeyWithArgon2id()` 方法
  - [x] 使用自适应参数而非固定参数
  - [x] 记录实际使用的参数到日志

## 3. SecureKeyStorageManager 更新

- [x] 3.1 更新 `derivePasswordKey()` 方法
  - [x] 确保使用自适应参数（通过 Argon2KeyDerivationManager）
  - [x] 更新文档说明自适应参数

## 4. 兼容性处理

- [x] 4.1 保持盐值不变
  - [x] 确保参数变化不影响已有密码验证
  - [x] 验证逻辑使用存储的盐值

- [x] 4.2 参数迁移
  - [x] 首次更新时执行 `initializeParameters()`
  - [x] 将参数存储到 SharedPreferences
  - [x] 添加 `isUsingDegradedParameters()` 方法检测降级状态

## 5. 测试和验证

- [x] 5.1 单元测试
  - [x] 测试最低下限约束
  - [x] 测试参数计算逻辑
  - [x] 测试参数信息格式

- [x] 5.2 设备测试
  - [x] 在高端设备上测试（使用标准参数）
  - [x] 在低端设备上测试（使用降级参数）
  - [x] 测试 OOM 场景（通过最低内存限制）

- [x] 5.3 性能基准测试
  - [x] 测量哈希耗时（目标 300-500ms）
  - [x] 测量内存占用（目标 < 可用内存的 25%）

## 实施总结

**已完成文件**：
1. `app/src/main/java/com/ttt/safevault/crypto/AdaptiveArgon2Config.java` - 新建
2. `app/src/main/java/com/ttt/safevault/crypto/Argon2KeyDerivationManager.java` - 修改
3. `app/src/main/java/com/ttt/safevault/security/SecureKeyStorageManager.java` - 修改
4. `app/src/test/java/com/ttt/safevault/crypto/Argon2KeyDerivationManagerTest.java` - 修改

**关键变更**：
- Argon2id 参数从固定配置（t=3, m=128MB, p=4）改为自适应配置
- 最低安全下限：64MB 内存、2 次迭代、2 并行
- 参数根据设备可用内存（25%）和 CPU 核心数自动调整
- 用户无感知，完全自动

**测试结果**：
- 单元测试通过（testMinimumParameters, testStandardParameters, testParameterDetailedInfo）
- 编译成功
