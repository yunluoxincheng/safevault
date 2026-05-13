package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.ttt.safevaultbackend.dto.PendingUser;
import org.ttt.safevaultbackend.exception.BusinessException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 待验证用户服务
 * 管理 Redis 中的待验证用户数据
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PendingUserService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${email.verification.pending-user-expiration-minutes:30}")
    private long pendingUserExpirationMinutes;

    private static final String PENDING_USER_KEY_PREFIX = "pending:user:";
    private static final String EMAIL_TOKEN_KEY_PREFIX = "email:token:";

    /**
     * Redis Key 格式
     */
    private String getPendingUserKey(String email) {
        return PENDING_USER_KEY_PREFIX + email.toLowerCase();
    }

    private String getEmailTokenKey(String token) {
        return EMAIL_TOKEN_KEY_PREFIX + token;
    }

    /**
     * 保存待验证用户到 Redis
     *
     * @param pendingUser 待验证用户
     * @return 是否保存成功
     */
    public boolean savePendingUser(PendingUser pendingUser) {
        try {
            String userKey = getPendingUserKey(pendingUser.getEmail());

            // 设置创建时间
            if (pendingUser.getCreatedAt() == null) {
                pendingUser.setCreatedAt(LocalDateTime.now());
            }

            log.info("准备保存待验证用户到Redis: email={}, username={}, token={}, tokenExpiresAt={}",
                    pendingUser.getEmail(),
                    pendingUser.getUsername(),
                    pendingUser.getVerificationToken(),
                    pendingUser.getTokenExpiresAt());

            // 保存到 Redis，设置过期时间
            redisTemplate.opsForValue().set(
                    userKey,
                    pendingUser,
                    Duration.ofMinutes(pendingUserExpirationMinutes)
            );

            log.info("已保存用户数据到Redis: key={}", userKey);

            // 同时保存 token -> email 的映射，方便验证时查找
            if (pendingUser.getVerificationToken() != null) {
                String tokenKey = getEmailTokenKey(pendingUser.getVerificationToken());
                String email = pendingUser.getEmail().toLowerCase();

                redisTemplate.opsForValue().set(
                        tokenKey,
                        email,
                        Duration.ofMinutes(pendingUserExpirationMinutes)
                );

                log.info("已保存token映射到Redis: tokenKey={}, email={}", tokenKey, email);

                // 验证保存是否成功（立即读取确认）
                Object savedEmail = redisTemplate.opsForValue().get(tokenKey);
                log.info("验证token映射保存结果: expected={}, actual={}, match={}",
                        email, savedEmail, email.equals(savedEmail));

                if (!email.equals(savedEmail)) {
                    log.error("Redis token映射验证失败！保存的值与读取的值不一致");
                }
            }

            log.info("保存待验证用户到 Redis 成功: email={}, expiresIn={}分钟",
                    pendingUser.getEmail(), pendingUserExpirationMinutes);
            return true;

        } catch (Exception e) {
            // 输出完整的异常堆栈
            log.error("保存待验证用户失败: email={}, 错误类型: {}, 错误信息: {}",
                    pendingUser.getEmail(), e.getClass().getName(), e.getMessage(), e);

            // 根据异常类型给出更具体的错误信息
            String errorMessage = "保存用户信息失败";
            if (e.getMessage() != null) {
                if (e.getMessage().contains("Connection refused") || e.getMessage().contains("Unable to connect")) {
                    errorMessage = "无法连接到 Redis 服务器，请确保 Redis 已启动";
                } else if (e.getMessage().contains("NOAUTH")) {
                    errorMessage = "Redis 认证失败，请检查密码配置";
                } else if (e.getMessage().contains("IOExecption")) {
                    errorMessage = "Redis 连接异常";
                }
            }

            throw new BusinessException("REDIS_ERROR", errorMessage + ": " + e.getMessage());
        }
    }

    /**
     * 根据邮箱获取待验证用户
     *
     * @param email 邮箱
     * @return 待验证用户，不存在返回 null
     */
    public PendingUser getPendingUserByEmail(String email) {
        try {
            String key = getPendingUserKey(email);
            log.info("通过 email 查找待验证用户: email={}, key={}", email, key);

            Object obj = redisTemplate.opsForValue().get(key);

            log.info("Redis 查询结果: key={}, obj={}, objType={}",
                    key, obj, obj != null ? obj.getClass().getName() : "null");

            if (obj instanceof PendingUser) {
                return (PendingUser) obj;
            }

            // 尝试直接用 LinkedHashMap 反序列化（兼容旧数据）
            if (obj instanceof java.util.LinkedHashMap) {
                try {
                    @SuppressWarnings("unchecked")
                    java.util.LinkedHashMap<String, Object> map = (java.util.LinkedHashMap<String, Object>) obj;

                    log.info("从 LinkedHashMap 构建 PendingUser: email={}, username={}, tokenExpiresAt={}",
                            map.get("email"), map.get("username"), map.get("tokenExpiresAt"));

                    // 手动构建 PendingUser 对象，避免 Jackson 反序列化问题
                    return PendingUser.builder()
                            .email((String) map.get("email"))
                            .username((String) map.get("username"))
                            .displayName((String) map.get("displayName"))
                            .verificationToken((String) map.get("verificationToken"))
                            .tokenExpiresAt(parseLocalDateTime(map.get("tokenExpiresAt")))
                            .createdAt(parseLocalDateTime(map.get("createdAt")))
                            .lastEmailSentAt(parseLocalDateTime(map.get("lastEmailSentAt")))
                            .build();
                } catch (Exception e) {
                    log.error("从 LinkedHashMap 构建 PendingUser 失败", e);
                }
            }

            log.warn("未找到 PendingUser: email={}, key={}, objType={}",
                    email, key, obj != null ? obj.getClass().getName() : "null");
            return null;

        } catch (Exception e) {
            log.error("获取待验证用户失败: email={}", email, e);
            return null;
        }
    }

    /**
     * 根据验证令牌获取待验证用户
     *
     * @param token 验证令牌
     * @return 待验证用户，不存在或令牌无效返回 null
     */
    public PendingUser getPendingUserByToken(String token) {
        try {
            // 先通过 token 获取 email
            String key = getEmailTokenKey(token);

            log.info("通过令牌查找待验证用户: token={}, key={}", token, key);

            Object emailObj = redisTemplate.opsForValue().get(key);

            if (emailObj == null) {
                log.warn("未找到token对应的email: token={}, key={}", token, key);
                return null;
            }

            String email = emailObj.toString();
            log.info("找到token对应的email: token={}, email={}", token, email);

            return getPendingUserByEmail(email);

        } catch (Exception e) {
            log.error("通过令牌获取待验证用户失败: token={}", token, e);
            return null;
        }
    }

    /**
     * 删除待验证用户
     *
     * @param email 邮箱
     */
    public void deletePendingUser(String email) {
        try {
            PendingUser pendingUser = getPendingUserByEmail(email);
            if (pendingUser != null) {
                // 删除用户数据
                redisTemplate.delete(getPendingUserKey(email));

                // 删除 token 映射
                if (pendingUser.getVerificationToken() != null) {
                    redisTemplate.delete(getEmailTokenKey(pendingUser.getVerificationToken()));
                }

                log.info("删除待验证用户: email={}", email);
            }

        } catch (Exception e) {
            log.error("删除待验证用户失败: email={}", email, e);
        }
    }

    /**
     * 更新验证令牌和发送时间
     *
     * @param email           邮箱
     * @param newToken        新令牌
     * @param tokenExpiresAt  令牌过期时间
     */
    public void updateVerificationToken(String email, String newToken, LocalDateTime tokenExpiresAt) {
        try {
            PendingUser pendingUser = getPendingUserByEmail(email);

            if (pendingUser == null) {
                throw new BusinessException("PENDING_USER_NOT_FOUND", "待验证用户不存在或已过期");
            }

            // 删除旧的 token 映射
            if (pendingUser.getVerificationToken() != null) {
                redisTemplate.delete(getEmailTokenKey(pendingUser.getVerificationToken()));
            }

            // 更新令牌和时间
            pendingUser.setVerificationToken(newToken);
            pendingUser.setTokenExpiresAt(tokenExpiresAt);
            pendingUser.setLastEmailSentAt(LocalDateTime.now());

            // 重新保存
            savePendingUser(pendingUser);

            log.info("更新验证令牌: email={}", email);

        } catch (Exception e) {
            log.error("更新验证令牌失败: email={}", email, e);
            throw new BusinessException("REDIS_ERROR", "更新验证令牌失败");
        }
    }

    /**
     * 检查邮箱是否已注册（包括待验证用户）
     *
     * @param email 邮箱
     * @return 是否已存在
     */
    public boolean existsPendingUser(String email) {
        try {
            String key = getPendingUserKey(email);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));

        } catch (Exception e) {
            log.error("检查待验证用户存在性失败: email={}", email, e);
            return false;
        }
    }

    /**
     * 调试方法：获取 Redis 中的原始值
     */
    public java.util.Map<String, Object> debugGetRawValue(String email) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        try {
            String key = getPendingUserKey(email);
            Object obj = redisTemplate.opsForValue().get(key);

            result.put("key", key);
            result.put("exists", redisTemplate.hasKey(key));
            result.put("value", obj);
            result.put("valueType", obj != null ? obj.getClass().getName() : "null");

            if (obj instanceof java.util.LinkedHashMap) {
                result.put("mapContent", obj);
            }

            // 同时检查 token 映射
            if (obj instanceof PendingUser) {
                String tokenKey = getEmailTokenKey(((PendingUser) obj).getVerificationToken());
                Object tokenValue = redisTemplate.opsForValue().get(tokenKey);
                result.put("tokenKey", tokenKey);
                result.put("tokenValue", tokenValue);
            }

        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 为用户生成验证令牌（256 位安全随机数）
     *
     * @return Base64 URL 安全编码的令牌
     */
    public String generateVerificationToken() {
        byte[] tokenBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    /**
     * 解析 LocalDateTime（兼容多种格式）
     */
    private LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof String) {
            return LocalDateTime.parse((String) value);
        }
        return null;
    }
}
