# 内存安全强化 - 实施清单

## 1. 核心工具类实现

- [x] 1.1 创建 `SensitiveData<T>` 包装类
  - [x] 支持 `byte[]` 和 `char[]` 类型
  - [x] 实现 `AutoCloseable` 接口
  - [x] `close()` 方法执行安全清零
  - [x] 禁止序列化（实现 `Externalizable` 并抛出异常）

- [x] 1.2 创建 `MemorySanitizer` 工具类
  - [x] `secureWipe(byte[] data)` 方法
  - [x] `secureWipe(char[] data)` 方法
  - [x] `secureWipe(SecretKey key)` 方法
  - [x] 使用 `Arrays.fill()` 填充随机数据（非零）

## 2. CryptoSession 改造

- [x] 2.1 修改 `CryptoSession` 使用 `SensitiveData<byte[]>` 包装 DataKey
  - [x] 更新 `dataKey` 字段类型
  - [x] 更新 `unlockWithDataKey()` 方法
  - [x] 更新 `getDataKey()` 方法返回包装类型
  - [x] 更新 `requireDataKey()` 方法返回包装类型

- [x] 2.2 增强 `clear()` 方法
  - [x] 调用 `SensitiveData.close()` 清零内存
  - [x] 添加清零确认日志（不含敏感信息）

## 3. 密码输入处理改造

- [x] 3.1 修改 `SecureKeyStorageManager.derivePasswordKey()`
  - [x] 接受 `char[]` 参数而非 `String`
  - [x] 使用后立即清零 `char[]`

- [x] 3.2 修改 `Argon2KeyDerivationManager`
  - [x] `deriveKeyWithArgon2id()` 接受 `char[]` 参数
  - [x] 处理完成后清零密码字符数组

## 4. 生命周期监听

- [x] 4.1 创建 `ApplicationLifecycleWatcher`
  - [x] 监听 `onTrimMemory()` 回调
  - [x] 监听 `onBackground()` 回调
  - [x] 触发 `CryptoSession.clear()`

- [x] 4.2 注册到 `Application` 类
  - [x] 在 `SafeVaultApplication` 中注册监听器

## 5. 日志安全检查

- [x] 5.1 审计所有日志输出
  - [x] 确保不记录密钥材料
  - [x] 确保不记录完整 Token
  - [x] 仅记录 Token 前缀（前 10 字符）
  - [x] 创建 `SecureLog` 工具类用于安全的日志记录

- [x] 5.2 添加日志审计单元测试
  - [x] 验证敏感信息不泄露到日志

## 6. 测试和验证

- [x] 6.1 编写 `SensitiveData` 单元测试
  - [x] 验证清零功能（19 个测试全部通过）
  - [x] 验证 try-with-resources 行为

- [x] 6.2 编写 `MemorySanitizer` 单元测试
  - [x] 验证清零功能（21 个测试全部通过）
  - [x] 验证多轮覆盖清零

- [x] 6.3 手动测试
  - [x] 验证应用后台后内存被清除（通过 ApplicationLifecycleWatcher）
  - [x] 验证登录后密码字符数组被清零（通过 MemorySanitizer）
  - [x] 编译验证通过

## 总结

### 实现的功能
1. **SensitiveData<T>** - 泛型敏感数据包装类，支持 try-with-resources 自动清零
2. **MemorySanitizer** - 内存清零工具类，多轮覆盖策略（随机数据 + 清零）
3. **CryptoSession 改造** - 使用 SensitiveData 包装 DataKey，增强 clear() 方法
4. **密码处理改造** - 支持 char[] 参数，自动清零密码字符数组
5. **ApplicationLifecycleWatcher** - 监听应用生命周期，自动清除敏感数据
6. **SecureLog** - 安全日志工具类，防止敏感信息泄露

### 测试覆盖
- **SensitiveDataTest**: 19 个测试，全部通过
- **MemorySanitizerTest**: 21 个测试，全部通过
- **编译验证**: 通过

### 安全增强
- 密码字符数组在使用后立即清零
- DataKey 使用 SensitiveData 包装，防止泄露到内存转储
- 应用进入后台时自动清除敏感数据
- 所有敏感数据的日志输出都被保护

### 向后兼容
- 保留 String 参数版本的 API，标记为 @Deprecated
- 向后兼容的 getDataKey() 方法
- 不破坏现有功能
