# 后端安全规格变更 - 添加第三阶段要求

## ADDED Requirements

### Requirement: JWT Signature Algorithm
JWT Token SHALL使用RS256（RSA-SHA256）非对称加密算法签名。

#### Scenario: RS256 Token生成
- **WHEN** 生成JWT Token
- **THEN** SHALL使用RSA私钥签名
- **AND** SHALL指定算法为RS256
- **AND** 私钥SHALL从环境变量读取
- **AND** 私钥SHALL至少2048位

#### Scenario: RS256 Token验证
- **WHEN** 验证JWT Token
- **THEN** SHALL使用RSA公钥验证签名
- **AND** SHALL仅接受RS256算法的Token
- **AND** SHALL拒绝HS256算法的Token

---

### Requirement: Token Expiration Time
Token过期时间SHALL限制在合理范围内以减少攻击窗口。

#### Scenario: 访问Token15分钟过期
- **WHEN** 生成访问Token
- **THEN** SHALL设置过期时间为15分钟
- **AND** SHALL使用当前时间戳加15分钟作为过期时间
- **AND** 应在Token中包含`exp`声明

#### Scenario: 刷新Token7天过期
- **WHEN** 生成刷新Token
- **THEN** SHALL设置过期时间为7天
- **AND** SHALL使用当前时间戳加7天作为过期时间
- **AND** 应在Token中包含`exp`声明

#### Scenario: Token过期验证
- **WHEN** 验证Token
- **THEN** SHALL检查`exp`声明
- **AND** 过期Token SHALL被拒绝
- **AND** 应返回401 Unauthorized

---

### Requirement: HMAC Signature Verification
签名验证SHALL使用完整的HMAC-SHA256算法，不得仅检查格式。

#### Scenario: HMAC签名计算
- **WHEN** 计算请求签名
- **THEN** SHALL使用HMAC-SHA256算法
- **AND** SHALL使用用户特定的盐值作为密钥
- **AND** SHALL对时间戳、邮箱、设备ID拼接数据签名
- **AND** 应返回Base64编码的签名

#### Scenario: HMAC签名验证
- **WHEN** 验证请求签名
- **THEN** SHALL计算期望的HMAC签名
- **AND** SHALL使用时间安全比较防止时序攻击
- **AND** 签名不匹配时SHALL抛出异常
- **AND** 应返回"签名验证失败"错误

#### Scenario: 时间戳验证
- **WHEN** 验证签名请求
- **THEN** SHALL检查请求时间戳
- **AND** 时间差超过5分钟SHALL被拒绝
- **AND** 应返回"请求时间戳无效"错误

---

### Requirement: API Rate Limiting
敏感API端点SHALL实施速率限制以防止暴力破解攻击。

#### Scenario: 登录API速率限制
- **WHEN** 调用登录API
- **THEN** SHALL限制每个IP地址每分钟最多5次请求
- **AND** 超过限制时SHALL返回429 Too Many Requests
- **AND** 应包含`Retry-After`头指示等待时间

#### Scenario: 注册API速率限制
- **WHEN** 调用注册API
- **THEN** SHALL限制每个IP地址每分钟最多3次请求
- **AND** SHALL限制每个邮箱地址每小时最多10次请求
- **AND** 超过限制时SHALL返回429 Too Many Requests

#### Scenario: 速率限制基于用户标识
- **WHEN** 用户已登录
- **THEN** 速率限制SHALL基于用户ID而非IP地址
- **AND** 应防止同一用户通过多IP绕过限制

#### Scenario: 速率限制窗口重置
- **GIVEN** 请求超过速率限制
- **WHEN** 时间窗口重置（如1分钟后）
- **THEN** 请求计数SHALL重置
- **AND** 用户SHALL可以继续请求

---

### Requirement: Concurrent Login Control
系统SHALL限制用户同时登录的设备数量。

#### Scenario: 设备数量限制
- **WHEN** 用户尝试登录
- **THEN** SHALL检查当前活跃设备数量
- **AND** 如果少于5台，允许登录
- **AND** 如果已达到5台，撤销最久未使用的设备
- **AND** 应记录新登录设备

#### Scenario: 设备信息记录
- **WHEN** 用户成功登录
- **THEN** SHALL记录设备ID
- **AND** SHALL记录设备名称
- **AND** SHALL记录登录时间
- **AND** SHALL记录最后活跃时间

#### Scenario: 设备列表查询
- **WHEN** 用户查询登录设备列表
- **THEN** SHALL返回所有活跃设备
- **AND** 应包含设备ID、设备名称、登录时间、最后活跃时间
- **AND** 应标识当前设备

#### Scenario: 远程设备移除
- **WHEN** 用户请求移除设备
- **THEN** SHALL撤销该设备的所有Token
- **AND** SHALL从设备列表中移除
- **AND** 被移除的设备后续API请求应被拒绝

#### Scenario: 全部登出
- **WHEN** 用户请求全部登出
- **THEN** SHALL撤销该用户的所有Token
- **AND** SHALL清除所有设备记录
- **AND** 用户需要在所有设备重新登录

---

### Requirement: Device Management API
系统SHALL提供设备管理的API端点。

#### Scenario: GET /v1/auth/devices
- **WHEN** 用户请求设备列表
- **THEN** SHALL返回HTTP 200和设备列表
- **AND** 每个设备应包含id、name、loginTime、lastActiveTime
- **AND** 应标识当前登录设备

#### Scenario: DELETE /v1/auth/devices/{deviceId}
- **WHEN** 用户请求移除指定设备
- **THEN** SHALL撤销该设备的Token
- **AND** SHALL从数据库移除设备记录
- **AND** SHALL返回HTTP 204 No Content

#### Scenario: POST /v1/auth/logout-all
- **WHEN** 用户请求全部登出
- **THEN** SHALL撤销该用户的所有Token
- **AND** SHALL清除所有设备记录
- **AND** SHALL返回HTTP 200

---

### Requirement: Rate Limiting Configuration
速率限制SHALL可配置以适应不同环境需求。

#### Scenario: 速率限制配置参数
- **WHEN** 配置速率限制
- **THEN** SHALL支持配置请求次数
- **AND** SHALL支持配置时间窗口（minute、hour、day）
- **AND** SHALL支持配置基于IP或用户ID的限制
- **AND** 配置SHALL在`application.yml`中定义

#### Scenario: 速率限制自定义
- **WHEN** 特定端点需要特殊限制
- **THEN** SHALL支持通过注解自定义
- **AND** 应允许配置不同的限制参数
- **AND** 示例：`@RateLimit(requests=10, per="hour")`
