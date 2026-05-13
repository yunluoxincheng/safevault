package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 联系人密码分享实体
 * 仅支持好友间的密码分享
 */
@Entity
@Table(name = "contact_shares")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactShare {

    @Id
    @Column(name = "share_id", length = 36)
    private String shareId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false, referencedColumnName = "user_id")
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false, referencedColumnName = "user_id")
    private User toUser;

    @Column(name = "password_id", nullable = false)
    private String passwordId;

    @Column(name = "encrypted_data", nullable = false, columnDefinition = "TEXT")
    private String encryptedData;

    /**
     * RSA加密版本
     * v1 = PKCS1Padding（不安全，仅向后兼容）
     * v2 = OAEPWithSHA-256AndMGF1Padding（安全）
     */
    @Column(name = "encryption_version", nullable = false, length = 10)
    @Builder.Default
    private String encryptionVersion = "v1";

    @Column(name = "can_view", nullable = false)
    @Builder.Default
    private boolean canView = true;

    @Column(name = "can_save", nullable = false)
    @Builder.Default
    private boolean canSave = true;

    @Column(name = "is_revocable", nullable = false)
    @Builder.Default
    private boolean isRevocable = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ContactShareStatus status = ContactShareStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
