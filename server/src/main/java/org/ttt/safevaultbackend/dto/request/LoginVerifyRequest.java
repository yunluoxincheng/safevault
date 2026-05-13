package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 零知识架构登录验证请求
 * 第二步：客户端验证密码并请求令牌
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVerifyRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "客户端验证器不能为空")
    private String clientVerifier; // 客户端计算的验证器（用于服务端验证）

    // 可选：客户端派生的密钥哈希（用于快速验证）
    private String clientKeyHash;
}
