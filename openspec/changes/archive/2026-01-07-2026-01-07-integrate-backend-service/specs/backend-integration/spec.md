# Spec: 后端服务集成

## ADDED Requirements

### Requirement: UI components MUST obtain BackendService instances through ServiceLocator
All UI components (Activities and Fragments) MUST NOT directly create BackendService instances, but MUST obtain them through ServiceLocator.

UI组件（Activities和Fragments）不得直接创建BackendService实例，必须通过ServiceLocator获取。

#### Scenario: LoginActivity获取BackendService
**Given** 应用启动且显示LoginActivity
**When** LoginActivity需要BackendService实例
**Then** 必须调用`ServiceLocator.getInstance().getBackendService()`
**And** 返回的BackendService实例不能为null

#### Scenario: Fragment获取BackendService
**Given** Fragment已附加到Activity
**When** Fragment需要BackendService实例
**Then** 必须调用`ServiceLocator.getInstance().getBackendService()`
**And** 返回的BackendService实例不能为null

### Requirement: ViewModels MUST be created through corresponding Factory with BackendService injection
Each ViewModel MUST have a corresponding Factory class that creates the ViewModel and injects the BackendService instance.

每个ViewModel必须有对应的Factory类，Factory负责创建ViewModel并注入BackendService实例。

#### Scenario: LoginViewModel通过Factory创建
**Given** 需要创建LoginViewModel实例
**When** 使用LoginViewModelFactory创建ViewModel
**Then** Factory必须接收BackendService作为构造参数
**And** 创建的LoginViewModel必须持有BackendService引用
**And** ViewModel必须使用LiveData暴露认证状态

#### Scenario: PasswordListViewModel通过Factory创建
**Given** 需要创建PasswordListViewModel实例
**When** 使用PasswordListViewModelFactory创建ViewModel
**Then** Factory必须接收BackendService作为构造参数
**And** 创建的PasswordListViewModel必须持有BackendService引用
**And** ViewModel必须使用LiveData暴露密码列表和加载状态

### Requirement: UI components MUST observe ViewModel LiveData and update UI accordingly
All UI components MUST observe the corresponding ViewModel's LiveData and update the UI based on data changes.

所有UI组件必须观察对应ViewModel的LiveData，并根据数据变化更新UI。

#### Scenario: LoginActivity观察认证状态
**Given** 用户在LoginActivity输入密码并点击登录
**When** LoginViewModel的isAuthenticated LiveData发生变化
**Then** LoginActivity必须导航到MainActivity
**And** 必须清除敏感输入

#### Scenario: PasswordListFragment观察密码列表
**Given** 用户打开密码列表页面
**When** PasswordListViewModel的passwordItems LiveData更新
**Then** PasswordListFragment必须更新RecyclerView显示
**And** 必须根据列表显示/隐藏空状态布局

#### Scenario: UI观察错误信息
**Given** ViewModel执行操作时发生错误
**When** ViewModel的errorMessage LiveData更新
**Then** UI必须显示错误提示给用户
**And** ViewModel必须在错误显示后清除错误消息

### Requirement: SafeVaultApplication MUST be properly registered in AndroidManifest
The application MUST register SafeVaultApplication in AndroidManifest to ensure ServiceLocator is properly initialized on app startup.

应用必须在AndroidManifest中正确注册SafeVaultApplication，确保ServiceLocator在应用启动时正确初始化。

#### Scenario: 应用启动时初始化ServiceLocator
**Given** 应用进程启动
**When** Application.onCreate()被调用
**Then** SafeVaultApplication必须调用ServiceLocator.init(this)
**And** ServiceLocator必须持有ApplicationContext引用

### Requirement: Auto-lock functionality MUST be properly implemented in MainActivity
The application MUST record time when going to background and check for timeout when returning to determine if re-locking is required.

自动锁定功能必须在MainActivity中正确实现，应用进入后台时记录时间，返回时检查是否超时需要重新锁定。

#### Scenario: 记录进入后台时间
**Given** 用户在MainActivity中按Home键
**When** MainActivity.onPause()被调用
**Then** 必须调用BackendService.recordBackgroundTime()
**And** 必须保存当前时间戳

#### Scenario: 检查是否需要重新锁定
**Given** 用户重新打开应用
**When** MainActivity.onResume()被调用
**And** 后台时间超过自动锁定超时设置
**Then** 必须调用BackendService.lock()
**And** 必须导航回LoginActivity

### Requirement: All temporary placeholder messages and TODO code MUST be removed
After integration is complete, all "feature not implemented" placeholder messages and commented ViewModel code MUST be restored or removed.

集成完成后，所有"功能待实现"提示和被注释的ViewModel代码必须恢复或移除。

#### Scenario: LoginActivity恢复完整功能
**Given** LoginActivity代码中包含临时提示信息
**When** 后端服务集成完成
**Then** 所有showError("功能待实现...")调用必须被移除
**And** 所有被注释的Observer代码必须恢复
**And** 按钮点击必须调用ViewModel方法而非显示临时提示

#### Scenario: PasswordListFragment恢复完整功能
**Given** PasswordListFragment代码中包含临时提示信息
**When** 后端服务集成完成
**Then** 所有showError("复制/删除功能待实现...")调用必须被移除
**And** 所有被注释的Observer代码必须恢复
**And** 功能按钮必须调用ViewModel方法
