# contact-sharing Spec Delta (安全码验证)

## ADDED Requirements

### Requirement: 安全码生成
系统 SHALL 为每个用户的公钥生成唯一的安全指纹，用于身份验证。

#### Scenario: 生成短指纹
- **GIVEN** 用户的 RSA 公钥
- **WHEN** 生成短指纹
- **THEN** 系统 SHALL 使用 SHA-256 哈希公钥编码
- **AND** 系统 SHALL 取前 4 字节转换为 5 组 2 位数字
- **AND** 系统 SHALL 返回格式 "12-34-56-78-90"

#### Scenario: 生成长指纹
- **GIVEN** 用户的 RSA 公钥
- **WHEN** 生成长指纹
- **THEN** 系统 SHALL 使用 SHA-256 哈希公钥编码
- **AND** 系统 SHALL 返回完整的 64 字符十六进制字符串
- **AND** 系统 SHALL 支持复制和分享

#### Scenario: 指纹生成一致性
- **GIVEN** 同一个公钥
- **WHEN** 多次生成指纹
- **THEN** 系统 SHALL 每次生成相同的结果
- **AND** 不同公钥 SHALL 生成不同的指纹

---

### Requirement: 分享前安全码验证
系统 SHALL 在首次向好友分享密码前显示安全码验证界面。

#### Scenario: 首次分享显示验证界面
- **GIVEN** 用户首次向某好友分享密码
- **AND** 该好友未被验证
- **WHEN** 用户确认分享
- **THEN** 系统 SHALL 显示安全码验证对话框
- **AND** 系统 SHALL 显示双方的安全码
- **AND** 系统 SHALL 提示"请通过线下渠道（面对面、电话）确认对方安全码"

#### Scenario: 用户确认验证
- **GIVEN** 安全码验证对话框已显示
- **WHEN** 用户点击"已验证"按钮
- **THEN** 系统 SHALL 标记该好友为"已验证"
- **AND** 系统 SHALL 记录验证时间戳
- **AND** 系统 SHALL 继续分享流程

#### Scenario: 用户报告不匹配
- **GIVEN** 安全码验证对话框已显示
- **WHEN** 用户点击"不匹配"按钮
- **THEN** 系统 SHALL 中止分享流程
- **AND** 系统 SHALL 显示安全警告
- **AND** 系统 SHALL 建议用户联系好友确认

#### Scenario: 用户跳过验证
- **GIVEN** 安全码验证对话框已显示
- **WHEN** 用户点击"跳过"按钮
- **THEN** 系统 SHALL 继续分享流程
- **AND** 系统 SHALL 标记该好友为"未验证"
- **AND** 系统 SHALL 在下次分享时再次提示

---

### Requirement: 已验证用户快速分享
系统 SHALL 为已验证的用户提供简化的分享流程。

#### Scenario: 已验证用户显示确认
- **GIVEN** 用户向已验证的好友分享密码
- **WHEN** 用户确认分享
- **THEN** 系统 SHALL 显示简化的确认对话框
- **AND** 系统 SHALL 显示"✓ 已验证"图标
- **AND** 系统 SHALL 不显示完整安全码

#### Scenario: 已验证用户可重新验证
- **GIVEN** 用户查看已验证好友的详情
- **WHEN** 用户点击"重新验证"按钮
- **THEN** 系统 SHALL 显示完整安全码
- **AND** 系统 SHALL 支持用户重新核对

---

### Requirement: 公钥变化警告
系统 SHALL 检测好友公钥变化并警告用户。

#### Scenario: 检测公钥变化
- **GIVEN** 用户已验证某好友的安全码
- **WHEN** 该好友的公钥发生变化
- **THEN** 系统 SHALL 检测到公钥不匹配
- **AND** 系统 SHALL 清除旧的验证状态
- **AND** 系统 SHALL 显示安全警告

#### Scenario: 公钥变化警告内容
- **WHEN** 显示公钥变化警告
- **THEN** 系统 SHALL 说明"安全码已变化"
- **AND** 系统 SHALL 显示旧安全码和新安全码
- **AND** 系统 SHALL 询问用户是否联系好友验证
- **AND** 系统 SHALL 提供"重新验证"和"联系用户"选项

---

### Requirement: 安全码详情页面
系统 SHALL 提供安全码详情页面供用户查看和分享。

#### Scenario: 查看完整安全码
- **GIVEN** 用户在分享界面
- **WHEN** 用户点击"查看完整安全码"
- **THEN** 系统 SHALL 打开安全码详情页面
- **AND** 系统 SHALL 显示长指纹（64 字符十六进制）
- **AND** 系统 SHALL 显示短指纹（5 组 2 位数字）
- **AND** 系统 SHALL 显示验证历史

#### Scenario: 复制安全码
- **WHEN** 用户点击"复制安全码"
- **THEN** 系统 SHALL 将长指纹复制到剪贴板
- **AND** 系统 SHALL 显示"已复制"提示
- **AND** 系统 SHALL 30 秒后自动清除剪贴板

#### Scenario: 分享安全码
- **WHEN** 用户点击"分享安全码"
- **THEN** 系统 SHALL 打开系统分享菜单
- **AND** 系统 SHALL 提供长指纹文本
- **AND** 系统 SHALL 允许用户通过其他渠道（短信、邮件）发送

---

### Requirement: 验证状态持久化
系统 SHALL 在本地数据库中存储验证状态。

#### Scenario: 保存验证状态
- **WHEN** 用户验证好友的安全码
- **THEN** 系统 SHALL 存储到 `verified_safety_numbers` 表
- **AND** 系统 SHALL 记录用户名、公钥、指纹、验证时间
- **AND** 系统 SHALL 支持查询验证状态

#### Scenario: 查询验证状态
- **WHEN** 用户向好友分享密码
- **THEN** 系统 SHALL 查询该好友的验证状态
- **AND** 系统 SHALL 根据状态显示不同的 UI

---

### Requirement: 安全码验证用户引导
系统 SHALL 引导用户理解安全码验证的重要性。

#### Scenario: 首次使用显示教程
- **GIVEN** 用户首次使用分享功能
- **WHEN** 用户打开分享界面
- **THEN** 系统 SHALL 显示安全码验证教程
- **AND** 系统 SHALL 解释"什么是安全码"
- **AND** 系统 SHALL 说明"为什么需要验证"
- **AND** 系统 SHALL 提供"了解更多"链接

#### Scenario: 验证对话框文案
- **WHEN** 显示验证对话框
- **THEN** 系统 SHALL 使用清晰的文案
- **AND** 系统 SHALL 避免"指纹"、"哈希"等技术术语
- **AND** 系统 SHALL 使用"安全码"等用户友好的词汇
