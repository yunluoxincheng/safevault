# user-interaction-improvement Specification Delta

## ADDED Requirements

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
