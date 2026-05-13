package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Nonce 服务
 * 管理 Challenge-Response 机制的挑战码生成和验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NonceService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String NONCE_PREFIX = "auth:nonce:";
    private static final int NONCE_SIZE_BYTES = 32; // 256 bits
    private static final int NONCE_EXPIRATION_SECONDS = 30; // 30 seconds

    /**
     * 生成并存储 nonce
     *
     * @param email 用户邮箱
     * @return nonce 字符串
     */
    public String generateNonce(String email) {
        // 生成随机 nonce
        byte[] nonceBytes = new byte[NONCE_SIZE_BYTES];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);

        // 存储到 Redis，设置过期时间
        String key = NONCE_PREFIX + nonce;
        long expiresAt = System.currentTimeMillis() / 1000 + NONCE_EXPIRATION_SECONDS;

        NonceData nonceData = NonceData.builder()
                .email(email)
                .expiresAt(expiresAt)
                .used(false)
                .build();

        redisTemplate.opsForValue().set(
                key,
                nonceData.toJson(),
                NONCE_EXPIRATION_SECONDS,
                TimeUnit.SECONDS
        );

        log.debug("Generated nonce for email: {}, expires at: {}", email, expiresAt);
        return nonce;
    }

    /**
     * 验证 nonce 并标记为已使用（一次性）
     *
     * @param nonce nonce 字符串
     * @param email 期望的用户邮箱
     * @return 是否验证成功
     */
    public boolean validateAndConsumeNonce(String nonce, String email) {
        String key = NONCE_PREFIX + nonce;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            log.warn("Nonce not found or expired: {}", nonce);
            return false;
        }

        NonceData nonceData = NonceData.fromJson(json);

        // 检查是否已使用
        if (nonceData.isUsed()) {
            log.warn("Nonce already used: {}", nonce);
            return false;
        }

        // 检查邮箱是否匹配
        if (!email.equals(nonceData.getEmail())) {
            log.warn("Nonce email mismatch: expected={}, got={}", nonceData.getEmail(), email);
            return false;
        }

        // 检查是否过期
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentTime > nonceData.getExpiresAt()) {
            log.warn("Nonce expired: {}, expired at: {}, current: {}",
                    nonce, nonceData.getExpiresAt(), currentTime);
            redisTemplate.delete(key);
            return false;
        }

        // 标记为已使用（用后即焚）
        nonceData.setUsed(true);
        redisTemplate.opsForValue().set(key, nonceData.toJson(), 5, TimeUnit.MINUTES);

        log.debug("Nonce validated and marked as used: {}", nonce);
        return true;
    }

    /**
     * 清理已使用的 nonce（可选，由 Redis TTL 自动处理）
     *
     * @param nonce nonce 字符串
     */
    public void cleanupNonce(String nonce) {
        String key = NONCE_PREFIX + nonce;
        redisTemplate.delete(key);
    }

    /**
     * Nonce 数据结构
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class NonceData {
        private String email;
        private long expiresAt;
        private boolean used;

        public String toJson() {
            return String.format("{\"email\":\"%s\",\"expiresAt\":%d,\"used\":%b}",
                    email, expiresAt, used);
        }

        public static NonceData fromJson(String json) {
            // 使用正则表达式提取字段（更可靠）
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "\\{\"email\":\"([^\"]+)\",\"expiresAt\":(\\d+),\"used\":(true|false)\\}"
            );
            java.util.regex.Matcher matcher = pattern.matcher(json);

            if (matcher.matches()) {
                String email = matcher.group(1);
                long expiresAt = Long.parseLong(matcher.group(2));
                boolean used = Boolean.parseBoolean(matcher.group(3));

                return NonceData.builder()
                        .email(email)
                        .expiresAt(expiresAt)
                        .used(used)
                        .build();
            }

            // 降级处理：尝试简单解析
            try {
                String email = json.substring(
                        json.indexOf("\"email\":\"") + 9,
                        json.indexOf("\",\"expiresAt\":")
                );
                long expiresAt = Long.parseLong(json.substring(
                        json.indexOf("\"expiresAt\":") + 12,
                        json.indexOf(",\"used\"")
                ));
                boolean used = Boolean.parseBoolean(json.substring(
                        json.indexOf("\"used\":") + 7,
                        json.indexOf("}")
                ));

                return NonceData.builder()
                        .email(email)
                        .expiresAt(expiresAt)
                        .used(used)
                        .build();
            } catch (Exception e) {
                log.error("Failed to parse NonceData JSON: {}", json, e);
                throw new RuntimeException("Invalid NonceData format", e);
            }
        }
    }
}
