# 安全加固第三阶段 - 技术设计文档

## Context

第三阶段专注于认证和授权机制的安全加固。这些修复将显著提升系统的安全性。

### 核心问题
1. **JWT使用HS256** - 对称加密，密钥泄露可伪造任意Token
2. **Token过期时间长** - 24小时访问Token，30天刷新Token
3. **HMAC签名未验证** - 仅检查Base64格式
4. **无速率限制** - 暴力破解无防护
5. **无并发控制** - 无限设备同时登录

### 约束条件
- **开发环境** - 不需要考虑用户兼容性
- **Redis已配置** - 可直接用于速率限制
- **设备管理已实现** - 只需添加数量限制

---

## Goals / Non-Goals

### Goals（目标）
1. JWT升级到RS256非对称加密
2. Token过期时间缩短到合理范围
3. 实现完整的HMAC签名验证
4. 添加API速率限制
5. 实现设备并发控制

### Non-Goals（非目标）
1. 不修改API接口
2. 不改变业务逻辑
3. 不考虑旧版本兼容

---

## Decisions

### 决策1: JWT从HS256升级到RS256

**选择**: 使用RSA-2048密钥对，私钥签名，公钥验证

**理由**:
- **密钥管理**: 私钥仅服务器持有，泄露风险低
- **密钥轮换**: 可定期更换公钥而不影响所有Token
- **性能**: RSA-2048签名/验证在服务器上性能可接受
- **标准**: OAuth 2.0推荐的算法

**技术对比**:
| 特性 | HS256 | RS256 |
|------|-------|-------|
| 算法 | HMAC-SHA256 | RSA-SHA256 |
| 密钥 | 对称（共享） | 非对称（公私钥） |
| 密钥泄露影响 | 可伪造任意Token | 仅影响新Token |
| 性能 | 快 | 慢（可接受） |

**密钥生成**:
```bash
# 生成RSA-2048密钥对
openssl genrsa -out jwt_private.pem 2048

# 提取公钥
openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem

# 转换为PKCS8格式（Java使用）
openssl pkcs8 -topk8 -inform PEM -outform PEM \
  -nocrypt -in jwt_private.pem -out jwt_private_pkcs8.pem

# Base64编码（用于环境变量）
cat jwt_private_pkcs8.pem | base64 -w 0
cat jwt_public.pem | base64 -w 0
```

**环境变量配置**:
```yaml
# application.yml
jwt:
  rsa:
    private-key: ${JWT_RSA_PRIVATE_KEY}  # Base64编码的PKCS8私钥
    public-key: ${JWT_RSA_PUBLIC_KEY}    # Base64编码的公钥
  access-token-expiration: 900000        # 15分钟
  refresh-token-expiration: 604800000    # 7天
```

---

### 决策2: Token过期时间调整

**选择**: 访问Token 15分钟，刷新Token 7天

**理由**:
- **15分钟访问Token**: 平衡安全性和用户体验
- **7天刷新Token**: 符合OAuth 2.0最佳实践
- **自动刷新**: 前端自动处理刷新，用户无感知

**对比分析**:
| 配置 | 旧值 | 新值 | 影响 |
|------|------|------|------|
| 访问Token | 24小时 | 15分钟 | 更频繁刷新 |
| 刷新Token | 30天 | 7天 | 每周登录一次 |

**用户体验影响**:
- **正常使用**: 自动刷新，用户无感知
- **长期未使用**: 超过7天需要重新登录
- **Token泄露**: 最多15分钟有效

---

### 决策3: HMAC签名完整验证

**选择**: 实现完整的HMAC-SHA256签名验证

**理由**:
- 当前仅检查Base64格式，可被伪造
- HMAC确保请求来自合法客户端
- 时间戳防止重放攻击

**实现方案**:
```java
// 签名计算
private String computeHmacSignature(String data, String key) {
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
}

// 签名验证
private void verifyDerivedKeySignature(
    String email, String deviceId, String signature, Long timestamp) {

    // 1. 时间戳验证（5分钟窗口）
    long timeDiff = Math.abs(System.currentTimeMillis() - timestamp);
    if (timeDiff > 300000) {
        throw new BusinessException("INVALID_TIMESTAMP");
    }

    // 2. 获取用户盐值
    User user = userRepository.findByEmail(email).orElseThrow();

    // 3. 计算期望签名
    String data = email + deviceId + timestamp;
    String expected = computeHmacSignature(data, user.getPasswordSalt());

    // 4. 时间安全比较
    if (!MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
    )) {
        throw new BusinessException("INVALID_SIGNATURE");
    }
}
```

---

### 决策4: 速率限制实现

**选择**: 使用Bucket4j + Redis实现分布式速率限制

**理由**:
- **Bucket4j**: Java令牌桶算法实现
- **Redis**: 分布式支持，多实例共享状态
- **灵活**: 可配置不同的速率策略

**速率策略**:
| API | 限制 | 窗口 |
|-----|------|------|
| POST /v1/auth/login-by-email | 5次 | 分钟 |
| POST /v1/auth/register-email | 3次 | 分钟 |
| POST /v1/auth/resend-verification | 10次 | 小时 |

**实现方案**:
```java
@Configuration
public class RateLimitConfig {

    @Bean
    public BucketResolver bucketResolver(RedisTemplate<String, Object> redisTemplate) {
        return (request) -> {
            String key = getClientKey(request);
            return buildBucket(key, redisTemplate);
        };
    }

    private Bucket buildBucket(String key, RedisTemplate<String, Object> redisTemplate) {
        BucketProxy bucket = Bucket4j.builder()
            .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
            .build()
            .asProxy();
        return bucket;
    }
}

@Aspect
@Component
public class RateLimitAspect {

    @Around("@annotation(rateLimit)")
    public Object rateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = getClientKey(request);
        Bucket bucket = bucketResolver.resolve(request);

        if (bucket.tryConsume(1)) {
            return joinPoint.proceed();
        } else {
            throw new RateLimitExceededException("请求过于频繁，请稍后再试");
        }
    }
}
```

---

### 决策5: 并发登录控制

**选择**: 限制最多5台设备同时登录

**理由**:
- **安全性**: 限制异常登录
- **可管理**: 用户可查看和移除设备
- **灵活性**: 可调整限制数量

**设备管理策略**:
```
1. 用户登录时检查当前设备数
2. 如果 < 5，允许登录并记录设备
3. 如果 = 5，撤销最久未使用的设备Token
4. 返回当前设备列表给前端
```

**实现方案**:
```java
public LoginResponse login(LoginRequest request) {
    User user = authenticate(request);

    // 获取活跃设备
    List<Device> devices = deviceService.getActiveDevices(user.getUserId());

    // 检查设备数量
    if (devices.size() >= user.getMaxDevices()) {
        Device oldestDevice = devices.stream()
            .min(Comparator.comparing(Device::getLastActiveTime))
            .orElseThrow();

        tokenRevokeService.revokeDevice(
            oldestDevice.getDeviceId(),
            user.getUserId()
        );
    }

    // 记录新设备
    Device newDevice = Device.builder()
        .deviceId(request.getDeviceId())
        .deviceName(request.getDeviceName())
        .lastActiveTime(System.currentTimeMillis())
        .build();

    deviceService.saveDevice(user.getUserId(), newDevice);

    String token = generateToken(user);

    return LoginResponse.builder()
        .token(token)
        .devices(deviceService.getActiveDevices(user.getUserId()))
        .build();
}
```

---

## Testing Strategy

### JWT升级测试
- RS256 Token生成和验证
- Token过期时间验证

### 速率限制测试
- 正常请求通过
- 超过限制被拒绝
- 窗口重置

### 并发控制测试
- 5台设备登录
- 第6台设备登录
- 设备移除

---

## Success Criteria

- [ ] JWT使用RS256签名
- [ ] Token过期时间已调整
- [ ] HMAC签名验证已实现
- [ ] 速率限制已生效
- [ ] 并发控制已生效
- [ ] 所有测试通过
