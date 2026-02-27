# crypto-security Spec Delta (安全随机填充)

## ADDED Requirements

### Requirement: 加密数据随机填充
系统 SHALL 在加密前对明文进行随机填充，防止通过密文长度推断明文长度。

#### Scenario: 填充到固定块大小
- **GIVEN** 明文数据为 `byte[]`
- **WHEN** 执行填充操作
- **THEN** 系统 SHALL 填充到 256 字节的倍数
- **AND** 系统 SHALL 使用 `SecureRandom` 生成随机填充字节
- **AND** 系统 SHALL 在最后一个字节记录填充长度

#### Scenario: 随机填充内容
- **WHEN** 生成填充字节
- **THEN** 系统 SHALL 使用加密安全的随机数生成器
- **AND** 系统 SHALL NOT 使用 `\0` 或固定模式
- **AND** 系统 SHALL 确保填充内容不可预测

#### Scenario: 移除填充恢复明文
- **GIVEN** 填充后的 `byte[]`
- **WHEN** 执行移除填充操作
- **THEN** 系统 SHALL 从最后一个字节读取填充长度
- **AND** 系统 SHALL 验证填充长度在合理范围 (1, 256]
- **AND** 系统 SHALL 返回原始明文数据

---

### Requirement: 填充作用在字节数组
系统 SHALL 在 UTF-8 编码的字节数组上执行填充，而非字符串。

#### Scenario: 字符串转字节后填充
- **WHEN** 填充字符串类型数据
- **THEN** 系统 SHALL 先转为 UTF-8 编码的 `byte[]`
- **AND** 系统 SHALL 在字节数组上执行填充
- **AND** 系统 SHALL NOT 使用 `String.length()` 计算长度

#### Scenario: 填充长度计算
- **WHEN** 计算填充长度
- **THEN** 系统 SHALL 基于 `byte[].length` 计算
- **AND** 系统 SHALL 考虑 UTF-8 多字节字符

---

### Requirement: 填充版本控制
系统 SHALL 支持带填充和不带填充的加密数据格式。

#### Scenario: 新数据使用填充格式
- **WHEN** 加密新数据
- **THEN** 系统 SHALL 使用带填充的格式（v2）
- **AND** 系统 SHALL 在密文中包含版本标识

#### Scenario: 旧数据兼容解密
- **GIVEN** 旧数据使用不带填充的格式（v1）
- **WHEN** 解密旧数据
- **THEN** 系统 SHALL 自动识别 v1 格式
- **AND** 系统 SHALL 直接解密，不移除填充

#### Scenario: 混合格式数据存储
- **GIVEN** 数据库中同时存在 v1 和 v2 格式数据
- **WHEN** 读取密码列表
- **THEN** 系统 SHALL 正确解密两种格式
- **AND** 系统 SHALL 在后台静默迁移 v1 到 v2

---

### Requirement: 数据迁移到填充格式
系统 SHALL 在应用更新后自动迁移旧数据到新格式。

#### Scenario: 首次启动后触发迁移
- **GIVEN** 用户更新到支持填充的版本
- **AND** 数据库中存在 v1 格式数据
- **WHEN** 用户首次解锁应用
- **THEN** 系统 SHALL 在后台启动迁移任务
- **AND** 系统 SHALL 显示"正在优化数据安全..."提示

#### Scenario: 批量迁移密码项
- **WHEN** 执行迁移任务
- **THEN** 系统 SHALL 遍历所有密码项
- **AND** 系统 SHALL 解密 v1 格式数据
- **AND** 系统 SHALL 使用 v2 格式重新加密
- **AND** 系统 SHALL 更新数据库记录

#### Scenario: 迁移失败回滚
- **GIVEN** 迁移过程中发生错误
- **WHEN** 检测到迁移失败
- **THEN** 系统 SHALL 停止迁移任务
- **AND** 系统 SHALL 保持 v1 数据可用
- **AND** 系统 SHALL 记录错误日志供用户报告

---

### Requirement: 填充性能优化
系统 SHALL 优化填充操作的性能和内存占用。

#### Scenario: 填充操作耗时
- **WHEN** 执行填充操作
- **THEN** 系统 SHALL 在 1ms 内完成（256 字节填充）
- **AND** 系统 SHALL 不显著增加加密延迟

#### Scenario: 填充数据大小控制
- **WHEN** 计算填充后的数据大小
- **THEN** 系统 SHALL 确保每个字段最多增加 256 字节
- **AND** 系统 SHALL 评估数据库总增长量
- **AND** 系统 SHALL 记录大小增长统计

---

## MODIFIED Requirements

### Requirement: AEAD Encryption with Integrity Verification
所有加密操作 SHALL 使用 AEAD 模式（如 AES-GCM），在加密前对明文执行随机填充，提供机密性和完整性保护。

#### Scenario: AES-GCM加密参数
- **WHEN** 执行AES加密操作
- **THEN** SHALL使用AES/GCM/NoPadding模式
- **AND** SHALL使用128位认证标签（GCM_TAG_LENGTH）
- **AND** SHALL使用96位（12字节）IV
- **AND** SHALL为每次加密生成新的随机IV

#### Scenario: AES-GCM加密前填充
- **WHEN** 执行AES加密操作
- **THEN** 系统 SHALL 先对明文执行随机填充
- **AND** 系统 SHALL 使用 AES/GCM/NoPadding 加密填充后的数据
- **AND** 系统 SHALL 确保填充不影响认证标签验证

#### Scenario: GCM认证标签验证
- **WHEN** 解密AES-GCM加密的数据
- **THEN** SHALL自动验证认证标签
- **AND** 如果密文被篡改，SHALL抛出AEADBadTagException
- **AND** SHALL NOT返回部分解密数据
- **AND** SHALL记录完整性验证失败的安全事件

#### Scenario: 解密后移除填充
- **WHEN** AES-GCM解密成功
- **THEN** 系统 SHALL 从解密数据中移除随机填充
- **AND** 系统 SHALL 返回原始明文
- **AND** 系统 SHALL 验证填充格式的正确性

#### Scenario: 加密数据包格式
- **WHEN** 存储或传输加密数据
- **THEN** SHALL包含IV、密文和认证标签
- **AND** SHALL使用标准格式（如IV在前，密文在后）
- **AND** SHALL支持Base64编码以便存储
- **AND** SHALL包含版本标识以便未来升级
- **AND** SHALL支持带填充和不带填充两种格式
