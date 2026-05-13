package org.ttt.safevaultbackend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 邮箱验证状态响应
 * 用于前端轮询检查验证是否完成
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationStatusResponse {

    /**
     * 验证状态
     * - PENDING: 待验证（验证邮件已发送，等待用户点击链接）
     * - VERIFIED: 已验证（用户已在 Web 页面完成验证）
     * - NOT_FOUND: 无待验证记录（邮箱未注册或已过期）
     */
    private String status;

    /**
     * 邮箱地址
     */
    private String email;

    /**
     * 用户名
     */
    private String username;

    /**
     * 令牌过期时间（仅 PENDING 状态有值）
     */
    private String tokenExpiresAt;

    /**
     * 是否已验证
     */
    public boolean isVerified() {
        return "VERIFIED".equals(status);
    }

    /**
     * 是否待验证
     */
    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
