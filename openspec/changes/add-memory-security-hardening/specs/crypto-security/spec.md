# crypto-security Spec Delta (内存安全强化)

## ADDED Requirements

### Requirement: 敏感数据内存清零
系统 SHALL 在敏感数据使用完毕后立即清零内存，防止数据泄露。

#### Scenario: 密钥使用后立即清零
- **GIVEN** 密钥已加载到内存用于加密/解密操作
- **WHEN** 加密/解密操作完成
- **THEN** 系统 SHALL 使用 `Arrays.fill()` 填充随机数据
- **AND** 系统 SHALL 再次填充零确保完全清零
- **AND** 系统 SHALL 将密钥引用设为 null

#### Scenario: 密码处理后立即清零
- **GIVEN** 用户密码作为 `char[]` 传入密钥派生函数
- **WHEN** 密钥派生完成
- **THEN** 系统 SHALL 立即清零 `char[]` 所有元素
- **AND** 系统 SHALL 记录清零操作（不含密码内容）

#### Scenario: 会话锁定时清零 DataKey
- **GIVEN** 应用已解锁，DataKey 缓存在 `CryptoSession` 中
- **WHEN** 应用锁定或进入后台
- **THEN** 系统 SHALL 清零 DataKey 内存
- **AND** 系统 SHALL 将 DataKey 引用设为 null
- **AND** 后续访问 SHALL 触发重新解锁

---

### Requirement: 敏感数据使用可清零类型
系统 SHALL 使用 `char[]` 或 `byte[]` 存储敏感数据，避免使用不可变的 `String`。

#### Scenario: 密钥派生使用 char[]
- **WHEN** 接收用户主密码进行密钥派生
- **THEN** 系统 SHALL 接受 `char[]` 参数
- **AND** 系统 SHALL NOT 使用 `String` 存储密码
- **AND** 处理完成后 SHALL 清零 `char[]`

#### Scenario: 密钥包装类实现 AutoCloseable
- **WHEN** 创建敏感数据包装类 `SensitiveData<T>`
- **THEN** 系统 SHALL 实现 `AutoCloseable` 接口
- **AND** SHALL 支持 try-with-resources 语法
- **AND** `close()` 方法 SHALL 执行安全清零

#### Scenario: 禁止敏感数据序列化
- **WHEN** 尝试序列化 `SensitiveData` 实例
- **THEN** 系统 SHALL 抛出 `NotSerializableException`
- **AND** 系统 SHALL 记录安全警告日志
- **AND** 系统 SHALL 防止数据通过 IPC/Bundle 泄露

---

### Requirement: 应用后台时主动清除内存
系统 SHALL 监听应用生命周期事件，后台时主动清除所有敏感数据。

#### Scenario: 应用进入后台时清零 DataKey
- **GIVEN** 应用已解锁，DataKey 缓存在内存中
- **WHEN** 系统调用 `onTrimMemory(TRIM_MEMORY_UI_HIDDEN)`
- **THEN** 系统 SHALL 立即清零 DataKey
- **AND** 系统 SHALL 将会话状态设为"已锁定"
- **AND** 用户回到应用时需重新解锁

#### Scenario: 内存压力时清除缓存
- **GIVEN** 系统内存紧张
- **WHEN** 系统调用 `onTrimMemory()` 且级别 >= `TRIM_MEMORY_BACKGROUND`
- **THEN** 系统 SHALL 清零 DataKey
- **AND** 系统 SHALL 清除所有非必要缓存
- **AND** 系统 SHALL 记录内存清零事件

---

### Requirement: 日志不得泄露敏感信息
系统 SHALL 确保敏感信息不被记录到日志文件。

#### Scenario: 日志中不记录密钥材料
- **WHEN** 记录加密/解密操作日志
- **THEN** 系统 SHALL NOT 记录密钥内容
- **AND** 系统 SHALL NOT 记录密钥长度（可能泄露信息）
- **AND** 系统 SHALL 使用占位符如 "[REDACTED]"

#### Scenario: 日志中仅记录 Token 前缀
- **WHEN** 记录 JWT Token 相关日志
- **THEN** 系统 SHALL 仅记录 Token 前 10 字符
- **AND** 系统 SHALL 用 "..." 表示截断
- **AND** 示例格式： "Bearer eyJhbGc..."

#### Scenario: 错误日志不包含敏感数据
- **WHEN** 加密/解密操作失败
- **THEN** 系统 SHALL 记录错误类型和堆栈
- **AND** 系统 SHALL NOT 记录输入数据
- **AND** 系统 SHALL NOT 记录密钥或密码

---

## MODIFIED Requirements

### Requirement: Private Key Memory Sanitization
RSA私钥在内存中的驻留时间SHALL被最小化，使用完毕后立即擦除。

#### Scenario: 私钥使用后立即擦除
- **WHEN** RSA私钥使用完毕（加密/解密/签名操作完成）
- **THEN** SHALL立即清除内存中的密钥材料
- **AND** SHALL使用`Arrays.fill()`将密钥字节填充为零
- **AND** SHALL将密钥引用设为null

#### Scenario: AutoCloseable私钥管理
- **WHEN** 解密或加载RSA私钥
- **THEN** SHALL返回实现AutoCloseable接口的包装类
- **AND** SHALL在try-with-resources块中使用私钥
- **AND** SHALL在close()方法中擦除内存

#### Scenario: 私钥驻留时间限制
- **GIVEN** RSA私钥已解密到内存
- **WHEN** 私钥在内存中驻留超过操作所需时间（如>5秒）
- **THEN** SHALL视为安全违规
- **AND** SHALL记录安全警告日志
- **AND** SHALL主动触发内存擦除

#### Scenario: 使用 SensitiveData 包装私钥
- **WHEN** 从 DataKey 解密 RSA 私钥
- **THEN** 系统 SHALL 使用 `SensitiveData<byte[]>` 包装私钥
- **AND** 系统 SHALL 在 try-with-resources 块中使用私钥
- **AND** 系统 SHALL 在块结束时自动清零私钥内存

---

### Requirement: Key Migration Security
密钥迁移过程SHALL保护密钥材料不被泄露。

#### Scenario: 迁移过程内存保护
- **WHEN** 迁移密钥
- **THEN** SHALL尽量减少密钥在内存中的停留时间
- **AND** 迁移完成后应清除临时副本
- **AND** 应使用`char[]`而非String存储密码

#### Scenario: 迁移失败不泄露密钥
- **GIVEN** 密钥迁移失败
- **WHEN** 处理迁移失败
- **THEN** SHALL保护密钥材料不被泄露到日志
- **AND** 错误消息不应包含密钥信息
- **AND** 应记录失败原因但不记录敏感数据

#### Scenario: 迁移完整性验证
- **WHEN** 密钥迁移完成
- **THEN** SHALL验证迁移后的密钥可用性
- **AND** 应测试加密和解密操作
- **AND** 验证失败时SHALL回滚或保留旧密钥

#### Scenario: 迁移时使用 SensitiveData 包装
- **WHEN** 在内存中持有旧密钥和新密钥
- **THEN** 系统 SHALL 使用 `SensitiveData<T>` 包装两者
- **AND** 系统 SHALL 在验证完成后立即清零旧密钥
- **AND** 系统 SHALL 确保在任何异常情况下都执行清零
