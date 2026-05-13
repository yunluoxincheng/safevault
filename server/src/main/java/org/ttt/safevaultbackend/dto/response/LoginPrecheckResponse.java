package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录预检查响应（Challenge-Response 机制）
 * 返回服务器生成的挑战码（nonce）供客户端签名
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginPrecheckResponse {

    /**
     * 服务器生成的随机挑战码（nonce）
     * 客户端需要用派生密钥对 nonce 进行签名
     */
    private String nonce;

    /**
     * Nonce 过期时间（Unix 时间戳，秒）
     * 默认 30 秒有效期
     */
    private Long expiresAt;

    /**
     * 用户ID（用于前端显示）
     */
    private String userId;

    /**
     * 用户派生密钥验证器（Base64 编码）
     * 这是用户注册时使用 Argon2id 从主密码派生的密钥
     * 客户端使用此密钥对 nonce 进行 HMAC-SHA256 签名
     *
     * 安全说明：虽然派生密钥可以通过主密码重新计算，
     * 但直接返回存储的 passwordVerifier 可以避免客户端
     * 需要存储和管理盐值，简化协议并确保一致性。
     */
    private String passwordVerifier;
}
