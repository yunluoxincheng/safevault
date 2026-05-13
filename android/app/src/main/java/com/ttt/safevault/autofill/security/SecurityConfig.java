package com.ttt.safevault.autofill.security;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 自动填充安全配置
 * 定义阻止列表和安全策略
 */
public class SecurityConfig {
    
    /**
     * 阻止列表：不提供自动填充服务的应用包名
     * 包括密码管理器、银行应用、敏感应用等
     */
    private static final Set<String> BLOCKED_PACKAGES = new HashSet<>(Arrays.asList(
            // 其他密码管理器
            "com.lastpass.lpandroid",
            "com.agilebits.onepassword",
            "com.dashlane",
            "com.keeper.keepersecurityapp",
            "com.x8bit.bitwarden",
            "com.enpass.passwordmanager",
            
            // 系统敏感应用
            "com.android.settings",
            "com.android.systemui",
            
            // 开发工具（可选）
            "com.android.development"
    ));

    /**
     * 检查应用是否在阻止列表中
     *
     * @param packageName 应用包名
     * @return true表示应阻止，false表示允许
     */
    public boolean isBlocked(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }

        return BLOCKED_PACKAGES.contains(packageName);
    }

    /**
     * 脱敏用户名（用于日志）
     *
     * @param username 原始用户名
     * @return 脱敏后的用户名
     */
    public static String maskUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "[empty]";
        }

        if (username.length() <= 3) {
            return "***";
        }

        // 只显示第一个和最后一个字符
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    /**
     * 脱敏密码（用于日志）
     *
     * @param password 原始密码
     * @return 脱敏后的密码
     */
    public static String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "[empty]";
        }

        return "***" + password.length() + "chars***";
    }

    /**
     * 脱敏域名（用于日志）
     *
     * @param domain 原始域名
     * @return 脱敏后的域名（保留用于调试）
     */
    public static String maskDomain(String domain) {
        // 域名可以保留用于调试，不做脱敏
        return domain;
    }
}
