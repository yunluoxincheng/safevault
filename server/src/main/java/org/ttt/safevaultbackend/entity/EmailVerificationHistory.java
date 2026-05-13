package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ttt.safevaultbackend.enums.DeliveryStatus;
import org.ttt.safevaultbackend.enums.VerificationRequestType;

import java.time.LocalDateTime;

/**
 * 邮箱验证历史记录实体
 * 用于追踪验证码/令牌的完整生命周期
 */
@Entity
@Table(name = "email_verification_history", indexes = {
    @Index(name = "idx_email_verification_history_user_id", columnList = "user_id"),
    @Index(name = "idx_email_verification_history_email", columnList = "email"),
    @Index(name = "idx_email_verification_history_code", columnList = "verification_code"),
    @Index(name = "idx_email_verification_history_token", columnList = "verification_token"),
    @Index(name = "idx_email_verification_history_sent_at", columnList = "sent_at"),
    @Index(name = "idx_email_verification_history_expires_at", columnList = "expires_at"),
    @Index(name = "idx_email_verification_history_is_used", columnList = "is_used"),
    @Index(name = "idx_email_verification_history_user_sent", columnList = "user_id,sent_at"),
    @Index(name = "idx_email_verification_history_email_expires", columnList = "email,expires_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户 ID
     * 外键关联 users 表
     */
    @Column(name = "user_id", length = 36)
    private String userId;

    /**
     * 接收验证的邮箱地址
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * 6位数字验证码 (可选)
     */
    @Column(name = "verification_code", length = 10)
    private String verificationCode;

    /**
     * 验证令牌 (可选)
     */
    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    /**
     * 请求类型
     * 区分不同的验证场景
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private VerificationRequestType requestType;

    /**
     * 请求来源 IP 地址
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * 设备指纹
     * 用于识别设备，防止滥用
     */
    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    /**
     * 验证发送时间
     */
    @Column(name = "sent_at", nullable = false, updatable = false)
    private LocalDateTime sentAt;

    /**
     * 验证完成时间
     * 当用户完成验证时设置
     */
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    /**
     * 验证码是否已使用
     */
    @Column(name = "is_used", nullable = false)
    @Builder.Default
    private Boolean isUsed = false;

    /**
     * 验证过期时间
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 邮件投递状态
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", length = 50)
    private DeliveryStatus deliveryStatus;

    /**
     * 邮件服务提供商
     * 如：aliyun, sendgrid等
     */
    @Column(name = "provider", length = 100)
    private String provider;

    /**
     * 持久化前自动设置时间戳和默认值
     */
    @PrePersist
    protected void onCreate() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
        if (isUsed == null) {
            isUsed = false;
        }
    }
}
