package com.ttt.safevault.dto.request;

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

    private String email;
    private String username;
    private String passwordVerifier;
    private String salt;
    private String publicKey;
    private String encryptedPrivateKey;
    private String privateKeyIv;
    private String deviceId;
}
