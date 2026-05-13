package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ttt.safevaultbackend.enums.VerificationEventType;

import java.time.LocalDateTime;

/**
 * 邮箱验证事件实体
 * 用于记录所有验证相关的操作事件，支持审计和问题排查
 */
@Entity
@Table(name = "verification_events", indexes = {
    @Index(name = "idx_verification_events_user_id", columnList = "user_id"),
    @Index(name = "idx_verification_events_email", columnList = "email"),
    @Index(name = "idx_verification_events_type", columnList = "event_type"),
    @Index(name = "idx_verification_events_timestamp", columnList = "event_timestamp"),
    @Index(name = "idx_verification_events_token", columnList = "verification_token"),
    @Index(name = "idx_verification_events_success", columnList = "success"),
    @Index(name = "idx_verification_events_user_type", columnList = "user_id,event_type"),
    @Index(name = "idx_verification_events_user_timestamp", columnList = "user_id,event_timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationEvent {

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
     * 用户邮箱
     */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    /**
     * 事件类型
     * 使用枚举确保类型安全
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private VerificationEventType eventType;

    /**
     * 验证令牌 (可选)
     * 用于关联特定的验证流程
     */
    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    /**
     * 客户端 IP 地址
     * 支持IPv4和IPv6
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * 客户端 User-Agent
     * 存储为TEXT类型以支持长字符串
     */
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    /**
     * 操作是否成功
     */
    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = false;

    /**
     * 失败原因
     * 当 success=false 时记录
     */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /**
     * 事件时间戳
     * 记录事件发生的准确时间
     */
    @Column(name = "event_timestamp", nullable = false, updatable = false)
    private LocalDateTime eventTimestamp;

    /**
     * 额外元数据
     * JSON格式存储扩展信息
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * 持久化前自动设置时间戳
     */
    @PrePersist
    protected void onCreate() {
        if (eventTimestamp == null) {
            eventTimestamp = LocalDateTime.now();
        }
        if (success == null) {
            success = false;
        }
    }
}
