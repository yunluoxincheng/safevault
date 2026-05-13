package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ttt.safevaultbackend.entity.RevokedToken;
import org.ttt.safevaultbackend.repository.RevokedTokenRepository;
import org.ttt.safevaultbackend.security.JwtTokenProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 令牌撤销服务
 * 负责管理和验证已撤销的 JWT 令牌
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevokeService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtTokenProvider tokenProvider;

    /**
     * 撤销令牌
     *
     * @param token        要撤销的 JWT 令牌
     * @param userId       用户 ID
     * @param deviceId     设备 ID
     * @param revokeReason 撤销原因
     */
    @Transactional
    public void revokeToken(String token, String userId, String deviceId, String revokeReason) {
        try {
            // 计算令牌哈希值
            String tokenHash = hashToken(token);

            // 获取令牌过期时间
            LocalDateTime expiresAt = getTokenExpiration(token);

            // 检查是否已经撤销
            if (revokedTokenRepository.existsByToken(tokenHash)) {
                log.debug("令牌已被撤销: userId={}, deviceId={}", userId, deviceId);
                return;
            }

            // 创建撤销记录
            RevokedToken revokedToken = RevokedToken.builder()
                    .userId(userId)
                    .deviceId(deviceId)
                    .token(tokenHash)
                    .expiresAt(expiresAt)
                    .revokeReason(revokeReason)
                    .build();

            revokedTokenRepository.save(revokedToken);

            log.info("令牌已撤销: userId={}, deviceId={}, reason={}", userId, deviceId, revokeReason);

        } catch (Exception e) {
            log.error("撤销令牌失败: userId={}, deviceId={}", userId, deviceId, e);
            throw new RuntimeException("撤销令牌失败", e);
        }
    }

    /**
     * 检查令牌是否已被撤销
     *
     * @param token    JWT 令牌
     * @param userId   用户 ID
     * @param deviceId 设备 ID
     * @return true 表示令牌已被撤销
     */
    @Transactional(readOnly = true)
    public boolean isTokenRevoked(String token, String userId, String deviceId) {
        try {
            String tokenHash = hashToken(token);
            return revokedTokenRepository.isTokenRevoked(userId, deviceId, tokenHash, LocalDateTime.now());
        } catch (Exception e) {
            log.error("检查令牌撤销状态失败: userId={}", userId, e);
            return false;
        }
    }

    /**
     * 撤销用户的所有令牌（用于密码修改或账户删除）
     *
     * @param userId       用户 ID
     * @param revokeReason 撤销原因
     */
    @Transactional
    public void revokeAllUserTokens(String userId, String revokeReason) {
        try {
            int count = revokedTokenRepository.markAllUserTokens(userId, revokeReason);
            log.info("标记用户所有令牌为已撤销: userId={}, count={}, reason={}", userId, count, revokeReason);
        } catch (Exception e) {
            log.error("撤销用户所有令牌失败: userId={}", userId, e);
            throw new RuntimeException("撤销令牌失败", e);
        }
    }

    /**
     * 定时清理过期的撤销记录
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            int deleted = revokedTokenRepository.deleteExpiredTokens(LocalDateTime.now());
            log.info("清理过期的撤销记录: count={}", deleted);
        } catch (Exception e) {
            log.error("清理过期撤销记录失败", e);
        }
    }

    /**
     * 计算令牌的 SHA-256 哈希值
     *
     * @param token JWT 令牌
     * @return Base64 编码的哈希值
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("计算令牌哈希失败", e);
        }
    }

    /**
     * 获取令牌的过期时间
     * 安全加固第三阶段：适配RS256算法，使用JwtTokenProvider解析
     *
     * @param token JWT 令牌
     * @return 过期时间
     */
    private LocalDateTime getTokenExpiration(String token) {
        try {
            // 直接使用JwtTokenProvider验证令牌
            if (!tokenProvider.validateToken(token)) {
                // 令牌无效，返回当前时间
                return LocalDateTime.now();
            }

            // 解析JWT获取过期时间
            io.jsonwebtoken.Jws<io.jsonwebtoken.Claims> jws =
                io.jsonwebtoken.Jwts.parser()
                    .build()
                    .parseSignedClaims(token);

            java.util.Date expiration = jws.getPayload().getExpiration();
            return expiration != null
                ? expiration.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                : LocalDateTime.now().plusDays(30); // 默认30天后过期

        } catch (Exception e) {
            log.warn("解析令牌过期时间失败，使用默认值: {}", e.getMessage());
            return LocalDateTime.now().plusDays(30);
        }
    }

    /**
     * 撤销设备的所有令牌
     * 安全加固第三阶段：用于并发登录控制
     *
     * @param deviceId 要撤销的设备ID
     * @param userId 用户ID
     */
    @Transactional
    public void revokeDevice(String deviceId, String userId) {
        try {
            // 标记该设备的所有令牌为已撤销
            int count = revokedTokenRepository.markDeviceTokens(userId, deviceId, "DEVICE_LIMIT_EXCEEDED");
            log.info("撤销设备令牌: userId={}, deviceId={}, count={}", userId, deviceId, count);
        } catch (Exception e) {
            log.error("撤销设备令牌失败: userId={}, deviceId={}", userId, deviceId, e);
            throw new RuntimeException("撤销设备令牌失败", e);
        }
    }
}
