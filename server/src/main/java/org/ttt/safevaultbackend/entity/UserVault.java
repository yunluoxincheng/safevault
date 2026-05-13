package org.ttt.safevaultbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户密码库实体
 * 零知识架构：存储加密的密码库，服务器无法解密
 */
@Entity
@Table(name = "user_vaults")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVault {

    @Id
    @Column(name = "vault_id", length = 36)
    private String vaultId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    // 加密的密码库数据（客户端加密，服务器无法解密）
    @Column(name = "encrypted_data", nullable = false, columnDefinition = "TEXT")
    private String encryptedData; // Base64 编码的 AES-256-GCM 加密数据

    @Column(name = "data_iv", nullable = false, length = 24)
    private String dataIv; // Base64 编码的 IV（96 bits）

    @Column(name = "data_auth_tag", length = 32)
    private String dataAuthTag; // Base64 编码的 GCM 认证标签（128 bits）

    /**
     * Salt（Base64 编码）
     * 用于 Argon2id 密钥派生，确保跨设备一致性
     * 客户端上传随机生成的 salt，服务器原样存储和返回
     */
    @Column(name = "salt", nullable = false, length = 32)
    private String salt; // Base64 编码的 salt（128 bits）

    // 版本控制（用于同步和冲突检测）
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

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
