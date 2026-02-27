# 网络安全规格 - 第一阶段

## ADDED Requirements

### Requirement: HTTPS Certificate Validation
Android客户端SHALL验证服务器SSL/TLS证书，不得绕过证书验证。

#### Scenario: 默认使用系统证书验证
- **WHEN** 创建OkHttpClient实例（Release构建）
- **THEN** SHALL NOT设置自定义的`X509TrustManager`
- **AND** SHALL NOT设置自定义的`hostnameVerifier`
- **AND** SHALL使用Android系统的默认证书验证
- **AND** 证书验证失败时连接应失败

#### Scenario: 不信任所有证书
- **GIVEN** 网络配置代码
- **WHEN** 审查SSL/TLS设置
- **THEN** SHALL NOT存在信任所有证书的代码
- **AND** SHALL NOT包含`checkServerTrusted()`空实现
- **AND** SHALL NOT包含返回`true`的`hostnameVerifier`

#### Scenario: Debug构建域名白名单例外（开发便利性）
- **GIVEN** `BuildConfig.DEBUG` 为 true
- **WHEN** 连接到开发环境域名（如 `frp-hat.com`、`172.17.176.22`、`localhost` 等）
- **THEN** MAY 使用自定义 SSL 配置，仅对白名单域名接受自签证书
- **AND** SHALL 使用域名白名单限制范围
- **AND** SHALL NOT 对生产环境域名绕过证书验证
- **AND** Release构建SHALL NOT包含此例外

---

### Requirement: Network Security Configuration
网络安全配置SHALL禁用明文HTTP，强制使用HTTPS。

#### Scenario: 禁用明文HTTP流量
- **GIVEN** `network_security_config.xml`
- **WHEN** 配置网络安全策略
- **THEN** SHALL设置`cleartextTrafficPermitted="false"`
- **AND** 所有HTTP请求应使用HTTPS

#### Scenario: 本地开发允许明文（可选）
- **GIVEN** 开发环境需要访问本地HTTP服务
- **WHEN** 配置开发环境网络安全
- **THEN** 可为开发域名添加例外
- **AND** 生产环境配置不应包含例外

---

### Requirement: API Response Security
API响应SHALL不包含敏感的调试信息或内部实现细节。

#### Scenario: 错误响应不泄露敏感信息
- **WHEN** API返回错误响应
- **THEN** SHALL NOT包含堆栈跟踪
- **AND** SHALL NOT包含内部路径
- **AND** SHALL NOT包含数据库错误详情
- **AND** 应返回通用错误消息和错误代码

#### Scenario: 生产环境禁用详细错误
- **GIVEN** 生产环境配置
- **WHEN** 发生错误
- **THEN** SHALL返回精简的错误消息
- **AND** 详细错误仅记录到服务器日志
- **AND** 日志级别应设置为INFO或WARN

---

### Requirement: WebSocket Connection Security
WebSocket连接SHALL使用WSS（加密）并验证来源。

#### Scenario: WebSocket使用WSS
- **WHEN** 建立WebSocket连接
- **THEN** SHALL使用`wss://`协议（加密）
- **AND** SHALL NOT使用`ws://`协议（明文）
- **AND** SHALL验证服务器证书

#### Scenario: WebSocket握手验证来源
- **WHEN** WebSocket握手请求
- **THEN** SHALL验证`Origin`头
- **AND** SHALL仅允许允许的域名
- **AND** 未授权来源应被拒绝
