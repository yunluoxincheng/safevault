package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码库初始化请求
 * 用于新用户创建初始密码库
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaultInitRequest {

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
     * 用于 Argon2id 密钥派生
     */
    @NotBlank(message = "Salt 不能为空")
    private String salt;
}
