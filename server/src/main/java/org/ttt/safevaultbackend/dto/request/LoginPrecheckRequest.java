package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 零知识架构登录预检查请求
 * 第一步：获取验证器和 Salt
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginPrecheckRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
