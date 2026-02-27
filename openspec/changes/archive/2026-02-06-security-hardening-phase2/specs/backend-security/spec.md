# 后端安全规格 - 第二阶段

## ADDED Requirements

### Requirement: RSA Encryption Padding
RSA加密SHALL使用OAEP填充方案，不得使用不安全的PKCS1填充。

#### Scenario: 新分享使用OAEP填充
- **WHEN** 创建新的密码分享
- **THEN** SHALL使用RSA/ECB/OAEPWithSHA-256AndMGF1Padding
- **AND** SHALL标记分享版本为"v2"
- **AND** SHALL使用OAEP加密AES会话密钥

#### Scenario: 旧分享保持PKCS1兼容
- **GIVEN** 历史分享数据使用PKCS1Padding加密
- **WHEN** 解密旧分享数据
- **THEN** SHALL使用RSA/ECB/PKCS1Padding解密
- **AND** SHALL识别版本为"v1"
- **AND** 解密应成功

#### Scenario: 根据版本选择解密算法
- **WHEN** 解密分享数据
- **THEN** SHALL检查分享版本标识
- **AND** 如果版本为"v1"，使用PKCS1Padding
- **AND** 如果版本为"v2"，使用OAEPWithSHA-256AndMGF1Padding
- **AND** SHALL抛出异常当版本未知

---

### Requirement: Password Hashing Configuration
密码哈希算法SHALL支持Argon2id和PBKDF2，新用户应使用Argon2id。

#### Scenario: Argon2id参数可配置
- **WHEN** 配置Argon2id参数
- **THEN** SHALL从配置文件读取参数
- **AND** SHALL支持环境变量`ARGON2_TIME_COST`、`ARGON2_MEMORY_COST`、`ARGON2_PARALLELISM`
- **AND** 默认值应为t=3, m=64MB, p=4

#### Scenario: 新用户使用Argon2id
- **WHEN** 新用户注册或设置密码
- **THEN** SHALL使用Argon2id算法
- **AND** SHALL在用户数据库记录算法类型为"ARGON2ID"
- **AND** SHALL使用配置的Argon2id参数

#### Scenario: 旧用户PBKDF2兼容
- **GIVEN** 旧用户使用PBKDF2哈希
- **WHEN** 验证旧用户密码
- **THEN** SHALL检测用户密码哈希算法类型
- **AND** 如果算法为"PBKDF2"或未设置，使用PBKDF2验证
- **AND** 密码验证应成功

#### Scenario: 密码哈希算法版本化存储
- **WHEN** 存储用户密码哈希
- **THEN** SHALL同时存储算法类型标识
- **AND** 标识应为"ARGON2ID"或"PBKDF2"
- **AND** 用于选择正确的验证算法

---

### Requirement: Encryption Versioning
加密操作SHALL支持版本标识，用于算法升级和向后兼容。

#### Scenario: 分享数据包含版本标识
- **WHEN** 创建加密分享数据包
- **THEN** SHALL包含版本字段（"v1"或"v2"）
- **AND** 版本标识用于选择解密算法
- **AND** 默认版本应为"v2"

#### Scenario: 数据库存储版本信息
- **WHEN** 保存分享数据到数据库
- **THEN** SHALL在`contact_shares`表存储`encryption_version`字段
- **AND** 新记录默认为"v2"
- **AND** 旧记录保持为"v1"

#### Scenario: 版本化加密服务接口
- **WHEN** 加密服务提供解密接口
- **THEN** SHALL接受版本参数
- **AND** SHALL根据版本选择正确算法
- **AND** SHALL支持版本"v1"和"v2"

---

### Requirement: Migration Monitoring
密钥和加密迁移过程SHALL被监控和记录。

#### Scenario: 记录迁移事件
- **WHEN** 用户执行密钥迁移或加密升级
- **THEN** SHALL记录迁移事件
- **AND** 应包含用户ID、迁移类型、结果
- **AND** 应记录迁移时间戳

#### Scenario: 迁移失败告警
- **WHEN** 迁移失败率超过阈值
- **THEN** SHALL触发告警
- **AND** 应通知运维团队
- **AND** 应记录详细错误信息

#### Scenario: 迁移统计报告
- **WHEN** 查询迁移统计
- **THEN** SHALL提供迁移成功率
- **AND** 应提供迁移失败率
- **AND** 应按版本分组统计
