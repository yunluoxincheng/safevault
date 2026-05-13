package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码库同步请求
 * 零知识架构：客户端发送加密后的密码库数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultSyncRequest {

    /**
     * 加密的密码库数据（Base64 编码）
     */
    @NotBlank(message = "加密数据不能为空")
    private String encryptedData;

    /**
     * IV（初始化向量，Base64 编码）
     */
    @NotBlank(message = "IV 不能为空")
    private String dataIv;

    /**
     * GCM 认证标签（Base64 编码）
     */
    @NotBlank(message = "认证标签不能为空")
    private String dataAuthTag;

    /**
     * Salt（Base64 编码）
     * 用于 Argon2id 密钥派生，客户端生成的随机 salt
     */
    @NotBlank(message = "Salt 不能为空")
    private String salt;

    /**
     * 客户端版本号（用于冲突检测）
     */
    @NotNull(message = "版本号不能为空")
    private Long clientVersion;

    /**
     * 强制同步标志（为 true 时覆盖服务器数据）
     */
    private boolean forceSync;
}
