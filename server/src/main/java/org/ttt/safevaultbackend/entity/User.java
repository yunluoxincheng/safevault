package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户实体
 */
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    // 设备 ID（保留用于兼容，但改为可选）
    @Column(name = "device_id", unique = true, length = 255)
    private String deviceId;

    // 用户名（保留用于显示和搜索）
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    // 邮箱
    @Column(unique = true, length = 255)
    private String email;

    // 邮箱验证相关字段
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    // 注册状态追踪字段
    @Column(name = "registration_status", length = 50, nullable = false)
    @Builder.Default
    private String registrationStatus = "ACTIVE"; // EMAIL_VERIFIED, ACTIVE

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "registration_completed_at")
    private LocalDateTime registrationCompletedAt;

    @Column(name = "verification_token", length = 255)
    private String verificationToken;

    @Column(name = "verification_expires_at")
    private LocalDateTime verificationExpiresAt;

    // 验证邮件发送频率限制
    @Column(name = "last_verification_email_sent_at")
    private LocalDateTime lastVerificationEmailSentAt;

    // 多设备支持
    @Column(name = "devices", columnDefinition = "TEXT")
    private String devices; // JSON array of registered devices

    // 最大设备数限制（安全加固第三阶段）
    @Column(name = "max_devices", nullable = false)
    @Builder.Default
    private Integer maxDevices = 5; // 默认最多5台设备同时登录

    // 分享用密钥对
    @Column(name = "public_key", columnDefinition = "TEXT")
    private String publicKey; // RSA 公钥（协议版本 2.0）

    @Column(name = "private_key_encrypted", columnDefinition = "TEXT")
    private String privateKeyEncrypted; // 加密的 RSA 私钥

    @Column(name = "private_key_iv", length = 24)
    private String privateKeyIv; // 私钥加密的 IV

    // X25519/Ed25519 椭圆曲线密钥对（协议版本 3.0）
    @Column(name = "x25519_public_key", columnDefinition = "TEXT")
    private String x25519PublicKey; // X25519 公钥（用于 ECDH 密钥交换）

    @Column(name = "ed25519_public_key", columnDefinition = "TEXT")
    private String ed25519PublicKey; // Ed25519 公钥（用于数字签名）

    // 公钥版本标识
    @Column(name = "key_version", length = 10)
    private String keyVersion; // v1=RSA, v2=X25519/Ed25519

    // 公钥更新时间
    @Column(name = "public_keys_updated_at")
    private LocalDateTime publicKeysUpdatedAt;

    // 密码验证相关字段（用于邮箱账户）
    @Column(name = "password_verifier", columnDefinition = "TEXT")
    private String passwordVerifier;

    @Column(name = "password_salt", length = 64)
    private String passwordSalt;

    // 密码哈希算法标识（安全加固第二阶段）
    // 开发环境：统一使用 Argon2id
    @Column(name = "password_hash_algorithm", length = 20, nullable = false)
    @Builder.Default
    private String passwordHashAlgorithm = "ARGON2ID";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "fromUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContactShare> createdShares = new ArrayList<>();

    @OneToMany(mappedBy = "toUser", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ContactShare> receivedShares = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
