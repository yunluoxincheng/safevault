<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---
## 项目放在com.ttt.safevault软件包目录下

##目前这个项目做的是前端部分

##项目的目标Android版本是Android10及以上（最小SDK 29，目标SDK 36)一定要兼容Android10+

## SafeVault Android Password Manager

A native Android password manager application built with Java using MVVM architecture. The frontend handles UI and user interactions only, while all encryption and data persistence is handled by a backend service.

## Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean

# Run tests
./gradlew test
./gradlew connectedAndroidTest

# Install debug build to connected device
./gradlew installDebug

# Check dependencies
./gradlew dependencies
```

## High-Level Architecture

### Frontend-Only Approach
- **UI Layer**: Activities/Fragments handle user interaction and display
- **ViewModel Layer**: Manages UI state and business logic
- **Backend Interface**: All cryptographic operations and data storage through `BackendService`

### Key Principle
The frontend **never** handles:
- Encryption/decryption operations
- Direct database access
- Plain-text password storage
- Sensitive data persistence

### Package Structure
```
com.ttt.safevault/
├── ui/                      # UI components (Activities/Fragments)
│   └── share/               # Password sharing UI components
├── viewmodel/               # MVVM ViewModels
├── model/                   # Data models and BackendService interface
├── autofill/                # Android AutofillService implementation
├── security/                # Security utilities and Token management
├── network/                 # Network layer (Retrofit, WebSocket, Token management)
├── dto/                     # Data Transfer Objects (Request/Response)
├── service/                 # Background services (WebSocket notifications)
├── receiver/                # Broadcast receivers
├── utils/                   # Helper classes
└── adapter/                 # RecyclerView adapters
```

### Core Components
- **LoginActivity**: App entry point with local and cloud authentication
- **MainActivity**: Main container with Navigation Component and Bottom Navigation
- **PasswordListFragment**: Display password entries with search
- **PasswordDetailFragment**: Show individual password details with share button
- **EditPasswordFragment**: Create/edit password entries
- **GeneratorFragment**: Standalone password generator with strength indicator and history
- **SettingsFragment**: App settings and preferences
- **AutofillServiceImpl**: Android AutofillService integration
- **BackendService**: Interface defining all backend operations
- **ShareActivity**: Password sharing configuration (offline and cloud)
- **ReceiveShareActivity**: Receive shared passwords
- **NearbyUsersActivity**: Discover nearby users for sharing
- **ShareHistoryFragment**: View sharing history (created and received)
- **ShareNotificationService**: WebSocket service for real-time sharing notifications

### Technology Stack
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 36
- **Language**: Java 8
- **Architecture**: MVVM with Android Jetpack
- **UI**: Material Design 3 + ConstraintLayout
- **Navigation**: Android Navigation Component with Bottom Navigation
- **Security**: Biometric authentication, FLAG_SECURE
- **Clipboard**: Custom ClipboardManager with 30-second auto-clear

## ⚠️ CRITICAL RULE - MANDATORY SKILL USAGE ⚠️

**在执行任何任务之前，必须先检查是否有可用的 skill！**

### 自动 Skill 匹配规则（内部使用）

每次收到用户请求时，**自动执行**以下检查：

```python
# AI 内部执行逻辑
def auto_match_skill(user_request, context):
    # 1. 关键词匹配
    keywords = extract_keywords(user_request)

    # 2. 错误模式匹配（如果有错误堆栈）
    error_patterns = extract_error_patterns(context)

    # 3. 文件模式匹配（如果有相关文件）
    file_patterns = extract_file_patterns(context)

    # 4. 匹配优先级规则
    for rule in SKILL_MATCHING_RULES:
        if matches_any(keywords, error_patterns, file_patterns, rule):
            return rule.skill_name

    return None  # 无匹配
```

### 强制 Skill 调用映射表

**当检测到以下关键词/模式时，必须调用对应 skill：**

| 检测关键词/错误/文件 | 必须调用 Skill |
|---------------------|---------------|
| 更新界面, UI美化, Material Design 3, 改UI | `android-ui-modernization` |
| 加密失败, 解密失败, BadPaddingException, IllegalBlockSizeException, KeyPermanentlyInvalidatedException, javax.crypto | `android-encryption-fixes` |
| API调用失败, WebSocket断开, 同步失败, 401/403错误, Token刷新 | `android-network-sync-fixes` |
| 生物识别失败, 指纹, 面容, BiometricPrompt | `android-biometric-fixes` |
| 邮箱验证, 验证码错误, OTP | `android-email-verification-fixes` |
| 修复bug, 崩溃, ANR, NullPointerException, 异常 | `android-debugging-fixes` |
| MVVM, ViewModel, LiveData, Repository | `android-mvvm-pattern` |
| FLAG_SECURE, 防截屏, 安全检查 | `android-security-practices` |
| 安全架构升级, SecureKeyStorage, KeyManager迁移 | `android-security-architecture-upgrade` |
| 密码分享, 二维码, 蓝牙, NFC分享 | `password-sharing-implementation` |
| 密码强度, 生成密码 | `password-strength-algorithms` |
| 创建提案, OpenSpec 提案 | `openspec:proposal` |
| 审查提案, 检查提案文档 | `openspec-review` |
| 完成审查, 验证实现 | `openspec-completion` |
| 写文档, 更新文档, API文档 | `documenting-code` |
| 重构代码, 优化代码, 清理代码 | `refactoring-code` |
| 如何实现, 设计方案 | `feature-design-advisor` |

### 用户调用方式

如果用户想手动指定 skill，可用以下方式：

```
# 方式 1: 中文简写别名
/加密-fix          # android-encryption-fixes
/UI-modern         # android-ui-modernization
/网络-sync         # android-network-sync-fixes
/生物-fix          # android-biometric-fixes
/邮箱-fix          # android-email-verification-fixes
/调试-fix          # android-debugging-fixes
/MVVM-pattern      # android-mvvm-pattern
/安全-practices    # android-security-practices
/Retrofit-net      # android-retrofit-network
/安全架构-upgrade  # android-security-architecture-upgrade
/密码分享          # password-sharing-implementation
/密码强度          # password-strength-algorithms
/文档              # documenting-code
/重构              # refactoring-code
/设计顾问          # feature-design-advisor
/openspec-review   # openspec-review
/openspec-complete # openspec-completion

# 方式 2: 完整 skill 名称
/skill android-encryption-fixes
/skill android-ui-modernization
```

### 多匹配处理

如果检测到多个匹配的 skill：
1. **高优先级优先** - encryption-fixes > debugging-fixes
2. **询问用户** - 同优先级时，使用 AskUserQuestion 让用户选择
3. **智能推荐** - 根据上下文推荐最合适的

**⚠️ 违反此规则将被视为严重的执行错误！**

---

## Development Guidelines

### BackendService Implementation
All data operations go through the `BackendService` interface. The frontend receives:
- `PasswordItem` objects (already decrypted)
- `List<PasswordItem>` for search results
- Primitive types for operation results

### Security Implementation
- Activities use `FLAG_SECURE` to prevent screenshots
- Clipboard manager auto-clears sensitive data
- Auto-lock when app goes to background
- Biometric authentication support

### Autofill Service
- Configured in `AndroidManifest.xml`
- Service implementation in `autofill/AutofillServiceImpl.java`
- Configuration file: `res/xml/autofill_service_configuration.xml`

### Navigation Flow
Bottom navigation with three top-level destinations:
- **密码库** (Vault): PasswordListFragment - Display password entries with search
  - List → Detail (pass `passwordId` as argument)
  - List → Edit (pass `passwordId`, use `-1` for new items)
- **生成器** (Generator): GeneratorFragment - Standalone password generator
  - Features: Password strength indicator, generation history, preset configurations
  - Supports: PIN codes, strong passwords, memorable passwords
- **设置** (Settings): SettingsFragment - App settings and preferences

### Material Design 3 Implementation
The app uses Material Design 3 components throughout:
- **TextInputLayout**: Outlined box style with Material 3
- **MaterialButton**: Filled, outlined, and text button variants
- **MaterialCardView**: 12dp corner radius for cards
- **MaterialSwitch**: For toggle controls
- **LinearProgressIndicator**: For password strength and loading states
- **BottomNavigationView**: Three-tab navigation with Material 3 styling
- **Dynamic Colors**: Android 12+ devices use system colors
- **Fixed Colors**: Android 10-11 use purple-based theme

## Important Notes

### Namespace Mismatch
- Package name: `com.ttt.safevault`
- Build namespace: `com.safevault`
- Maintain consistency when creating new components

### Backend Dependency
The frontend codebase is incomplete without a backend implementation of `BackendService`. Current placeholders exist for:
- Repository pattern
- Security manager
- Backend service locator

### Sprint Development Structure
1. **Sprint 1**: UI framework, login, password list
2. **Sprint 2**: Edit/create UI, password generator
3. **Sprint 3**: Autofill service, search functionality
4. **Sprint 4**: Settings, animations, bug fixes

### Build Configuration
- ViewBinding enabled for type-safe view references
- Room database included for repository access only
- Material Design 3 components for modern UI
- Biometric authentication support
- Custom ClipboardManager for secure clipboard handling

### Password Generator Features
The GeneratorFragment provides comprehensive password generation:
- **Length Control**: Slider from 8-32 characters
- **Character Types**: Uppercase, lowercase, numbers, symbols toggles
- **Strength Indicator**: Visual bar with color-coded strength levels (weak/medium/strong/very strong)
- **Presets**: Quick-select configurations for PIN codes, strong passwords, memorable passwords
- **Generation History**: Recent passwords stored locally (max 10 items)
- **Clear History**: Button to remove all stored history
- **Secure Clipboard**: 30-second auto-clear after copying

## Password Sharing Features

### Overview
SafeVault supports both offline and cloud-based password sharing with multiple transmission methods:
- **Offline Sharing**: QR code, Bluetooth, NFC
- **Cloud Sharing**: Direct link, user-to-user, nearby users

### Sharing Types

#### 1. Offline Sharing (Direct)
- Generate QR code containing encrypted password data
- Bluetooth transmission between nearby devices
- NFC tap-to-share functionality
- Requires share password for additional security

#### 2. Cloud Direct Sharing
- Generate shareable link (safevault://share/{shareId})
- Anyone with link can access (if permissions allow)
- QR code for easy scanning
- Configurable expiration time

#### 3. User-to-User Sharing
- Share with specific SafeVault users
- Requires both users to have cloud accounts
- Real-time notification via WebSocket
- Access control and permission management

#### 4. Nearby Users Sharing
- Discover nearby SafeVault users via location
- Share with users in physical proximity
- Requires location permissions
- Automatic distance calculation

### Share Permissions
Each share can have granular permissions:
- **canView**: Receiver can view the password
- **canSave**: Receiver can save to their vault
- **revocable**: Share can be revoked by sender

### Cloud Authentication
- **Register**: Create cloud account with username, password, display name
- **Login**: Authenticate with JWT tokens
- **Auto-refresh**: Tokens automatically refresh when expired
- **Biometric**: Support for fingerprint/face unlock

### Real-time Notifications
- WebSocket connection maintained by ShareNotificationService
- Receive notifications for:
  - New password shares
  - Share revocations
  - Online user status updates
- System notifications with deep links to shared content

### Network Layer Architecture
```
Frontend (Activities/Fragments)
    ↓
ViewModels (AuthViewModel, ShareViewModel, etc.)
    ↓
BackendService Interface
    ↓
BackendServiceImpl
    ↓
Network Layer:
├── RetrofitClient (REST API)
├── WebSocketManager (Real-time)
└── TokenManager (Authentication)
```

### API Integration
The app integrates with `safevault-backend` REST APIs:
- **Authentication**: `/v1/auth/register`, `/v1/auth/login`, `/v1/auth/refresh`
- **Shares**: `/v1/shares` (CRUD operations)
- **Discovery**: `/v1/discovery/register`, `/v1/discovery/nearby`
- **WebSocket**: Real-time notifications

### LLM API Concurrency Limits
- **最大并发请求数**: 5
- 调用大模型API时，确保同时进行的请求数不超过5个
- 如果需要发送超过5个请求，应使用适当的限流/队列机制
- 此限制适用于所有大模型API调用（如OpenAI、Claude等）

### Security Considerations for Sharing
- All cloud communication uses HTTPS
- Tokens stored securely with AndroidKeyStore
- Share passwords encrypt offline transfers
- FLAG_SECURE prevents screenshots of sharing UI
- Expiration times limit share validity

### Encryption Protocol Version 2.0 (Hybrid Encryption)
The contact sharing feature uses **hybrid encryption (RSA + AES)**:
- **AES-256-GCM**: Encrypts the actual share data (no size limit)
- **RSA-OAEP**: Encrypts the AES key with receiver's public key
- **RSA-SHA256**: Digital signature to verify sender identity
- **Protocol Version**: 2.0 (not backward compatible with 1.0)

**Key Components**:
- `EncryptedSharePacket` (v2.0): Contains `encryptedAESKey`, `iv`, `encryptedData`, `signature`
- `ShareEncryptionManager`: Handles `createEncryptedPacket()` and `openEncryptedPacket()`

**Encryption Flow**:
1. Serialize `ShareDataPacket` to JSON
2. Generate random AES-256 key and IV
3. Encrypt data with AES-GCM
4. Encrypt AES key with receiver's RSA public key
5. Sign original data with sender's RSA private key
6. Assemble `EncryptedSharePacket` (v2.0)

**Decryption Flow**:
1. Verify packet version is "2.0"
2. Decrypt AES key with receiver's RSA private key
3. Decrypt data with AES key and IV
4. Verify signature with sender's RSA public key

### Deep Link Handling
The app handles deep links for password sharing:
- `safevault://share/{shareId}` - Open received share
- `safevault://offline/{data}` - Offline share with encrypted data
- NFC NDEF records - Auto-open share on tap

### Location Services (Nearby Users)
- **Permissions**: ACCESS_FINE_LOCATION required
- **Registration**: Users register their location with server
- **Discovery**: Query for users within specified radius
- **Heartbeat**: Periodic updates to maintain online status
- **Privacy**: Location data only used for sharing discovery

### Notification Channels
- **Share Notification Channel**: High priority for new shares
- **Foreground Service**: Maintains WebSocket connection
- **Badge Support**: Show notification count on launcher icon

### Share History Management
Users can view:
- **My Shares**: Passwords they've shared with others
- **Received Shares**: Passwords others have shared with them
- **Status Tracking**: Active, expired, revoked, accepted
- **Actions**: Revoke active shares, save received shares

### UI Components for Sharing

#### ShareActivity
- Choose sharing method (offline/cloud)
- Configure permissions (view, save, revoke)
- Set expiration time
- Select target user (for user-to-user sharing)

#### ReceiveShareActivity
- Display shared password details
- Show permissions and expiration
- Save to vault or decline
- Handle offline shares with password prompt

#### NearbyUsersActivity
- List nearby SafeVault users
- Show distance and online status
- Filter by search radius
- Initiate share with selected user

#### ShareHistoryFragment
- Tabbed interface (created/received)
- Filter by offline/cloud
- Revoke or save shares
- View share details
