package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户配置响应
 * 包含用户的公钥信息（RSA 和 X25519/Ed25519）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String userId;
    private String username;
    private String displayName;

    // RSA 公钥（协议版本 2.0）
    private String publicKey;

    // X25519/Ed25519 公钥（协议版本 3.0）
    private String x25519PublicKey;
    private String ed25519PublicKey;
    private String keyVersion;

    private Long createdAt;
    private Integer shareCount;
}
