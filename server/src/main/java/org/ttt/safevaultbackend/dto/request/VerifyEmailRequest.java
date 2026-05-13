package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱验证请求
 * 用户点击验证链接后调用此接口完成验证
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEmailRequest {

    @NotBlank(message = "验证令牌不能为空")
    @Size(min = 43, max = 43, message = "验证令牌格式不正确")
    private String token;
}
