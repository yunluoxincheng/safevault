package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通过用户名登录请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginByUsernameRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "签名不能为空")
    private String signature;

    private Long timestamp;
}
