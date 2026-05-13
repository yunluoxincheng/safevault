package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 零知识架构用户注册请求
 * 客户端发送主密码验证器，而非主密码本身
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZeroKnowledgeRegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;

    @NotBlank(message = "显示名称不能为空")
    @Size(min = 1, max = 50, message = "显示名称长度必须在1-50个字符之间")
    private String displayName;

    // 零知识认证相关字段
    @NotBlank(message = "密码验证器不能为空")
    private String passwordVerifier; // PBKDF2 派生的验证器（Base64）

    @NotBlank(message = "密码盐不能为空")
    private String passwordSalt; // Salt（Base64）

    @Min(value = 10000, message = "密码迭代次数不能少于10000")
    private Integer passwordIterations; // PBKDF2 迭代次数（默认100000）

    // 分享用 RSA 密钥对
    @NotBlank(message = "公钥不能为空")
    private String publicKey; // RSA 公钥（Base64）

    @NotBlank(message = "加密的私钥不能为空")
    private String encryptedPrivateKey; // 使用主密钥加密的私钥（Base64）

    @NotBlank(message = "私钥IV不能为空")
    private String privateKeyIv; // 私钥加密的 IV（Base64）

    private String keyHash; // 主密钥哈希，用于快速验证（可选）

    // 可选：生物识别用加密主密钥
    private String encryptedMasterKey; // 加密的主密钥（Base64，可选）

    private String masterKeyIv; // 主密钥加密的 IV（Base64，可选）
}
