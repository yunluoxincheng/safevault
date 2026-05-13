# Proposal: 集成后端服务到前端UI

## 背景

后端服务代码已经准备完毕，包括：
- `BackendServiceImpl` - BackendService接口的完整实现
- `CryptoManager` - AES-256-GCM加密管理器
- `AppDatabase` 和 `PasswordDao` - Room数据库层
- `EncryptedPasswordEntity` - 加密数据实体
- `SecurityUtils` - 安全工具类
- `ServiceLocator` - 服务定位器模式
- `SafeVaultApplication` - 应用类初始化ServiceLocator

项目能够成功构建，但前端UI组件（LoginActivity、MainActivity、PasswordListFragment等）中仍存在以下问题：

1. **BackendService获取方式缺失** - UI代码中将BackendService设置为null
2. **ViewModel未实现** - LoginViewModel、PasswordListViewModel等被注释或设置为null
3. **ViewModelFactory未创建** - 用于将BackendService注入ViewModel的工厂类
4. **功能未连接** - 所有需要后端支持的功能都显示"功能待实现"的临时提示

## 目标

将已实现的后端服务正确集成到前端UI中，使应用能够正常运行并提供完整的密码管理功能。

## 建议方案

### 1. 创建ViewModelFactory类
为每个需要BackendService的ViewModel创建对应的Factory类：
- `LoginViewModelFactory` - 注入BackendService到LoginViewModel
- `PasswordListViewModelFactory` - 注入BackendService到PasswordListViewModel
- `PasswordDetailViewModelFactory` - 注入BackendService到PasswordDetailViewModel
- `EditPasswordViewModelFactory` - 注入BackendService到EditPasswordViewModel

### 2. 实现或修复ViewModel类
确保所有ViewModel类正确实现，接收BackendService并通过LiveData暴露数据：
- `LoginViewModel` - 处理登录/初始化逻辑
- `PasswordListViewModel` - 管理密码列表和搜索
- `PasswordDetailViewModel` - 处理密码详情显示
- `EditPasswordViewModel` - 处理密码创建和编辑

### 3. 更新UI组件以使用ServiceLocator
修改所有Activities和Fragments，通过`ServiceLocator.getInstance().getBackendService()`获取BackendService实例：
- `LoginActivity` - 获取BackendService并创建LoginViewModel
- `MainActivity` - 获取BackendService用于自动锁定
- `PasswordListFragment` - 获取BackendService并创建PasswordListViewModel
- `PasswordDetailFragment` - 获取BackendService并创建PasswordDetailViewModel
- `EditPasswordFragment` - 获取BackendService并创建EditPasswordViewModel

### 4. 取消注释被临时禁用的代码
恢复所有被TODO注释标记的ViewModel相关代码，移除临时提示。

### 5. 确保SafeVaultApplication正确配置
验证AndroidManifest.xml中SafeVaultApplication已正确注册为应用类。

## 范围

### 包含
- 创建ViewModelFactory类
- 实现ViewModel类（如未实现）
- 更新UI组件以集成BackendService
- 恢复被注释的ViewModel相关代码
- 确保应用可以正常运行并提供核心功能

### 不包含
- 添加新的功能特性
- 修改UI布局和样式
- 更改数据库结构
- 修改加密算法
- 实现导入/导出功能（BackendServiceImpl中标记为TODO）

## 依赖关系

此变更依赖于已存在的：
- BackendService接口
- BackendServiceImpl实现
- CryptoManager
- AppDatabase和DAO
- ServiceLocator
- SafeVaultApplication

## 风险评估

- **低风险** - 后端代码已经完成并经过测试
- **中等风险** - 需要确保ViewModel正确处理生命周期
- **需要测试** - 集成后需要完整测试所有用户流程

## 验收标准

1. 应用可以成功构建和安装
2. 登录/初始化流程正常工作
3. 密码列表可以正常显示
4. 可以添加、编辑、删除密码条目
5. 搜索功能正常工作
6. 自动锁定功能正常工作
7. 所有临时提示信息已被移除
