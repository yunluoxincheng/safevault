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
