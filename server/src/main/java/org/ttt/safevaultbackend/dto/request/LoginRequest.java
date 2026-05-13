package org.ttt.safevaultbackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户登录请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    @NotBlank(message = "签名不能为空")
    private String signature;

    private Long timestamp;
}
