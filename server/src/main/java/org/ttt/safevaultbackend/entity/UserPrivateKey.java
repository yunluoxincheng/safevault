package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户加密私钥实体
 * 用于云端存储用户加密的私钥，支持版本控制
 */
@Entity
@Table(name = "user_private_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivateKey {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    private String encryptedPrivateKey;

    @Column(name = "iv", nullable = false, length = 255)
    private String iv;

    @Column(name = "salt", nullable = false, length = 255)
    private String salt;

    @Column(name = "auth_tag", nullable = false, length = 32)
    @Builder.Default
    private String authTag = "";

    @Column(name = "version", length = 50)
    @Builder.Default
    private String version = "v1";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

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
