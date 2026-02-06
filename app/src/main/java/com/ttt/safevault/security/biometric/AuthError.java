package com.ttt.safevault.security.biometric;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 生物识别认证错误枚举
 *
 * 定义生物识别认证过程中可能出现的错误类型。
 * 错误分为不同类别，UI 层可以根据类别选择不同的展示方式。
 *
 * @since SafeVault 3.3.0 (生物识别架构重构)
 */
public enum AuthError {
    /**
     * 防抖动错误
     * 用户短时间内重复触发认证，应该稍后重试
     * 类别：临时错误（轻量提示）
     */
    DEBOUNCED("debounced", "认证处理中，请稍候", ErrorCategory.TEMPORARY, true),

    /**
     * 硬件不支持
     * 设备没有生物识别硬件
     * 类别：永久错误（禁用生物识别）
     */
    HARDWARE_UNAVAILABLE("hardware_unavailable", "设备不支持生物识别", ErrorCategory.PERMANENT, false),

    /**
     * 未注册生物识别信息
     * 用户未在系统设置中注册指纹或面部
     * 类别：可配置错误（引导用户设置）
     */
    NOT_ENROLLED("not_enrolled", "未设置生物识别信息，请在系统设置中添加", ErrorCategory.CONFIGURABLE, false),

    /**
     * 生物识别已变更
     * 用户添加了新指纹或面部，导致密钥失效
     * 类别：严重错误（需要重新启用生物识别）
     */
    BIOMETRIC_CHANGED("biometric_changed", "生物识别信息已变更，请重新启用", ErrorCategory.SEVERE, false),

    /**
     * KeyStore 认证已过期
     * DeviceKey 的 30 秒认证窗口已过
     * 类别：临时错误（可重新认证）
     */
    KEYSTORE_AUTH_EXPIRED("keystore_auth_expired", "认证已过期，请重新验证", ErrorCategory.TEMPORARY, true),

    /**
     * KeyStore 密钥永久失效
     * 密钥被标记为永久无效，通常是由于生物识别变更
     * 类别：严重错误（需要重新启用生物识别）
     */
    KEYSTORE_INVALIDATED("keystore_invalidated", "密钥已失效，请重新启用生物识别", ErrorCategory.SEVERE, false),

    /**
     * 业务级锁定
     * 连续失败次数过多，账户被临时锁定
     * 类别：临时错误（等待解锁）
     */
    LOCKED_OUT("locked_out", "生物识别已被锁定，请稍后再试或使用主密码", ErrorCategory.TEMPORARY, true),

    /**
     * 用户取消
     * 用户主动取消认证（点击取消按钮）
     * 类别：用户操作（静默处理）
     */
    CANCELLED("cancelled", "用户取消", ErrorCategory.USER_ACTION, true),

    /**
     * 认证失败
     * 生物识别认证失败（指纹不匹配等）
     * 类别：临时错误（可重试）
     */
    AUTH_FAILED("auth_failed", "生物识别认证失败，请重试", ErrorCategory.TEMPORARY, true),

    /**
     * 超时
     * 生物识别认证超时
     * 类别：临时错误（可重试）
     */
    TIMEOUT("timeout", "认证超时，请重试", ErrorCategory.TEMPORARY, true),

    /**
     * 未知错误
     * 未知的错误类型
     * 类别：临时错误（可重试）
     */
    UNKNOWN("unknown", "未知错误", ErrorCategory.TEMPORARY, true);

    private final String code;
    private final String message;
    private final ErrorCategory category;
    private final boolean canRetry;

    AuthError(@NonNull String code, @NonNull String message,
              @NonNull ErrorCategory category, boolean canRetry) {
        this.code = code;
        this.message = message;
        this.category = category;
        this.canRetry = canRetry;
    }

    /**
     * 获取错误代码
     *
     * @return 错误代码
     */
    @NonNull
    public String getCode() {
        return code;
    }

    /**
     * 获取错误消息
     *
     * @return 错误消息
     */
    @NonNull
    public String getMessage() {
        return message;
    }

    /**
     * 获取错误类别
     *
     * @return 错误类别
     */
    @NonNull
    public ErrorCategory getCategory() {
        return category;
    }

    /**
     * 是否可以重试
     *
     * @return true 表示可以重试，false 表示不能重试
     */
    public boolean canRetry() {
        return canRetry;
    }

    /**
     * 从代码解析错误
     *
     * @param code 错误代码
     * @return AuthError 实例，未找到返回 UNKNOWN
     */
    @NonNull
    public static AuthError fromCode(@NonNull String code) {
        for (AuthError error : values()) {
            if (error.code.equals(code)) {
                return error;
            }
        }
        return UNKNOWN;
    }

    /**
     * 从 BiometricPrompt 错误码映射到 AuthError
     *
     * 注意：ERROR_NONE_ENROLLED 是 BiometricManager 的常量，不是 BiometricPrompt 的。
     * BiometricPrompt 的错误码通常通过 BiometricPrompt.AuthenticationCallback 的 onAuthenticationError 传递。
     *
     * @param errorCode BiometricPrompt 错误码
     * @return AuthError 实例
     */
    @NonNull
    public static AuthError fromBiometricPromptError(int errorCode) {
        switch (errorCode) {
            case androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE:
            case androidx.biometric.BiometricPrompt.ERROR_NO_SPACE:
                return HARDWARE_UNAVAILABLE;
            // BiometricPrompt 没有 ERROR_NONE_ENROLLED，这通常是 BiometricManager 的检查结果
            // case 11: // ERROR_NONE_ENROLLED 在 BiometricManager 中
            //     return NOT_ENROLLED;
            case androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON:
                return CANCELLED;
            case androidx.biometric.BiometricPrompt.ERROR_LOCKOUT:
            case androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT:
                return LOCKED_OUT;
            case androidx.biometric.BiometricPrompt.ERROR_TIMEOUT:
                return TIMEOUT;
            case androidx.biometric.BiometricPrompt.ERROR_CANCELED:
                return CANCELLED;
            case androidx.biometric.BiometricPrompt.ERROR_VENDOR:
            case androidx.biometric.BiometricPrompt.ERROR_UNABLE_TO_PROCESS:
            default:
                return UNKNOWN;
        }
    }

    /**
     * 错误类别枚举
     */
    public enum ErrorCategory {
        /**
         * 临时错误
         * 可以通过重试或等待解决，使用 Toast 轻量提示
         */
        TEMPORARY,

        /**
         * 永久错误
         * 硬件或系统限制，无法通过重试解决，需要禁用生物识别
         */
        PERMANENT,

        /**
         * 可配置错误
         * 用户可以通过系统设置解决，引导用户配置
         */
        CONFIGURABLE,

        /**
         * 严重错误
         * 需要重新启用生物识别功能
         */
        SEVERE,

        /**
         * 用户操作
         * 用户主动取消，静默处理
         */
        USER_ACTION
    }
}
