package org.ttt.safevaultbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待验证用户缓存模型
 * 存储在 Redis 中，验证成功后才写入数据库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户邮箱（作为 Redis key 的一部分）
     */
    private String email;

    /**
     * 用户名
     */
    private String username;

    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 验证令牌
     */
    private String verificationToken;

    /**
     * 令牌过期时间
     */
    private LocalDateTime tokenExpiresAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后发送验证邮件的时间
     */
    private LocalDateTime lastEmailSentAt;

    /**
     * 检查令牌是否过期
     */
    public boolean isTokenExpired() {
        if (tokenExpiresAt == null) {
            System.err.println("[PendingUser.isTokenExpired] tokenExpiresAt is NULL!");
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean expired = tokenExpiresAt.isBefore(now);
        // 诊断日志
        System.err.println("[PendingUser.isTokenExpired] tokenExpiresAt=" + tokenExpiresAt +
                          ", now=" + now + ", expired=" + expired);
        return expired;
    }

    /**
     * 检查是否允许重新发送验证邮件（60秒冷却）
     */
    public boolean canResendEmail() {
        if (lastEmailSentAt == null) {
            return true;
        }
        return lastEmailSentAt.plusSeconds(60).isBefore(LocalDateTime.now());
    }
}
