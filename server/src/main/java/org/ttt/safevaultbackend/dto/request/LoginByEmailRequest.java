package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱登录请求（Challenge-Response 机制）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginByEmailRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "派生密钥签名不能为空")
    private String derivedKeySignature;

    /**
     * 服务器生成的挑战码（nonce）
     * 客户端使用派生密钥对 nonce 进行签名
     */
    @NotBlank(message = "挑战码不能为空")
    private String nonce;

    private String deviceType; // 手机、平板、电脑等

    private String osVersion; // 操作系统版本
}
