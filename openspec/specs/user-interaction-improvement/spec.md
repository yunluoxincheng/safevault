# user-interaction-improvement Specification

## Purpose
TBD - created by archiving change enhance-frontend-ui. Update Purpose after archive.
## Requirements
### Requirement: 密码可见性切换
密码输入框 SHALL 提供显示/隐藏密码的切换功能。

#### Scenario: 编辑页面密码可见性切换
**Given** 用户在编辑密码页面
**When** 用户点击密码输入框的显示/隐藏图标
**Then** 密码 SHALL 以明文形式显示
**And** 图标 SHALL 变为"隐藏"状态
**When** 用户再次点击图标
**Then** 密码 SHALL 以掩码形式显示
**And** 图标 SHALL 变为"显示"状态

#### Scenario: 登录页面密码可见性切换
**Given** 用户在登录页面
**When** 用户点击密码输入框的显示/隐藏图标
**Then** 主密码 SHALL 以明文形式显示
**And** 图标状态 SHALL 相应更新

#### Scenario: 详情页面密码可见性切换
**Given** 用户在密码详情页面
**When** 用户点击密码字段的显示/隐藏图标
**Then** 密码 SHALL 以明文形式显示
**And** 系统 SHALL 提供复制密码的快捷按钮

### Requirement: 搜索历史和建议
应用 SHALL 记录用户的搜索历史并提供搜索建议。

#### Scenario: 记录搜索历史
**Given** 用户在密码列表页面
**When** 用户执行搜索操作
**Then** 搜索关键词 SHALL 被保存到搜索历史
**And** 系统 SHALL 最多保存最近 10 条搜索记录
**And** 重复搜索 SHALL 更新时间戳而不创建新记录

#### Scenario: 显示搜索建议
**Given** 用户点击搜索框
**When** 搜索框获得焦点
**Then** 系统 SHALL 显示搜索历史列表（如果存在）
**And** 搜索历史 SHALL 按最近使用时间排序
**And** 点击历史记录 SHALL 执行搜索

#### Scenario: 清除搜索历史
**Given** 用户有搜索历史记录
**When** 用户在设置中清除搜索历史
**Then** 所有搜索记录 SHALL 被删除
**And** 搜索框 SHALL 不再显示建议

### Requirement: 快捷操作优化
密码列表项 SHALL 提供高效的快捷操作方式。

#### Scenario: 长按显示操作菜单
**Given** 用户在密码列表页面
**When** 用户长按某个密码项
**Then** 系统 SHALL 弹出操作菜单
**And** 菜单 SHALL 包含：查看、编辑、复制、删除选项
**And** 系统 SHALL 提供震动反馈（如果设备支持）

#### Scenario: 滑动操作（可选高级功能）
**Given** 用户在密码列表页面
**When** 用户向左滑动某个密码项
**Then** 系统 SHALL 显示编辑和删除操作按钮
**And** 点击按钮 SHALL 执行相应操作
**And** 点击其他区域 SHALL 取消操作

#### Scenario: 操作反馈动画
**Given** 用户执行快捷操作
**When** 操作成功完成
**Then** 系统 SHALL 显示操作反馈（如 Toast 消息）
**And** 系统 SHALL 播放按钮点击动画
**And** 列表 SHALL 根据操作更新

### Requirement: 输入验证和反馈
表单输入 SHALL 提供实时验证和清晰的反馈。

#### Scenario: 密码强度实时反馈
**Given** 用户在编辑或新建密码
**When** 用户输入密码
**Then** 系统 SHALL 实时显示密码强度指示器
**And** 系统 SHALL 使用颜色编码（红色=弱，黄色=中等，绿色=强）
**And** 系统 SHALL 提供改进建议

#### Scenario: 必填字段验证
**Given** 用户在编辑或新建密码
**When** 用户尝试保存未填写必填字段
**Then** 保存按钮 SHALL 保持禁用状态
**And** 系统 SHALL 显示错误提示

### Requirement: 撤销和重做
应用 SHALL 支持常见操作的撤销功能。

#### Scenario: 删除操作撤销
**Given** 用户删除了某个密码项
**When** 删除操作完成后显示 Snackbar
**Then** Snackbar SHALL 包含"撤销"按钮
**When** 用户点击"撤销"
**Then** 被删除的项目 SHALL 恢复
**And** 列表 SHALL 更新

### Requirement: 生物识别启用前身份验证
用户启用生物识别解锁功能前，系统 SHALL 要求用户验证身份以防止未授权访问。

#### Scenario: 首次启用生物识别前验证身份
- **GIVEN** 用户在账户安全设置页面且生物识别功能未启用
- **WHEN** 用户点击生物识别开关
- **THEN** 系统 SHALL 要求用户验证身份
- **AND** 系统 SHALL 显示主密码输入对话框或生物识别验证提示
- **AND** 仅在验证成功后启用生物识别功能
- **AND** 验证失败时 SHALL 显示错误提示并保持开关关闭状态

#### Scenario: 已启用生物识别时调整设置
- **GIVEN** 用户已启用生物识别功能
- **WHEN** 用户调整生物识别相关设置
- **THEN** 系统 SHALL 允许直接操作（无需重复验证）
- **AND** 用户关闭生物识别功能 SHALL 直接生效

#### Scenario: 生物识别验证失败处理
- **GIVEN** 用户尝试启用生物识别功能
- **WHEN** 身份验证失败（密码错误或生物识别失败）
- **THEN** 系统 SHALL 显示验证失败提示
- **AND** 系统 SHALL 保持生物识别开关为关闭状态
- **AND** 系统 SHALL 允许用户重新尝试验证

### Requirement: 蓝牙传输进度显示
蓝牙传输密码分享数据时，系统 SHALL 显示实时传输进度，提供用户掌控感。

#### Scenario: 发送数据时显示进度
- **GIVEN** 用户通过蓝牙分享密码给另一设备
- **WHEN** 传输开始
- **THEN** 系统 SHALL 在分享结果页面显示进度条
- **AND** 系统 SHALL 实时更新进度百分比（0-100%）
- **AND** 系统 SHALL 显示进度文本（如"正在发送 45%"）
- **AND** 传输完成后系统 SHALL 隐藏进度指示器

#### Scenario: 接收数据时显示进度
- **GIVEN** 用户通过蓝牙接收另一设备的密码分享
- **WHEN** 接收开始
- **THEN** 系统 SHALL 显示接收进度
- **AND** 进度 SHALL 从 0% 增长到 100%
- **AND** 接收完成后系统 SHALL 自动解析并显示分享内容

#### Scenario: 传输中断时的状态处理
- **GIVEN** 蓝牙传输正在进行中
- **WHEN** 传输因连接断开而中断
- **THEN** 系统 SHALL 停止在当前进度位置
- **AND** 系统 SHALL 显示错误提示
- **AND** 系统 SHALL 允许用户重新尝试传输

### Requirement: 重复凭据警告对话框
当检测到用户尝试保存重复的凭据时，系统 SHALL 显示 Material Design 3 对话框，让用户选择操作。

#### Scenario: 检测到重复凭据时显示对话框
- **GIVEN** 自动填充服务检测到用户输入的凭据与已存在的凭据重复
- **WHEN** 用户点击保存按钮
- **THEN** 系统 SHALL 显示警告对话框
- **AND** 对话框标题 SHALL 为"检测到重复凭据"
- **AND** 对话框内容 SHALL 显示现有凭据的标题和用户名
- **AND** 对话框 SHALL 提供"覆盖现有"和"取消"两个操作按钮

#### Scenario: 覆盖现有凭据
- **GIVEN** 重复凭据对话框已显示
- **WHEN** 用户点击"覆盖现有"按钮
- **THEN** 系统 SHALL 更新现有密码项的内容
- **AND** 系统 SHALL 保存更新后的凭据
- **AND** 系统 SHALL 关闭自动填充保存界面
- **AND** 系统 SHALL 显示保存成功提示

#### Scenario: 取消保存操作
- **GIVEN** 重复凭据对话框已显示
- **WHEN** 用户点击"取消"按钮或返回键
- **THEN** 系统 SHALL 关闭对话框
- **AND** 系统 SHALL 保持自动填充保存界面打开
- **AND** 用户 SHALL 可以继续编辑或取消保存

#### Scenario: 对话框 Material Design 3 样式
- **GIVEN** 重复凭据对话框显示
- **WHEN** 用户查看对话框
- **THEN** 对话框 SHALL 使用 Material Design 3 的样式规范
- **AND** "覆盖现有"按钮 SHALL 使用警示色（红色）
- **AND** 对话框 SHALL 正确支持深色模式
- **AND** 对话框 SHALL 使用圆角和适当的内边距

### Requirement: 云端分享链接生成
用户通过云端分享密码时，系统 SHALL 上传分享令牌到服务器并生成可分享的链接。

#### Scenario: 生成云端分享链接
- **GIVEN** 用户选择通过云端方式分享密码
- **WHEN** 分享配置完成（过期时间、权限等）
- **THEN** 系统 SHALL 将加密的分享令牌上传到服务器
- **AND** 系统 SHALL 接收服务器返回的分享 ID
- **AND** 系统 SHALL 生成格式为 `https://safevault.app/share/{shareId}` 的链接
- **AND** 系统 SHALL 显示分享链接供用户复制或分享

#### Scenario: 上传失败时的错误处理
- **GIVEN** 用户尝试通过云端分享密码
- **WHEN** 上传分享令牌到服务器失败
- **THEN** 系统 SHALL 显示错误提示
- **AND** 系统 SHALL 说明失败原因（如网络错误、服务器错误）
- **AND** 系统 SHALL 允许用户重试或选择其他分享方式

#### Scenario: 分享链接复制功能
- **GIVEN** 云端分享链接已生成
- **WHEN** 用户点击复制链接按钮
- **THEN** 系统 SHALL 将分享链接复制到剪贴板
- **AND** 系统 SHALL 显示"链接已复制"的 Toast 提示
- **AND** 用户可以通过粘贴将链接分享给他人

### Requirement: 注册后邮箱信息持久化

系统 SHALL 在用户注册成功后保存邮箱信息到本地持久化存储，以便后续功能（如身份码生成）使用。

#### Scenario: 注册成功后保存邮箱

- **GIVEN** 用户完成邮箱验证并设置主密码
- **WHEN** 注册流程成功完成
- **THEN** 系统 SHALL 调用 `TokenManager.saveLastLoginEmail(email)` 保存邮箱
- **AND** 邮箱 SHALL 被持久化到 SharedPreferences
- **AND** 后续可通过 `TokenManager.getLastLoginEmail()` 获取邮箱

#### Scenario: 身份码页面获取邮箱

- **GIVEN** 用户已注册并登录
- **WHEN** 用户打开"我的身份码"页面
- **THEN** 系统 SHALL 通过 `TokenManager.getLastLoginEmail()` 获取邮箱
- **AND** 使用获取的邮箱生成身份二维码
- **AND** 二维码 SHALL 正确显示在页面上

---

### Requirement: 删除好友的云端同步

系统 SHALL 在用户删除联系人时，同时删除云端的好友关系和本地的联系人记录。

#### Scenario: 删除好友时同步云端

- **GIVEN** 用户在联系人列表页面
- **WHEN** 用户长按联系人并选择删除
- **THEN** 系统 SHALL 先调用 `FriendServiceApi.deleteFriend(cloudUserId)` 删除云端好友
- **AND** 云端删除成功后，系统 SHALL 调用 `ContactManager.deleteContact(contactId)` 删除本地记录
- **AND** 系统 SHALL 刷新联系人列表

#### Scenario: 删除好友失败处理

- **GIVEN** 用户尝试删除联系人
- **WHEN** 云端 API 调用失败
- **THEN** 系统 SHALL 显示错误提示："删除失败，请重试"
- **AND** 系统 SHALL 保持本地联系人记录不变
- **AND** 系统 SHALL 允许用户重新尝试删除

---

### Requirement: 搜索用户时过滤已添加好友

系统 SHALL 在用户搜索时，从搜索结果中过滤掉已经是好友的用户。

#### Scenario: 搜索结果过滤已添加好友

- **GIVEN** 用户已添加某些好友
- **WHEN** 用户在搜索框输入关键词
- **THEN** 系统 SHALL 从本地加载已添加好友的 cloudUserId 列表
- **AND** 系统 SHALL 从搜索结果中过滤掉匹配的好友
- **AND** 只显示尚未添加的用户

#### Scenario: 尝试添加重复好友

- **GIVEN** 用户尝试添加已存在的好友
- **WHEN** 搜索结果中不存在该好友（已被过滤）
- **THEN** 用户 SHALL 无法看到已添加的好友
- **AND** 系统 SHALL 避免重复添加的混淆

---

### Requirement: 联系人选择界面正确启动

系统 SHALL 使用 ActivityResultLauncher API 启动联系人选择界面并正确处理返回结果。

#### Scenario: 从分享界面打开联系人选择

- **GIVEN** 用户在密码分享界面
- **WHEN** 用户点击"选择联系人"按钮
- **THEN** 系统 SHALL 使用 ActivityResultLauncher 启动 ContactListActivity
- **AND** ContactListActivity SHALL 正常显示
- **AND** 用户 SHALL 能看到所有联系人

#### Scenario: 返回选中的联系人

- **GIVEN** 用户在 ContactListActivity 中选择了联系人
- **WHEN** 用户点击某个联系人
- **THEN** ContactListActivity SHALL 返回 RESULT_OK 和 contactId
- **AND** ShareActivity SHALL 接收返回结果
- **AND** 系统 SHALL 加载并显示选中的联系人信息

---

### Requirement: 主界面扫码入口

系统 SHALL 在主界面提供扫码入口，允许用户快速扫描分享二维码。

#### Scenario: 显示扫码按钮

- **GIVEN** 用户在主界面（MainActivity）
- **WHEN** 主界面加载完成
- **THEN** 系统 SHALL 在右下角显示 Floating Action Button
- **AND** 按钮 SHALL 显示扫码图标
- **AND** 按钮 SHALL 有内容描述"扫一扫"

#### Scenario: 点击扫码按钮

- **GIVEN** 用户在主界面
- **WHEN** 用户点击"扫一扫"按钮
- **THEN** 系统 SHALL 启动 ScanShareActivity
- **AND** ScanShareActivity SHALL 准备扫描二维码
- **AND** 用户 SHALL 能看到相机预览

---

### Requirement: 扫描分享二维码

系统 SHALL 允许用户扫描分享二维码并解析分享ID。

#### Scenario: 扫描成功的分享码

- **GIVEN** 用户在 ScanShareActivity
- **WHEN** 用户扫描格式为 `safevault://share/{shareId}` 的二维码
- **THEN** 系统 SHALL 解析出 shareId
- **AND** 系统 SHALL 跳转到 ReceiveShareActivity
- **AND** ReceiveShareActivity SHALL 加载并显示分享内容

#### Scenario: 扫描无效的二维码

- **GIVEN** 用户在 ScanShareActivity
- **WHEN** 用户扫描不是 SafeVault 分享码的二维码
- **THEN** 系统 SHALL 显示提示："这不是有效的分享二维码"
- **AND** 系统 SHALL 返回 ScanShareActivity 继续扫描
- **AND** 用户 SHALL 能重新扫描

---

### Requirement: 蓝牙设备状态标记

系统 SHALL 在蓝牙设备搜索时，明确标记已配对设备和新发现的设备。

#### Scenario: 显示已配对设备

- **GIVEN** 用户打开蓝牙设备选择对话框
- **WHEN** 对话框加载已配对设备
- **THEN** 系统 SHALL 在设备名称旁显示"已配对"标识
- **AND** 标识 SHALL 使用绿色（成功色）
- **AND** 用户 SHALL 能区分已配对设备

#### Scenario: 显示新发现的设备

- **GIVEN** 用户启动蓝牙设备扫描
- **WHEN** 系统发现新设备
- **THEN** 系统 SHALL 在设备名称旁显示"新设备"标识
- **AND** 标识 SHALL 使用主题色
- **AND** 用户 SHALL 能区分新发现的设备

#### Scenario: 设备列表排序

- **GIVEN** 蓝牙设备列表包含已配对和新设备
- **WHEN** 设备列表显示
- **THEN** 已配对设备 SHALL 显示在列表顶部
- **AND** 新发现的设备 SHALL 显示在已配对设备之后
- **AND** 用户 SHALL 优先看到常用设备

---

### Requirement: 移除 NFC 功能

系统 SHALL 完全移除 NFC 相关的功能，因为 Android 10+ 不支持点对点传输。

#### Scenario: 移除 NFC 相关代码

- **GIVEN** 项目包含 NFC 相关代码
- **WHEN** 执行移除操作
- **THEN** NFCTransferManager.java SHALL 被删除
- **AND** NFCSendActivity.java SHALL 被删除
- **AND** NFCReceiveActivity.java SHALL 被删除
- **AND** AndroidManifest.xml 中的 NFC 权限 SHALL 被移除

#### Scenario: 移除 NFC UI 入口

- **GIVEN** 分享界面包含 NFC 选项
- **WHEN** 执行移除操作
- **THEN** ShareActivity SHALL 不包含 NFC 相关按钮
- **AND** 所有布局文件 SHALL 不包含 NFC 相关 UI
- **AND** 用户 SHALL 无法看到 NFC 分享选项

