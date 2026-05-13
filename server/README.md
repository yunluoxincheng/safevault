# SafeVault 后端服务

<div align="center">

![SafeVault Backend](https://img.shields.io/badge/SafeVault-v3.6.0-brightgreen)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.9-green)
![Java](https://img.shields.io/badge/Java-17-blue)
![License](https://img.shields.io/badge/license-MIT-green)

**为 Android 密码管理器应用提供云端支持的后端服务**

</div>

## 目录

- [项目概述](#项目概述)
- [技术栈](#技术栈)
- [核心功能模块](#核心功能模块)
- [快速开始](#快速开始)
- [项目结构](#项目结构)
- [模块化重构](#模块化重构)
- [服务器部署](#服务器部署)
- [API 接口文档](#api-接口文档)
- [密码算法 v3.0](#密码算法-v30)
- [数据库设计](#数据库设计)
- [安全设计](#安全设计)
- [版本历史](#版本历史)

---

## 项目概述

SafeVault 后端服务为 Android 密码管理器应用提供云端支持，实现用户管理、应用内密码分享、实时通信等核心功能。

### 技术栈

- **框架**: Spring Boot 3.5.9
- **Java 版本**: JDK 17+
- **数据库**: PostgreSQL 15+ / H2（开发环境）
- **ORM**: Spring Data JPA + Flyway
- **安全**: Spring Security + JWT
- **加密**: Bouncy Castle (AES-256-GCM, X25519, Ed25519)
- **密码哈希**: Argon2id
- **实时通信**: WebSocket (STOMP)
- **缓存**: Redis
- **限流**: Bucket4j
- **API 文档**: SpringDoc OpenAPI
- **构建工具**: Maven
- **容器化**: Docker + Docker Compose

### 核心功能模块

```
server/
├── 用户管理模块 (User Management)
│   ├── 用户注册/登录
│   ├── JWT 认证
│   ├── 密钥对管理 (RSA + X25519 + Ed25519)
│   ├── 用户搜索
│   └── 用户配置文件管理
│
├── 密码分享模块 (Share Management)
│   ├── 三种分享方式
│   │   ├── 通过用户ID/用户名直接分享
│   │   ├── 扫码分享（类似 AirDrop）
│   │   └── 附近设备发现分享
│   ├── 创建/接收/撤销分享
│   ├── 分享历史管理
│   ├── 有效期控制
│   └── 权限控制
│
├── 实时通信模块 (WebSocket)
│   ├── 分享通知推送
│   ├── 在线状态管理
│   └── 连接心跳保活
│
├── 附近发现模块 (Discovery)
│   ├── 位置注册
│   ├── 附近用户查询
│   └── 距离计算（Haversine 公式）
│
└── 加密服务模块 (Crypto Service)
    ├── 用户密钥对生成
    ├── 会话密钥生成
    ├── 端到端加密
    └── 分享数据加解密
```

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 15+（生产环境）

### 本地运行

```bash
# 克隆项目
git clone https://github.com/yunluoxincheng/SafeVault.git
cd SafeVault/server

# 启动数据库
docker-compose up -d postgres redis

# 运行应用
./mvnw spring-boot:run

# 访问 API 文档
open http://localhost:8080/api/swagger-ui.html
```

### Docker 部署

```bash
# 启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

---

## 模块化重构

后端已按“模块化单体”方式整理，功能行为保持不变。

- 模块边界说明：`docs/modularization-plan.md`
- 模块边界包：`src/main/java/org/ttt/safevaultbackend/modules/*`

---

## 服务器部署

生产部署文档：`docs/deployment/server-deployment.md`

核心文件：

- 统一编排：`docker-compose.yml`
- 镜像构建脚本：`scripts/deploy/build-image.sh`
- 服务器部署脚本：`scripts/deploy/deploy-server.sh`
- 生产环境变量模板：`scripts/deploy/.env.prod.example`

---

## 项目结构

```
server/
├── pom.xml                                    # Maven 配置
├── Dockerfile                                 # Docker 镜像构建
├── docker-compose.yml                         # 本地开发环境
├── src/
│   ├── main/
│   │   ├── java/org/ttt/safevaultbackend/
│   │   │   ├── SafevaultBackendApplication.java    # 主启动类
│   │   │   │
│   │   │   ├── config/                            # 配置类
│   │   │   │   ├── SecurityConfig.java            # 安全配置
│   │   │   │   ├── WebSocketConfig.java          # WebSocket 配置
│   │   │   │   ├── OpenApiConfig.java             # API 文档配置
│   │   │   │   └── RedisConfig.java               # Redis 配置
│   │   │   │
│   │   │   ├── controller/                        # REST API 控制器
│   │   │   │   ├── AuthController.java            # 认证接口
│   │   │   │   ├── UserController.java            # 用户接口
│   │   │   │   ├── ShareController.java           # 分享接口
│   │   │   │   └── DiscoveryController.java       # 附近发现接口
│   │   │   │
│   │   │   ├── service/                           # 业务逻辑层
│   │   │   │   ├── AuthService.java               # 认证服务
│   │   │   │   ├── UserService.java               # 用户服务
│   │   │   │   ├── ShareService.java              # 分享服务
│   │   │   │   ├── WebSocketService.java          # WebSocket 服务
│   │   │   │   ├── DiscoveryService.java          # 附近发现服务
│   │   │   │   └── CryptoService.java             # 加密服务
│   │   │   │
│   │   │   ├── repository/                        # 数据访问层
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── PasswordShareRepository.java
│   │   │   │   ├── ShareAuditLogRepository.java
│   │   │   │   └── OnlineUserRepository.java
│   │   │   │
│   │   │   ├── entity/                            # 数据库实体
│   │   │   │   ├── User.java                      # 用户实体
│   │   │   │   ├── PasswordShare.java             # 分享记录
│   │   │   │   ├── ShareAuditLog.java             # 审计日志
│   │   │   │   ├── OnlineUser.java                # 在线用户
│   │   │   │   ├── ShareStatus.java               # 分享状态枚举
│   │   │   │   └── ShareType.java                 # 分享类型枚举
│   │   │   │
│   │   │   ├── dto/                               # 数据传输对象
│   │   │   │   ├── request/                       # 请求 DTO
│   │   │   │   │   ├── RegisterRequest.java
│   │   │   │   │   ├── LoginRequest.java
│   │   │   │   │   ├── CreateShareRequest.java
│   │   │   │   │   ├── RegisterLocationRequest.java
│   │   │   │   │   └── UploadEccPublicKeyRequest.java  # ECC公钥上传
│   │   │   │   ├── response/                      # 响应 DTO
│   │   │   │   │   ├── AuthResponse.java
│   │   │   │   │   ├── UserProfileResponse.java
│   │   │   │   │   ├── ShareResponse.java
│   │   │   │   │   ├── NearbyUserResponse.java
│   │   │   │   │   ├── UserKeyInfoResponse.java    # 用户密钥信息
│   │   │   │   │   └── UploadEccPublicKeyResponse.java
│   │   │   │   ├── SharePermission.java
│   │   │   │   ├── PasswordData.java
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   └── ShareNotificationMessage.java
│   │   │   │
│   │   │   ├── security/                          # 安全相关
│   │   │   │   ├── JwtTokenProvider.java          # JWT 工具
│   │   │   │   └── JwtAuthenticationFilter.java
│   │   │   │
│   │   │   ├── websocket/                         # WebSocket 相关
│   │   │   │   ├── WebSocketAuthInterceptor.java
│   │   │   │   ├── WebSocketConnectionManager.java
│   │   │   │   └── WebSocketEventListener.java
│   │   │   │
│   │   │   ├── scheduler/                         # 定时任务
│   │   │   │   ├── ShareExpirationScheduler.java
│   │   │   │   ├── OnlineUserCleanupScheduler.java
│   │   │   │   └── RegistrationCleanupScheduler.java
│   │   │   │
│   │   │   └── util/                              # 工具类
│   │   │       └── KeyGenerator.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml                    # 主配置文件
│   │       ├── application-dev.yml                # 开发环境配置
│   │       ├── application-prod.yml               # 生产环境配置
│   │       └── db/migration/                      # 数据库迁移脚本
│   │
│   └── test/
│       └── java/org/ttt/safevaultbackend/
│           ├── SafevaultBackendApplicationTests.java
│           └── integration/                       # 集成测试
│               └── CryptoKeyManagementIntegrationTest.java
```

---

## API 接口文档

### 基础信息

- **Base URL**: `http://localhost:8080/api`
- **认证方式**: JWT Bearer Token
- **响应格式**: JSON
- **API 文档**: `http://localhost:8080/api/swagger-ui.html`

### 1. 认证接口

#### 1.1 用户注册

```http
POST /v1/auth/register
Content-Type: application/json

{
  "deviceId": "unique-device-id",
  "username": "zhangsan",
  "displayName": "张三",
  "email": "zhangsan@example.com",
  "publicKey": "BASE64_ENCODED_PUBLIC_KEY",
  "x25519PublicKey": "X25519_PUBLIC_KEY",  // v3.0 新增
  "ed25519PublicKey": "ED25519_PUBLIC_KEY" // v3.0 新增
}

Response 201:
{
  "userId": "usr_abc123xyz",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 86400
}
```

#### 1.2 用户登录

```http
POST /v1/auth/login
Content-Type: application/json

{
  "userId": "usr_abc123xyz",
  "deviceId": "unique-device-id",
  "signature": "USER_SIGNATURE"
}

Response 200:
{
  "userId": "usr_abc123xyz",
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 86400
}
```

### 2. 用户管理接口

#### 2.1 获取当前用户信息

```http
GET /v1/users/me
Authorization: Bearer {access_token}
```

#### 2.2 上传 ECC 公钥（v3.0 新增）

```http
PUT /v1/users/me/ecc-public-keys
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "x25519PublicKey": "BASE64_X25519_PUBLIC_KEY",
  "ed25519PublicKey": "BASE64_ED25519_PUBLIC_KEY"
}

Response 200:
{
  "message": "ECC public keys updated successfully",
  "x25519PublicKey": "BASE64_X25519_PUBLIC_KEY",
  "ed25519PublicKey": "BASE64_ED25519_PUBLIC_KEY",
  "updatedAt": "2026-03-03T10:30:00"
}
```

#### 2.3 获取用户密钥信息（v3.0 新增）

```http
GET /v1/users/{userId}/keys
Authorization: Bearer {access_token}

Response 200:
{
  "userId": "usr_abc123xyz",
  "rsaPublicKey": "BASE64_RSA_PUBLIC_KEY",
  "x25519PublicKey": "BASE64_X25519_PUBLIC_KEY",
  "ed25519PublicKey": "BASE64_ED25519_PUBLIC_KEY",
  "keyVersion": "v2"
}
```

#### 2.4 搜索用户

```http
GET /v1/users/search?query=zhang
Authorization: Bearer {access_token}

Response 200:
[
  {
    "userId": "usr_def456uvw",
    "username": "zhangsan",
    "displayName": "张三",
    "publicKey": "BASE64_ENCODED_PUBLIC_KEY",
    "x25519PublicKey": "BASE64_X25519_PUBLIC_KEY",  // v3.0 新增
    "ed25519PublicKey": "BASE64_ED25519_PUBLIC_KEY" // v3.0 新增
  }
]
```

#### 2.5 生成二维码（用于扫码分享）

```http
GET /v1/users/me/qrcode
Authorization: Bearer {access_token}

Response 200:
{
  "qrCodeData": "safevault:receive:usr_abc123xyz:temp_token",
  "expiresAt": 1704672000000,
  "size": 300
}
```

### 3. 密码分享接口

#### 3.1 创建分享（支持三种类型）

```http
POST /v1/shares
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "passwordId": "local_pwd_123",
  "title": "GitHub",
  "username": "user@example.com",
  "encryptedPassword": "ENCRYPTED_PASSWORD",
  "url": "https://github.com",
  "notes": "我的GitHub账号",
  "toUserId": "usr_def456uvw",
  "expireInMinutes": 1440,
  "permission": {
    "canView": true,
    "canSave": true,
    "isRevocable": true
  },
  "shareType": "USER_TO_USER",
  "protocolVersion": "v3"  // v2 或 v3
}

Response 200:
{
  "shareId": "shr_abc123xyz",
  "shareToken": null,
  "expiresAt": 1704240000000
}
```

**分享类型（shareType）**：
- `DIRECT` - 直接分享（链接/二维码，无需指定接收方）
- `USER_TO_USER` - 用户对用户分享（通过用户ID/用户名）
- `NEARBY` - 附近设备分享

**协议版本（protocolVersion）**：
- `v2` - 使用 RSA-2048 加密
- `v3` - 使用 X25519/Ed25519 加密（推荐）

#### 3.2 接收分享

```http
GET /v1/shares/{shareId}
Authorization: Bearer {access_token}

Response 200:
{
  "shareId": "shr_abc123xyz",
  "fromUserId": "usr_abc123xyz",
  "fromDisplayName": "张三",
  "passwordData": {
    "title": "GitHub",
    "username": "user@example.com",
    "encryptedPassword": "ENCRYPTED_PASSWORD",
    "url": "https://github.com",
    "notes": "我的GitHub账号"
  },
  "permission": {
    "canView": true,
    "canSave": true,
    "isRevocable": true
  },
  "status": "PENDING",
  "shareType": "USER_TO_USER",
  "protocolVersion": "v3",
  "createdAt": 1704067200000,
  "expiresAt": 1704240000000
}
```

#### 3.3 撤销分享

```http
POST /v1/shares/{shareId}/revoke
Authorization: Bearer {access_token}
```

#### 3.4 保存分享的密码

```http
POST /v1/shares/{shareId}/save
Authorization: Bearer {access_token}
```

#### 3.5 获取创建的分享

```http
GET /v1/shares/created
Authorization: Bearer {access_token}
```

#### 3.6 获取接收的分享

```http
GET /v1/shares/received
Authorization: Bearer {access_token}
```

### 4. 附近发现接口

#### 4.1 注册位置信息

```http
POST /v1/discovery/register
Authorization: Bearer {access_token}
Content-Type: application/json

{
  "latitude": 39.9042,
  "longitude": 116.4074
}
```

#### 4.2 获取附近用户

```http
GET /v1/discovery/nearby?lat=39.9042&lng=116.4074&radius=1000
Authorization: Bearer {access_token}

Response 200:
[
  {
    "userId": "usr_def456uvw",
    "username": "lisi",
    "displayName": "李四",
    "distance": 256.5,
    "lastSeen": 1704067200000
  }
]
```

#### 4.3 发送心跳

```http
POST /v1/discovery/heartbeat
Authorization: Bearer {access_token}
```

### 5. WebSocket 接口

#### 5.1 连接端点

```
ws://localhost:8080/api/ws
```

#### 5.2 订阅主题

- `/user/queue/shares` - 接收分享通知（用户专属）
- `/topic/online-users` - 在线用户列表（附近发现功能）

#### 5.3 消息格式

**分享通知消息**：
```json
{
  "type": "NEW_SHARE",
  "shareId": "shr_abc123",
  "fromUserId": "usr_abc123xyz",
  "fromDisplayName": "张三",
  "message": "张三向你分享了一个密码",
  "timestamp": 1704067200000
}
```

**在线用户消息**：
```json
{
  "userId": "usr_def456uvw",
  "username": "lisi",
  "displayName": "李四",
  "latitude": 39.9042,
  "longitude": 116.4074,
  "lastSeen": 1704067200000
}
```

---

## 密码算法 v3.0

### 算法升级

SafeVault 后端已支持 v3.0 密码算法，提供更强的安全性和更好的性能：

| 算法 | 版本 | 用途 | 特性 |
|------|------|------|------|
| **X25519** | v3.0 | ECDH 密钥交换 | 32字节公钥，前向保密 |
| **Ed25519** | v3.0 | EdDSA 数字签名 | 64字节签名，快速验证 |
| **RSA-2048** | v2.0 | 兼容性支持 | OAEP填充，向后兼容 |

### 密钥管理

**User 实体支持的密钥类型**：

```java
// RSA 公钥（协议版本 2.0）
@Column(name = "public_key")
private String publicKey;

// X25519 公钥（协议版本 3.0）
@Column(name = "x25519_public_key")
private String x25519PublicKey;

// Ed25519 公钥（协议版本 3.0）
@Column(name = "ed25519_public_key")
private String ed25519PublicKey;

// 公钥版本标识
@Column(name = "key_version")
private String keyVersion; // v1=RSA, v2=X25519/Ed25519
```

### 版本协商

客户端可以通过 `/v1/users/{userId}/keys` 接口获取用户的所有公钥信息，根据对方支持的密钥版本选择合适的加密协议：

1. 如果双方都支持 v3.0，优先使用 X25519/Ed25519
2. 如果任一方不支持，回退到 RSA-2048
3. 确保向后兼容性

### 安全优势

相比 RSA-2048：

- **性能**: 密钥生成快约 100 倍
- **密钥尺寸**: 公钥/私钥仅 32 字节（RSA 约 300 字节）
- **签名尺寸**: 64 字节（RSA 约 256 字节）
- **前向保密**: 支持前向保密协议，提升长期安全性

---

## 数据库设计

### 主要表结构

- **users** - 用户表
  - 用户ID、设备ID、用户名、显示名称、邮箱
  - RSA 公钥、X25519 公钥、Ed25519 公钥
  - 密钥版本标识、公钥更新时间
  - 创建的分享、接收的分享
  - 密码验证器（Argon2id）

- **password_shares** - 密码分享表
  - 分享ID、密码ID、分享方、接收方、加密数据
  - 创建时间、过期时间、权限、状态、分享类型
  - 协议版本（v2/v3）

- **share_audit_logs** - 分享审计日志表
  - 分享操作记录（创建、接收、撤销等）

- **online_users** - 在线用户表
  - 用户ID、用户名、显示名称、位置信息
  - 最后活跃时间、会话ID

---

## 安全设计

### 1. 认证与授权

- JWT 认证机制
- Token 有效期控制（24小时）
- Refresh Token 轮换
- API 权限控制
- WebSocket 连接认证
- 限流保护（Bucket4j + Redis）

### 2. 数据加密

- **密钥派生**: Argon2id（自适应性能调优）
- **用户密钥对**:
  - RSA-2048（兼容性）
  - X25519（ECDH）
  - Ed25519（数字签名）
- **会话密钥**: AES-256-GCM
- **传输加密**: HTTPS + WSS (WebSocket Secure)
- **签名验证**: Ed25519 / HMAC-SHA256

### 3. 数据保护

- 密码数据不在服务端明文存储
- 会话密钥使用后立即销毁
- 敏感数据不记录日志
- 数据库连接加密
- 多设备限制（最多5台设备）

---

## 版本历史

### v3.6.0 (最新)

**密码算法升级**

- 新增 X25519/Ed25519 密钥对支持
- 新增 ECC 公钥上传 API
- 新增用户密钥信息查询 API
- 支持密码分享协议版本协商（v2/v3）
- 数据库添加 X25519/Ed25519 公钥字段
- User 实体扩展支持多版本密钥

**安全增强**

- 支持 v2.0（RSA）与 v3.0（X25519/Ed25519）互操作
- 自动版本协商机制
- 向后兼容保证

### v3.5.0

- 修复相关同步问题
- 优化连接管理

### v3.4.2

- 文档和证书更新

### v3.4.0

- 架构兼容性修复
- 完善错误处理机制

### v3.3.3

- 增加 Challenge-Response 登录机制
- 提升登录安全性

### v3.3.2

- 安全加固第三阶段完成
- 多设备支持（最多5台设备）

### v3.2.2

- 安全加固第二阶段完成
- Argon2id 密码哈希

### v3.1.2

- 密钥架构升级
- 三层安全架构兼容
- 注册流程优化

### v3.0.2

- 安全加固第一阶段完成
- 完整的后端API集成
- WebSocket 实时通知
- 附近发现功能

---

## 定时任务

### 分享过期检查
- **频率**：每小时一次
- **功能**：将过期的分享状态更新为 EXPIRED

### 在线用户清理
- **频率**：每分钟一次
- **功能**：清理 2 分钟未活跃的在线用户记录

### 注册清理
- **频率**：每小时一次
- **功能**：清理超过24小时未完成的注册记录

---

## 开发指南

### API 测试

```bash
# 注册用户
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "test-device-001",
    "username": "testuser",
    "displayName": "测试用户",
    "email": "test@example.com",
    "publicKey": "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
    "x25519PublicKey": "X25519_PUBLIC_KEY_BASE64",
    "ed25519PublicKey": "ED25519_PUBLIC_KEY_BASE64"
  }'

# 登录
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "usr_abc123xyz",
    "deviceId": "test-device-001",
    "signature": "signature"
  }'

# 搜索用户
curl -X GET "http://localhost:8080/api/v1/users/search?query=test" \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# 上传 ECC 公钥
curl -X PUT http://localhost:8080/api/v1/users/me/ecc-public-keys \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "x25519PublicKey": "X25519_PUBLIC_KEY_BASE64",
    "ed25519PublicKey": "ED25519_PUBLIC_KEY_BASE64"
  }'

# 创建分享（v3.0）
curl -X POST http://localhost:8080/api/v1/shares \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "passwordId": "pwd_123",
    "title": "测试密码",
    "username": "test@example.com",
    "encryptedPassword": "encrypted_password_here",
    "toUserId": "usr_def456uvw",
    "expireInMinutes": 1440,
    "permission": {
      "canView": true,
      "canSave": true,
      "isRevocable": true
    },
    "shareType": "USER_TO_USER",
    "protocolVersion": "v3"
  }'
```

---

## 许可证

Copyright © 2024 SafeVault. All rights reserved.

---

<div align="center">

**Made with ❤️ for Security**

</div>