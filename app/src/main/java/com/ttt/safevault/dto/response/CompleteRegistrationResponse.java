package com.ttt.safevault.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 完成注册响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteRegistrationResponse {

    private Boolean success;
    private String message;
    private String userId;
    private String accessToken;
    private String refreshToken;
    private String displayName;
}
