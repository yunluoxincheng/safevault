# 安全加固第一阶段 - 技术设计文档

## Context

SafeVault项目经过全面安全审计，发现多个严重安全漏洞。这些漏洞主要集中在：
1. SSL/TLS证书验证被完全禁用
2. CORS和WebSocket配置允许所有来源
3. JWT密钥硬编码在代码中
4. 敏感配置信息明文存储
5. 调试端点在生产环境可访问
6. Token撤销机制未实际生效
7. 存在越权访问风险
8. 代码未启用混淆保护

### 约束条件
- **不能影响现有功能** - 所有修复必须向后兼容
- **不能要求数据迁移** - 本阶段不涉及数据格式变更
- **不能要求用户操作** - 用户无感知升级
- **Android 10+兼容** - 最低API级别29

### 利益相关者
- **最终用户** - 需要安全保障，但不能影响使用体验
- **运维团队** - 需要清晰的部署配置
- **开发团队** - 需要明确的技术方案

---

## Goals / Non-Goals

### Goals（目标）
1. 消除所有严重和高危安全漏洞（无数据迁移类）
2. 启用代码混淆减少APK大小30-40%
3. 建立安全的配置管理机制
4. 确保敏感信息不再硬编码

### Non-Goals（非目标）
1. 不修改JWT签名算法（HS256→RS256）- 留待第三阶段
2. 不迁移RSA密钥到AndroidKeyStore - 留待第二阶段
3. 不修改后端RSA填充方案 - 留待第二阶段
4. 不提高PBKDF2迭代次数 - 留待第二阶段

---

## Decisions

### 决策1: SSL/TLS证书验证修复

**选择**: 移除所有自定义SSL配置，使用Android系统默认验证

**理由**:
- 当前代码信任所有证书，完全破坏了HTTPS安全性
- Android系统默认证书验证已经足够安全
- 不需要实现SSL Pinning（增加维护复杂度）

**代码变更**:
```java
// 移除前（不安全）
X509TrustManager trustAllCerts = new X509TrustManager() {
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
};
okHttpBuilder.sslSocketFactory(sslSocketFactory, trustAllCerts);
okHttpBuilder.hostnameVerifier((hostname, session) -> true);

// 修复后（安全）
// 完全移除自定义SSL配置，使用系统默认
OkHttpClient.Builder okHttpBuilder = new OkHttpClient.Builder()
    .addInterceptor(authInterceptor)
    .connectTimeout(30, TimeUnit.SECONDS);
    // 系统默认SSL验证自动生效
```

**影响**:
- 功能无影响
- 安全性大幅提升
- 中间人攻击风险消除

**替代方案考虑**:
- 实现SSL Pinning - 增加复杂度，证书管理困难
- 仅在Release版本禁用 - 仍有开发版泄露风险

---

### 决策2: CORS和WebSocket来源限制

**选择**: 硬编码允许的域名列表

**理由**:
- 密码管理器前端域名固定
- 配置简单明确
- 不依赖动态配置

**代码变更**:
```java
// SecurityConfig.java
@Configuration
public class SecurityConfig {
    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
        "https://safevaultapp.top",
        "safevault://"  // Deep link scheme
    );

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(ALLOWED_ORIGINS);  // 注意：setAllowedOrigins而非setAllowedOriginPatterns
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        // ...
    }
}

// WebSocketConfig.java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private static final List<String> ALLOWED_ORIGINS = Arrays.asList(
        "https://safevaultapp.top",
        "safevault://"
    );

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationHandler, "/ws")
                .setAllowedOrigins("https://safevaultapp.top")  // 注意：setAllowedOrigins
                .withSockJS();
    }
}
```

**影响**:
- 如需添加新域名，需更新代码
- 如果配置错误，前端无法连接

**替代方案考虑**:
- 从环境变量读取 - 增加配置复杂度
- 使用数据库配置 - 过度设计

---

### 决策3: 调试端点保护

**选择**: 使用Spring Profile控制，仅开发环境启用

**理由**:
- Spring Profile是标准方案
- 不影响生产环境性能
- 保留开发时调试能力

**代码变更**:
```java
// 修改前
@GetMapping("/debug/pending-user")
public ResponseEntity<Map<String, Object>> debugPendingUser(...)

// 修改后
@Profile("dev")  // 仅dev环境启用
@GetMapping("/debug/pending-user")
public ResponseEntity<Map<String, Object>> debugPendingUser(...)

@Profile("dev")
@GetMapping("/debug/redis-raw")
public ResponseEntity<Map<String, Object>> debugRedisRaw(...)
```

**生产环境验证**:
```bash
# 生产环境应设置
spring.profiles.active=prod

# 或启动参数
--spring.profiles.active=prod
```

**影响**:
- 生产环境调试端点不可访问（404）
- 开发环境保持调试能力

---

### 决策4: Token撤销检查集成

**选择**: 在JWT认证过滤器中添加撤销检查

**理由**:
- 撤销机制已存在（TokenRevokeService）
- 只是未在过滤器中调用
- 最小改动实现功能

**代码变更**:
```java
// JwtAuthenticationFilter.java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain chain) {
    String jwt = getJwtFromRequest(request);

    if (jwt != null && tokenProvider.validateToken(jwt)) {
        String userId = tokenProvider.getUserIdFromToken(jwt);
        String deviceId = request.getHeader("X-Device-ID");

        // 新增：检查Token是否已被撤销
        if (tokenRevokeService.isTokenRevoked(jwt, userId, deviceId)) {
            logger.warn("Token已撤销: userId={}, deviceId={}", userId, deviceId);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Token已撤销\"}");
            return;
        }

        // 设置认证上下文
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    chain.doFilter(request, response);
}
```

**性能考虑**:
- 每次请求需要查询Redis
- Redis查询很快（<1ms）
- 可接受的性能开销

**替代方案考虑**:
- 本地缓存撤销列表 - 增加复杂度，可能不一致
- 定期同步 - 实时性差

---

### 决策5: 越权访问防护

**选择**: 从SecurityContext获取用户ID，不从请求头读取

**理由**:
- JWT已包含用户ID
- 从请求头读取可被伪造
- Spring Security标准做法

**代码变更**:
```java
// 创建辅助方法
public abstract class BaseController {
    protected String getCurrentUserId() {
        Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("未认证");
        }
        return (String) authentication.getPrincipal();
    }
}

// 使用示例
@RestController
@RequestMapping("/v1/vault")
public class VaultController extends BaseController {

    @GetMapping
    public ResponseEntity<VaultResponse> getVault() {
        // 修改前：@RequestHeader("X-User-Id") String userId
        String userId = getCurrentUserId();  // 从JWT获取
        VaultResponse response = vaultService.getVault(userId);
        return ResponseEntity.ok(response);
    }
}
```

**影响**:
- API签名变更（移除X-User-Id参数）
- 前端不需要传递X-User-Id头

---

### 决策6: 环境变量配置

**选择**: 使用Spring Boot的标准环境变量机制

**理由**:
- Spring Boot原生支持
- Docker/Kubernetes友好
- 符合12-Factor App原则

**代码变更**:
```yaml
# application.yml
spring:
  mail:
    host: ${MAIL_HOST:smtpdm.aliyun.com}
    port: ${MAIL_PORT:465}
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}  # 无默认值，必须设置

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/safevault}
    username: ${DB_USERNAME:safevault}
    password: ${DB_PASSWORD}  # 无默认值

  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}  # 无默认值

jwt:
  secret: ${JWT_SECRET}  # 无默认值，至少32字符
```

**启动验证**:
```java
@Component
public class ConfigValidator {
    @Value("${spring.mail.password}")
    private String mailPassword;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    public void validate() {
        if (mailPassword == null || mailPassword.isEmpty()) {
            throw new IllegalStateException("MAIL_PASSWORD环境变量未设置");
        }
        if (dbPassword == null || dbPassword.isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD环境变量未设置");
        }
        if (redisPassword == null || redisPassword.isEmpty()) {
            throw new IllegalStateException("REDIS_PASSWORD环境变量未设置");
        }
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET必须至少32字符");
        }
    }
}
```

**Docker Compose示例**:
```yaml
services:
  backend:
    image: yunluoxincheng/safevault-backend:latest
    environment:
      - MAIL_HOST=smtpdm.aliyun.com
      - MAIL_PASSWORD=${MAIL_PASSWORD}
      - DB_URL=jdbc:postgresql://postgres:5432/safevault
      - DB_PASSWORD=${DB_PASSWORD}
      - REDIS_HOST=redis
      - REDIS_PASSWORD=${REDIS_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
```

---

### 决策7: 代码混淆配置

**选择**: 启用R8混淆和资源压缩

**理由**:
- Android默认构建工具
- 自动优化和删除未使用代码
- 显著减少APK大小

**代码变更**:
```gradle
// app/build.gradle
android {
    buildTypes {
        release {
            minifyEnabled true  // 启用混淆
            shrinkResources true  // 启用资源压缩
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'),
                           'proguard-rules.pro'
        }
    }
}

// gradle.properties
android.r8.fullMode=true  // 启用R8完整模式

// app/proguard-rules.pro
# 保留模型类
-keep class com.ttt.safevault.model.** { *; }
-keep class com.ttt.safevault.dto.** { *; }

# 保留ViewModels
-keep class com.ttt.safevault.viewmodel.** { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Gson
-keep class com.google.gson.** { *; }
-keep class com.ttt.safevault.model.** { *; }

# 移除日志
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
```

**预期效果**:
- APK大小减少30-40%
- 反编译难度大幅增加

---

## Risks / Trade-offs

### 风险1: CORS配置错误导致前端无法连接

**概率**: 低
**影响**: 高
**缓解措施**:
1. 代码审查时重点检查CORS配置
2. 部署前在测试环境验证
3. 保留快速回滚机制

### 风险2: 环境变量未配置导致启动失败

**概率**: 中
**影响**: 高
**缓解措施**:
1. 提供清晰的部署文档
2. 启动时明确提示缺失的环境变量
3. 提供配置模板

### 风险3: ProGuard配置不当导致运行时错误

**概率**: 中
**影响**: 中
**缓解措施**:
1. 充分测试Release版本
2. 逐步添加ProGuard规则
3. 保留调试映射文件

### 风险4: Token撤销检查影响性能

**概率**: 低
**影响**: 低
**缓解措施**:
1. Redis查询很快（<1ms）
2. 可考虑添加本地缓存
3. 监控API响应时间

---

## Migration Plan

### 部署步骤

#### 阶段1: 准备（1-2天）
1. 生成所有需要的密钥和密码
2. 创建环境变量配置文件
3. 更新.gitignore

#### 阶段2: 代码修改（3-5天）
1. Android前端修复（1-2天）
2. 后端修复（2-3天）
3. 代码审查

#### 阶段3: 测试（2-3天）
1. 单元测试
2. 集成测试
3. 安全测试
4. 性能测试

#### 阶段4: 部署（1天）
1. 部署后端（带环境变量）
2. 部署前端APK
3. 验证功能
4. 监控日志

### Rollback Plan

**触发条件**:
- 严重功能故障
- 安全测试失败
- 性能严重下降

**回滚步骤**:
1. 停止新版本服务
2. 恢复之前版本容器/APK
3. 恢复之前配置
4. 验证功能恢复

**预计回滚时间**: <30分钟

---

## Open Questions

### Q1: 是否需要SSL Pinning?

**背景**: SSL Pinning可进一步防止中间人攻击

**分析**:
- 优点: 更高级别的安全
- 缺点: 证书管理复杂，更新困难

**建议**: 当前阶段不实现，作为未来增强项

### Q2: Token撤销是否需要持久化存储?

**背景**: 当前使用Redis存储撤销列表

**分析**:
- Redis已足够（高性能，支持TTL）
- 持久化存储增加复杂度

**建议**: 继续使用Redis，考虑持久化备份

### Q3: 是否需要添加API速率限制?

**背景**: 当前无速率限制

**分析**:
- 登录/注册API应有限制
- 需要引入依赖（如Bucket4j）

**建议**: 作为后续优化项，不在本次范围

---

## Testing Strategy

### 单元测试
- SSL/TLS连接测试
- Token撤销逻辑测试
- 越权访问防护测试

### 集成测试
- 完整认证流程
- CORS请求验证
- WebSocket连接

### 安全测试
- 尝试访问调试端点（应失败）
- 尝试越权访问（应失败）
- 尝试撤销后的Token（应失败）

### 性能测试
- API响应时间监测
- Token撤销检查开销

---

## Monitoring

### 部署后监控指标
1. API错误率
2. API响应时间
3. WebSocket连接成功率
4. 认证失败率

### 告警规则
- 错误率 > 5%
- 响应时间 > 1s
- 认证失败率 > 10%
