package com.ttt.safevault.security.biometric;

import androidx.annotation.NonNull;

/**
 * 生物识别认证场景枚举
 *
 * 定义生物识别认证的使用场景，不同场景可能有不同的行为策略。
 *
 * @since SafeVault 3.3.0 (生物识别架构重构)
 */
public enum AuthScenario {
    /**
     * 登录认证
     * 用户首次登录应用时的生物识别认证
     */
    LOGIN("login", "登录认证"),

    /**
     * 快速解锁
     * 应用从后台切换回前台时的快速生物识别解锁
     */
    QUICK_UNLOCK("quick_unlock", "快速解锁"),

    /**
     * 启用生物识别
     * 用户首次启用生物识别功能时的验证认证
     */
    ENROLLMENT("enrollment", "启用生物识别"),

    /**
     * 确认操作
     * 敏感操作确认时的生物识别认证
     */
    CONFIRMATION("confirmation", "确认操作"),

    /**
     * 密码查看
     * 查看密码详情时的生物识别认证
     */
    PASSWORD_VIEW("password_view", "密码查看");

    private final String code;
    private final String displayName;

    AuthScenario(@NonNull String code, @NonNull String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 获取场景代码
     *
     * @return 场景代码
     */
    @NonNull
    public String getCode() {
        return code;
    }

    /**
     * 获取场景显示名称
     *
     * @return 显示名称
     */
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 从代码解析场景
     *
     * @param code 场景代码
     * @return AuthScenario 实例，未找到返回 QUICK_UNLOCK
     */
    @NonNull
    public static AuthScenario fromCode(@NonNull String code) {
        for (AuthScenario scenario : values()) {
            if (scenario.code.equals(code)) {
                return scenario;
            }
        }
        return QUICK_UNLOCK;
    }
}
