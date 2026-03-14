# Project Context

## Purpose
SafeVault 是一个原生 Android 密码管理器应用，旨在为用户提供安全、便捷的密码存储和管理功能。
- 前端只负责 UI 和用户交互
- 所有加密和数据持久化由后端服务处理
- 支持 Android AutofillService 自动填充
- 支持生物识别认证

## Tech Stack
- **语言**: Java 17
- **架构**: MVVM (Model-View-ViewModel)
- **最小 SDK**: 29 (Android 10)
- **目标 SDK**: 36
- **UI 框架**: Material Components + ConstraintLayout
- **导航**: Android Navigation Component
- **视图绑定**: ViewBinding
- **安全**: Biometric authentication, FLAG_SECURE
- **包名**: `com.ttt.safevault`
- **构建命名空间**: `com.safevault`

## Project Conventions

### Code Style
- 包结构位于 `com.ttt.safevault/` 下
- 命名规范遵循 Java 约定
- 使用 Material Design 组件保持 UI 一致性
- 所有敏感数据操作必须通过 BackendService

### Architecture Patterns
- **MVVM 架构**: Activities/Fragments → ViewModels → BackendService
- **前端原则**: 前端绝不处理：
  - 加密/解密操作
  - 直接数据库访问
  - 明文密码存储
  - 敏感数据持久化
- **包结构**:
  ```
  com.ttt.safevault/
  ├── ui/           # UI 组件
  ├── viewmodel/    # MVVM ViewModels
  ├── model/        # 数据模型和 BackendService 接口
  ├── autofill/     # AutofillService 实现
  ├── security/     # 安全工具和配置
  ├── utils/        # 辅助类
  └── adapter/      # RecyclerView adapters
  ```

### Testing Strategy
- 单元测试: `./gradlew test`
- Android 测试: `./gradlew connectedAndroidTest`
- 测试应覆盖 ViewModel 和业务逻辑

### Git Workflow
- 主分支: `master`
- 提交信息遵循项目规范（参考现有提交格式）
- Pull Requests 基于 master 分支

## Domain Context
- **密码管理器核心功能**: 密码条目的增删改查、搜索、分类
- **安全特性**:
  - FLAG_SECURE 防止截屏
  - 剪贴板自动清除敏感数据
  - 应用后台时自动锁定
  - 生物识别认证
- **自动填充**: Android AutofillService 集成，支持其他应用自动填充密码

## Important Constraints
- **版本兼容**: 必须兼容 Android 10 (API 29) 及以上版本
- **安全隔离**: 前端代码不能直接处理加密或存储敏感数据
- **命名空间不一致**: 包名 (`com.ttt.safevault`) 与构建命名空间 (`com.safevault`) 需保持一致性
- **后端依赖**: 前端代码库依赖 BackendService 实现，当前存在占位符

## External Dependencies
- **BackendService**: 定义所有后端操作的接口
  - 返回已解密的 `PasswordItem` 对象
  - 处理搜索结果列表
  - 返回操作结果的原始类型
- **Android 系统**:
  - BiometricPrompt API
  - AutofillFramework
  - ClipboardManager
  - Navigation Component
