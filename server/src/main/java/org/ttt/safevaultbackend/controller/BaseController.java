package org.ttt.safevaultbackend.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Controller 基类
 * 安全加固：提供从 SecurityContext 获取当前用户 ID 的辅助方法（2.5）
 *
 * <p>所有需要获取当前用户信息的 Controller 应继承此类，使用 getCurrentUserId()
 * 方法而不是从请求头读取 X-User-Id，防止越权访问攻击。</p>
 */
public abstract class BaseController {

    /**
     * 从 Spring Security Context 获取当前登录用户的 ID
     *
     * <p>用户 ID 由 JwtAuthenticationFilter 在验证 JWT Token 后设置到
     * SecurityContext 中。此方法确保用户 ID 来自可信的 Token 而非可伪造的
     * 请求头。</p>
     *
     * @return 当前用户的 ID
     * @throws IllegalStateException 如果用户未认证
     */
    protected String getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("用户未认证");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof String) {
            return (String) principal;
        }

        throw new IllegalStateException("无法从认证上下文获取用户 ID");
    }
}
