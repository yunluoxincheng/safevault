# Tasks: 集成后端服务到前端UI

## 任务列表

### 1. 检查并配置SafeVaultApplication
- [x] 验证AndroidManifest.xml中已注册SafeVaultApplication
- [x] 确认ServiceLocator在Application.onCreate()中正确初始化

### 2. 创建ViewModelFactory类
- [x] 创建`viewmodel/factory/LoginViewModelFactory.java`
  - 接收BackendService作为构造参数
  - 创建LoginViewModel实例
- [x] 创建`viewmodel/factory/PasswordListViewModelFactory.java`
  - 接收BackendService作为构造参数
  - 创建PasswordListViewModel实例
- [x] 创建`viewmodel/factory/PasswordDetailViewModelFactory.java`
  - 接收BackendService作为构造参数
  - 创建PasswordDetailViewModel实例
- [x] 创建`viewmodel/factory/EditPasswordViewModelFactory.java`
  - 接收BackendService作为构造参数
  - 创建EditPasswordViewModel实例

### 3. 实现或修复ViewModel类
- [x] 检查并修复`LoginViewModel`
  - 确保接收BackendService
  - 实现登录/初始化逻辑
  - 使用LiveData暴露认证状态
- [x] 检查并修复`PasswordListViewModel`
  - 确保接收BackendService
  - 实现密码列表加载
  - 实现搜索功能
- [x] 检查并修复`PasswordDetailViewModel`
  - 确保接收BackendService
  - 实现密码详情加载
  - 实现删除和复制功能
- [x] 检查并修复`EditPasswordViewModel`
  - 确保接收BackendService
  - 实现保存/更新逻辑
  - 实现密码生成器调用

### 4. 更新LoginActivity
- [x] 通过ServiceLocator获取BackendService实例
- [x] 使用LoginViewModelFactory创建LoginViewModel
- [x] 取消注释观察者设置代码
- [x] 取消注释按钮点击处理代码
- [x] 移除临时提示信息

### 5. 更新MainActivity
- [x] 通过ServiceLocator获取BackendService实例
- [x] 确保onPause中正确调用recordBackgroundTime
- [x] 确保checkAutoLock正确使用BackendService
- [x] 取消注释ViewModel相关代码
- [x] 移除临时null检查

### 6. 更新PasswordListFragment
- [x] 通过ServiceLocator获取BackendService实例
- [x] 使用PasswordListViewModelFactory创建ViewModel
- [x] 取消注释观察者设置代码
- [x] 取消注释所有功能方法
- [x] 移除临时提示信息

### 7. 更新PasswordDetailFragment
- [x] 通过ServiceLocator获取BackendService实例
- [x] 使用PasswordDetailViewModelFactory创建ViewModel
- [x] 取消注释观察者设置代码
- [x] 取消注释所有功能方法
- [x] 移除临时提示信息

### 8. 更新EditPasswordFragment
- [x] 通过ServiceLocator获取BackendService实例
- [x] 使用EditPasswordViewModelFactory创建ViewModel
- [x] 取消注释观察者设置代码
- [x] 取消注释所有功能方法
- [x] 移除临时提示信息

### 9. 测试验证
- [x] 构建项目确认无编译错误
- [ ] 测试首次启动（初始化流程）
- [ ] 测试登录流程
- [ ] 测试密码列表显示
- [ ] 测试添加密码
- [ ] 测试编辑密码
- [ ] 测试删除密码
- [ ] 测试搜索功能
- [ ] 测试密码复制功能
- [ ] 测试自动锁定功能

## 任务依赖

- 任务1必须最先完成
- 任务2和任务3可以并行进行
- 任务4-8依赖任务2和任务3的完成
- 任务9必须在前面所有任务完成后进行

## 可并行工作

任务2（创建Factory类）和任务3（实现ViewModel）可以并行进行，因为它们相互独立。

任务4-8（更新各个UI组件）可以并行进行，因为它们各自独立。

## 任务依赖

- 任务1必须最先完成
- 任务2和任务3可以并行进行
- 任务4-8依赖任务2和任务3的完成
- 任务9必须在前面所有任务完成后进行

## 可并行工作

任务2（创建Factory类）和任务3（实现ViewModel）可以并行进行，因为它们相互独立。

任务4-8（更新各个UI组件）可以并行进行，因为它们各自独立。
