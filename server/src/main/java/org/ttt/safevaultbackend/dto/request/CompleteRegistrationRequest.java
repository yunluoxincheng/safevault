package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成注册请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteRegistrationRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码验证器不能为空")
    private String passwordVerifier;

    @NotBlank(message = "盐值不能为空")
    private String salt;

    @NotBlank(message = "公钥不能为空")
    private String publicKey;

    @NotBlank(message = "加密私钥不能为空")
    private String encryptedPrivateKey;

    @NotBlank(message = "私钥IV不能为空")
    private String privateKeyIv;

    @NotBlank(message = "私钥认证标签不能为空")
    private String authTag;

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;
}
