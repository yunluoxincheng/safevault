package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求
 *
 * 支持协议版本 2.0 (RSA) 和 3.0 (X25519/Ed25519)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    private String username;

    @NotBlank(message = "显示名称不能为空")
    @Size(min = 1, max = 50, message = "显示名称长度必须在1-50个字符之间")
    private String displayName;

    @NotBlank(message = "RSA 公钥不能为空")
    private String publicKey;

    // X25519/Ed25519 公钥（协议版本 3.0）
    private String x25519PublicKey;

    private String ed25519PublicKey;

    // 密钥版本（"v1" 或 "v2"）
    private String keyVersion;
}
