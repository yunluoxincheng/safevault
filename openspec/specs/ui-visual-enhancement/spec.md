# ui-visual-enhancement Specification

## Purpose
TBD - created by archiving change enhance-frontend-ui. Update Purpose after archive.
## Requirements
### Requirement: 空状态展示优化
应用在数据为空时 SHALL 提供友好的视觉引导。

#### Scenario: 密码列表为空时显示引导
**Given** 用户打开应用且没有保存任何密码
**When** 密码列表页面显示
**Then** 系统 SHALL 显示空状态插图（图标）
**And** 系统 SHALL 显示引导文案（"点击右下角按钮添加第一个密码"）
**And** 系统 SHALL 提供快捷操作按钮（立即添加）

#### Scenario: 搜索无结果时显示提示
**Given** 用户执行搜索操作
**When** 搜索结果为空
**Then** 系统 SHALL 显示空状态插图
**And** 系统 SHALL 显示"未找到匹配的密码"提示
**And** 系统 SHALL 提供清除搜索按钮

### Requirement: 加载状态展示
应用在数据加载时 SHALL 提供清晰的视觉反馈，并正确处理系统UI边距。

#### Scenario: 下拉刷新时显示加载指示器
- **GIVEN** 用户在密码列表页面
- **WHEN** 用户执行下拉刷新操作
- **THEN** SwipeRefreshLayout SHALL 显示刷新动画
- **AND** 刷新完成后动画 SHALL 自动消失
- **AND** 加载指示器 SHALL 不被系统UI遮挡

#### Scenario: 首次加载时显示进度指示
- **GIVEN** 用户打开应用或进入新页面
- **WHEN** 数据正在加载
- **THEN** 系统 SHALL 显示进度条或加载指示器
- **AND** 加载完成后系统 SHALL 隐藏进度指示器
- **AND** 进度指示器 SHALL 正确处理系统窗口边距

### Requirement: 页面过渡动画
应用在不同页面间切换时 SHALL 提供流畅的过渡动画，并正确处理系统UI。

#### Scenario: Fragment 切换动画
- **GIVEN** 用户在应用内导航
- **WHEN** 从列表页面切换到详情页面
- **THEN** 系统 SHALL 应用进入和退出过渡动画
- **AND** 动画时长 SHALL 在 200-300ms 之间
- **AND** 动画 SHALL 使用 Material Design 推荐的缓动曲线
- **AND** 目标页面 SHALL 正确处理系统窗口边距

#### Scenario: 共享元素过渡
- **GIVEN** 用户在密码列表中点击某个项目
- **WHEN** 导航到详情页面
- **THEN** 列表项卡片 SHALL 过渡到详情页面
- **AND** 标题文本 SHALL 过渡到详情页面标题
- **AND** 过渡动画 SHALL 不被系统UI遮挡

### Requirement: 列表项动画
RecyclerView 列表项 SHALL 具有流畅的进入和退出动画。

#### Scenario: 列表项进入动画
**Given** 用户打开密码列表页面
**When** 列表数据加载完成
**Then** 列表项 SHALL 依次淡入并从下方滑入
**And** 每个项目的动画 SHALL 略有延迟（staggered）
**And** 动画延迟 SHALL 在 20-50ms 之间

#### Scenario: 列表项删除动画
**Given** 用户删除某个密码项
**When** 删除操作执行
**Then** 被删除的项目 SHALL 先收缩然后淡出
**And** 周围的项目 SHALL 平滑地移动到新位置

### Requirement: 动画开关控制
用户 SHALL 能够控制界面动画的显示，包括启动画面动画。

#### Scenario: 禁用动画
- **GIVEN** 用户在设置中禁用了界面动画
- **WHEN** 用户启动应用
- **THEN** 系统 SHALL 尽可能缩短启动画面显示时间
- **AND** 系统 SHALL NOT 显示页面过渡动画
- **AND** 系统 SHALL NOT 显示列表项动画
- **AND** 系统 SHALL 保持基本的交互反馈（如点击状态）

#### Scenario: 启用动画（默认）
- **GIVEN** 用户使用默认设置或启用了界面动画
- **WHEN** 用户启动应用或在应用内导航
- **THEN** 系统 SHALL 显示启动画面动画
- **AND** 系统 SHALL 显示所有页面过渡动画效果
- **AND** 动画 SHALL 流畅不卡顿

### Requirement: 启动屏幕显示
应用 SHALL 在启动时显示启动画面，提供一致的视觉体验。

#### Scenario: 应用启动时显示启动画面
- **WHEN** 用户启动应用
- **THEN** 系统 SHALL 显示应用启动画面，持续约1秒
- **AND** 启动画面 SHALL 使用应用图标作为中心元素
- **AND** 启动画面 SHALL 自动淡出并过渡到登录界面

#### Scenario: 启动画面支持深色模式
- **WHEN** 系统处于深色模式
- **THEN** 启动画面 SHALL 使用深色主题背景
- **AND** 启动画面 SHALL 与应用主题保持一致

### Requirement: 深色模式完整适配
应用 SHALL 在所有界面正确响应深色模式设置。

#### Scenario: 登录界面支持深色模式
- **WHEN** 系统处于深色模式且用户打开应用
- **THEN** 登录界面 SHALL 使用深色主题背景色
- **AND** 所有文本颜色 SHALL 适配深色背景
- **AND** 输入框和卡片 SHALL 使用深色主题样式

#### Scenario: 主题切换时界面立即更新
- **WHEN** 用户在系统设置中切换深色/浅色模式
- **THEN** 应用界面 SHALL 在下次启动时应用新主题
- **AND** 所有界面元素 SHALL 正确显示新主题颜色

### Requirement: 系统UI边距适配
应用 SHALL 正确处理系统UI（状态栏、导航栏）的边距，防止内容被遮挡。

#### Scenario: 登录界面顶部不被状态栏遮挡
- **WHEN** 用户打开登录界面
- **THEN** 界面顶部内容 SHALL 完全可见
- **AND** 系统状态栏 SHALL 不遮挡任何界面元素
- **AND** 内容 SHALL 从状态栏下方开始显示

#### Scenario: 所有Fragment页面正确处理系统窗口边距
- **WHEN** 用户导航到任何Fragment页面
- **THEN** 页面顶部内容 SHALL 不被状态栏遮挡
- **AND** 页面底部内容 SHALL 不被导航栏遮挡
- **AND** 滚动内容 SHALL 在系统UI下方正确显示

### Requirement: 自动锁定选项现代化UI
自动锁定设置 SHALL 使用Material Design 3推荐的单选按钮对话框。

#### Scenario: 显示自动锁定选项对话框
- **WHEN** 用户点击"自动锁定"设置项
- **THEN** 系统 SHALL 显示单选按钮对话框
- **AND** 对话框 SHALL 列出所有可选的自动锁定时间选项
- **AND** 当前选中的选项 SHALL 高亮显示
- **AND** 用户点击任意选项 SHALL 保存设置并关闭对话框

#### Scenario: 自动锁定对话框支持深色模式
- **WHEN** 系统处于深色模式且用户打开自动锁定对话框
- **THEN** 对话框 SHALL 使用深色主题样式
- **AND** 单选按钮 SHALL 在深色背景下清晰可见
- **AND** 文本颜色 SHALL 适配深色主题

### Requirement: 网站图标自动加载
密码列表 SHALL 自动加载并显示网站对应的 favicon 图标，提升视觉识别度。

#### Scenario: 从 URL 加载网站图标
- **GIVEN** 用户保存了一个包含 URL 的密码项
- **WHEN** 密码列表页面显示该密码项
- **THEN** 系统 SHALL 从 URL 提取域名
- **AND** 系统 SHALL 尝试加载网站的 favicon.ico
- **AND** 系统 SHALL 在图标位置显示加载的网站图标
- **AND** 图标 SHALL 使用圆形裁剪显示

#### Scenario: 网站图标加载失败回退到首字母
- **GIVEN** 用户保存了一个密码项但网站没有 favicon
- **WHEN** 密码列表尝试加载网站图标
- **THEN** 系统 SHALL 加载失败后显示首字母图标
- **AND** 首字母图标 SHALL 使用密码标题的第一个字符
- **AND** 首字母图标 SHALL 使用 Material Design 3 的配色方案

#### Scenario: 网站图标多层回退机制
- **GIVEN** 用户保存了一个密码项
- **WHEN** 系统尝试加载网站图标
- **THEN** 系统 SHALL 按优先级尝试以下 URL：
  - `https://domain.com/favicon.ico`
  - `https://domain.com/apple-touch-icon.png`
  - Google Favicon Service（最后回退）
- **AND** 如果所有尝试失败，系统 SHALL 显示首字母或默认图标

#### Scenario: 网站图标缓存
- **GIVEN** 用户已查看过密码列表
- **WHEN** 用户再次打开密码列表
- **THEN** 系统 SHALL 从缓存加载已显示过的网站图标
- **AND** 系统 SHALL 减少网络请求，提升加载速度
- **AND** 缓存 SHALL 在 7 天后过期

#### Scenario: 离线状态下的图标显示
- **GIVEN** 用户设备处于离线状态
- **WHEN** 密码列表页面加载
- **THEN** 系统 SHALL 显示缓存中的网站图标（如果有）
- **AND** 如果没有缓存，系统 SHALL 显示首字母或默认图标
- **AND** 系统 SHALL NOT 阻塞列表显示等待图标加载

