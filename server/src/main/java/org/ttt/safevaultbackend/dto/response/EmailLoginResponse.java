package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ttt.safevaultbackend.dto.DeviceInfo;

import java.util.List;

/**
 * 邮箱登录响应
 * 包含设备列表和邮箱信息
 * 安全加固第三阶段：添加maxDevices字段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLoginResponse {

    private String userId;
    private String email;
    private String username;
    private String displayName;
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private Boolean emailVerified;
    private List<DeviceInfo> devices;
    private Boolean isNewDevice;
    private String message; // 新设备提示信息
    private Integer maxDevices; // 最大设备数限制（安全加固第三阶段）
}
