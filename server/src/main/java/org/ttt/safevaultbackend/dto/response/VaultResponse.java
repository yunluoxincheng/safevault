package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 密码库响应
 * 零知识架构：返回加密的密码库数据，由客户端解密
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultResponse {

    /**
     * 密码库 ID
     */
    private String vaultId;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 加密的密码库数据（Base64 编码）
     */
    private String encryptedData;

    /**
     * IV（初始化向量，Base64 编码）
     */
    private String dataIv;

    /**
     * GCM 认证标签（Base64 编码）
     */
    private String dataAuthTag;

    /**
     * Salt（Base64 编码）
     * 用于 Argon2id 密钥派生
     */
    private String salt;

    /**
     * 版本号
     */
    private Long version;

    /**
     * 最后同步时间
     */
    private LocalDateTime lastSyncedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
