package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户密钥信息响应
 *
 * 用于版本协商，返回用户的所有公钥信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserKeyInfoResponse {

    private String userId;

    // RSA 公钥（协议版本 2.0）
    private String rsaPublicKey;

    // X25519 公钥（协议版本 3.0）
    private String x25519PublicKey;

    // Ed25519 公钥（协议版本 3.0）
    private String ed25519PublicKey;

    // 当前活跃的密钥版本（"v1" 或 "v2"）
    private String keyVersion;
}