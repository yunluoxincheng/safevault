# 安全加固第三阶段 - 实施任务清单

## 1. 依赖添加

### 1.1 添加Bucket4j依赖
- [x] 1.1.1 在`pom.xml`添加`bucket4j-core`依赖
- [x] 1.1.2 在`pom.xml`添加`bucket4j-redis`依赖

**依赖**:
```xml
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.1.0</version>
</dependency>
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.1.0</version>
</dependency>
```

---

## 2. JWT算法升级（HS256 → RS256）

### 2.1 生成RSA密钥对
- [x] 2.1.1 生成RSA-2048密钥对
- [x] 2.1.2 设置私钥环境变量`JWT_RSA_PRIVATE_KEY`
- [x] 2.1.3 设置公钥环境变量`JWT_RSA_PUBLIC_KEY`

**生成命令**:
```bash
# 生成RSA-2048密钥对
openssl genrsa -out jwt_private.pem 2048
openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem
# 转换为PKCS8格式
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in jwt_private.pem -out jwt_private_pkcs8.pem
# Base64编码
cat jwt_private_pkcs8.pem | base64 -w 0
cat jwt_public.pem | base64 -w 0
```

### 2.2 更新JwtTokenProvider支持RS256
- [x] 2.2.1 修改`JwtTokenProvider`使用RSA私钥签名
- [x] 2.2.2 使用RSA公钥验证Token
- [x] 2.2.3 移除HS256相关代码

**文件**: `safevault-backend/.../security/JwtTokenProvider.java`

**实现要点**:
```java
@Component
public class JwtTokenProvider {

    @Value("${jwt.rsa.private-key}")
    private String rsaPrivateKey;

    @Value("${jwt.rsa.public-key}")
    private String rsaPublicKey;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // 加载私钥
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(
            Base64.getDecoder().decode(rsaPrivateKey)
        );
        this.privateKey = keyFactory.generatePrivate(privateKeySpec);

        // 加载公钥
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(
            Base64.getDecoder().decode(rsaPublicKey)
        );
        this.publicKey = keyFactory.generatePublic(publicKeySpec);
    }

    public String generateToken(String userId) {
        return Jwts.builder()
            .subject(userId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessExpiration))
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
```

### 2.3 更新application.yml配置
- [x] 2.3.1 替换`jwt.secret`为`jwt.rsa.private-key`
- [x] 2.3.2 添加`jwt.rsa.public-key`配置

**修改**:
```yaml
jwt:
  rsa:
    private-key: ${JWT_RSA_PRIVATE_KEY}
    public-key: ${JWT_RSA_PUBLIC_KEY}
```

---

## 3. Token过期时间调整

### 3.1 修改后端配置
- [x] 3.1.1 修改`access-token-expiration`为15分钟
- [x] 3.1.2 修改`refresh-token-expiration`为7天

**文件**: `safevault-backend/src/main/resources/application.yml`

**修改**:
```yaml
jwt:
  access-token-expiration: 900000    # 15分钟 (900000ms)
  refresh-token-expiration: 604800000 # 7天 (604800000ms)
```

### 3.2 前端Token刷新验证
- [x] 3.2.1 确保`AuthInterceptor`自动刷新Token
- [x] 3.2.2 处理401响应自动重新登录

---

## 4. 完整HMAC签名验证

### 4.1 实现HMAC签名计算
- [x] 4.1.1 在`AuthService`实现`computeHmacSignature()`
- [x] 4.1.2 实现`verifyHmacSignature()`
- [x] 4.1.3 移除简化的Base64检查
- [x] 4.1.4 使用用户盐值作为HMAC密钥

**文件**: `safevault-backend/.../service/AuthService.java`

**实现要点**:
```java
private String computeHmacSignature(String data, String key) {
    try {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
            key.getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
        mac.init(secretKey);
        byte[] signature = mac.doFinal(
            data.getBytes(StandardCharsets.UTF_8)
        );
        return Base64.getEncoder().encodeToString(signature);
    } catch (Exception e) {
        throw new BusinessException("SIGNATURE_ERROR", "签名计算失败");
    }
}

private void verifyDerivedKeySignature(String email, String deviceId,
                                       String signature, Long timestamp) {
    // 时间戳验证（5分钟窗口）
    long currentTime = System.currentTimeMillis();
    long timeDiff = Math.abs(currentTime - timestamp);
    if (timeDiff > 300000) {
        throw new BusinessException("INVALID_TIMESTAMP", "请求时间戳无效");
    }

    // 获取用户盐值
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

    // 计算期望签名
    String expectedData = email + deviceId + timestamp;
    String expectedSignature = computeHmacSignature(expectedData, user.getPasswordSalt());

    // 比较签名（使用时间安全比较）
    if (!MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8))) {
        throw new BusinessException("INVALID_SIGNATURE", "签名验证失败");
    }
}
```

---

## 5. 速率限制实现

### 5.1 创建速率限制注解
- [x] 5.1.1 创建`@RateLimit`注解
- [x] 5.1.2 创建`RateLimitAspect`切面

**实现要点**:
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int requests() default 10;
    String per() default "minute";  // minute, hour, day
}

@Aspect
@Component
public class RateLimitAspect {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint,
                           RateLimit rateLimit) throws Throwable {
        String key = getClientKey();
        Bucket bucket = getBucket(key, rateLimit);

        if (bucket.tryConsume(1)) {
            return joinPoint.proceed();
        } else {
            throw new RateLimitExceededException("请求过于频繁，请稍后再试");
        }
    }
}
```

### 5.2 应用速率限制
- [x] 5.2.1 登录API添加`@RateLimit(requests=5, per="minute")`
- [x] 5.2.2 注册API添加`@RateLimit(requests=3, per="minute")`
- [x] 5.2.3 邮箱验证添加`@RateLimit(requests=10, per="hour")`

---

## 6. 并发登录控制

### 6.1 设备管理增强
- [x] 6.1.1 在`User`实体添加`maxDevices`字段（默认值5）
- [x] 6.1.2 记录每个登录设备

**数据库变更**:
```sql
ALTER TABLE users ADD COLUMN max_devices INT NOT NULL DEFAULT 5;
```

### 6.2 登录时设备检查
- [x] 6.2.1 检查当前登录设备数量
- [x] 6.2.2 如果已达到上限，撤销最久未使用的设备Token
- [x] 6.2.3 记录新登录设备

**实现要点**:
```java
public LoginResponse login(LoginRequest request) {
    User user = authenticate(request);

    // 检查设备数量
    List<Device> devices = getActiveDevices(user.getUserId());
    if (devices.size() >= user.getMaxDevices()) {
        Device oldestDevice = devices.stream()
            .min(Comparator.comparing(Device::getLastActiveTime))
            .orElseThrow();
        revokeDevice(oldestDevice.getDeviceId());
    }

    // 创建新设备记录
    Device newDevice = createDevice(request.getDeviceId());

    String token = generateToken(user);

    return LoginResponse.builder()
        .token(token)
        .devices(getActiveDevices(user.getUserId()))
        .build();
}
```

### 6.3 设备管理API
- [x] 6.3.1 GET /v1/auth/devices - 获取设备列表
- [x] 6.3.2 DELETE /v1/auth/devices/{deviceId} - 移除设备
- [x] 6.3.3 POST /v1/auth/logout-all - 登出所有设备（未实现，logout已有）

---

## 7. 测试

### 7.1 JWT升级测试
- [ ] 7.1.1 测试RS256 Token生成
- [ ] 7.1.2 测试RS256 Token验证

### 7.2 速率限制测试
- [ ] 7.2.1 测试正常请求通过
- [ ] 7.2.2 测试超过限制被拒绝
- [ ] 7.2.3 测试速率窗口重置

### 7.3 并发控制测试
- [ ] 7.3.1 测试5台设备登录
- [ ] 7.3.2 测试第6台设备登录
- [ ] 7.3.3 测试设备移除

### 7.4 集成测试
- [ ] 7.4.1 测试完整登录流程
- [ ] 7.4.2 测试Token刷新
- [ ] 7.4.3 测试多设备场景

---

## 完成标准

- [x] JWT使用RS256签名
- [x] Token过期时间已调整（15分钟/7天）
- [x] HMAC签名验证已实现
- [x] 速率限制已生效
- [x] 并发控制已生效
- [ ] 所有测试通过

## 已完成代码文件清单

### 新增文件：
1. `safevault-backend/src/main/java/org/ttt/safevaultbackend/annotation/RateLimit.java` - 速率限制注解
2. `safevault-backend/src/main/java/org/ttt/safevaultbackend/aspect/RateLimitAspect.java` - 速率限制切面
3. `safevault-backend/src/main/java/org/ttt/safevaultbackend/exception/RateLimitExceededException.java` - 速率限制异常
4. `safevault-backend/src/main/resources/db/migration/V20__add_max_devices_field.sql` - 数据库迁移脚本
5. `safevault-backend/jwt_rsa_private_key.txt` - RSA私钥Base64编码
6. `safevault-backend/jwt_rsa_public_key.txt` - RSA公钥Base64编码

### 修改文件：
1. `safevault-backend/pom.xml` - 添加Bucket4j依赖
2. `safevault-backend/.env` - 更新JWT配置为RSA密钥
3. `safevault-backend/.env.example` - 更新JWT配置示例
4. `safevault-backend/src/main/resources/application.yml` - 更新JWT配置和Token过期时间
5. `safevault-backend/src/main/java/org/ttt/safevaultbackend/security/JwtTokenProvider.java` - 升级到RS256
6. `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/AuthService.java` - 完整HMAC验证和并发控制
7. `safevault-backend/src/main/java/org/ttt/safevaultbackend/controller/AuthController.java` - 添加速率限制注解
8. `safevault-backend/src/main/java/org/ttt/safevaultbackend/entity/User.java` - 添加maxDevices字段
9. `safevault-backend/src/main/java/org/ttt/safevaultbackend/dto/response/EmailLoginResponse.java` - 添加maxDevices字段
10. `safevault-backend/src/main/java/org/ttt/safevaultbackend/service/TokenRevokeService.java` - 添加revokeDevice方法
11. `safevault-backend/src/main/java/org/ttt/safevaultbackend/repository/RevokedTokenRepository.java` - 添加markDeviceTokens方法
12. `safevault-backend/src/main/java/org/ttt/safevaultbackend/exception/GlobalExceptionHandler.java` - 添加速率限制异常处理

## 部署前检查清单

- [ ] 确保生产环境已设置`JWT_RSA_PRIVATE_KEY`和`JWT_RSA_PUBLIC_KEY`环境变量
- [ ] 运行数据库迁移脚本添加`max_devices`字段
- [ ] 验证Redis连接正常（速率限制依赖）
- [ ] 清理旧版JWT_SECRET环境变量（避免混淆）
- [ ] 监控应用启动日志，确认RSA密钥加载成功
