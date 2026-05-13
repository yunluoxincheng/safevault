package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 已撤销的令牌实体
 * 用于存储已注销的 JWT 令牌，防止其被继续使用
 */
@Entity
@Table(name = "revoked_tokens", indexes = {
    @Index(name = "idx_revoked_token", columnList = "token"),
    @Index(name = "idx_revoked_user_device", columnList = "user_id,device_id"),
    @Index(name = "idx_revoked_expires_at", columnList = "expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户 ID
     */
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /**
     * 设备 ID
     */
    @Column(name = "device_id", length = 255)
    private String deviceId;

    /**
     * 撤销的令牌（JWT 的 jti 或完整令牌的哈希）
     * 存储哈希值以节省空间并提高安全性
     */
    @Column(name = "token", nullable = false, length = 64)
    private String token;

    /**
     * 令牌过期时间
     * 用于定期清理过期的撤销记录
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 撤销时间
     */
    @Column(name = "revoked_at", nullable = false)
    private LocalDateTime revokedAt;

    /**
     * 撤销原因
     */
    @Column(name = "revoke_reason", length = 50)
    private String revokeReason; // LOGOUT, PASSWORD_CHANGE, ACCOUNT_DELETE, etc.

    @PrePersist
    protected void onCreate() {
        revokedAt = LocalDateTime.now();
    }
}
