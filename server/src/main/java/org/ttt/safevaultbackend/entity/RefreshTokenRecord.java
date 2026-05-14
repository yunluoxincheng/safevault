package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Refresh Token 生命周期追踪记录
 *
 * 每个 refresh token 签发时创建一条记录，用于：
 * - Token 轮换：刷新时旧 token 标记 rotated=true
 * - 重用检测：rotated=true 的 token 被重用时撤销整个 family
 * - Token family 追踪：同一次签发链路的 token 共享 family（首次签发的 jti）
 */
@Entity
@Table(name = "refresh_token_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "jti", nullable = false, length = 64)
    private String jti;

    @Column(name = "family", nullable = false, length = 64)
    private String family;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "rotated", nullable = false)
    @Builder.Default
    private Boolean rotated = false;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private Boolean revoked = false;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
