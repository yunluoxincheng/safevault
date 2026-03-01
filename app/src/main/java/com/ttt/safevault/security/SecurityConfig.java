package com.ttt.safevault.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * 安全配置类
 * 管理安全相关的设置
 */
public class SecurityConfig {

    private static final String PREFS_NAME = "security_prefs";
    private static final String PREF_AUTO_LOCK_ENABLED = "auto_lock_enabled";
    private static final String PREF_AUTO_LOCK_TIMEOUT = "auto_lock_timeout";
    private static final String PREF_CLIPBOARD_CLEAR_ENABLED = "clipboard_clear_enabled";
    private static final String PREF_CLIPBOARD_CLEAR_TIMEOUT = "clipboard_clear_timeout";
    private static final String PREF_BIOMETRIC_ENABLED = "biometric_enabled";
    private static final String PREF_SCREENSHOT_PROTECTION = "screenshot_protection";

    // 新增配置项
    private static final String PREF_AUTO_LOCK_MODE = "auto_lock_mode";
    private static final String PREF_PIN_CODE_ENABLED = "pin_code_enabled";
    private static final String PREF_PIN_CODE_HASH = "pin_code_hash";
    private static final String PREF_AUTOFILL_ENABLED = "autofill_enabled";
    private static final String PREF_AUTOFILL_SUGGESTIONS = "autofill_suggestions";
    private static final String PREF_AUTOFILL_COPY_TO_CLIPBOARD = "autofill_copy_to_clipboard";
    private static final String PREF_THEME_MODE = "theme_mode";
    private static final String PREF_DYNAMIC_COLOR = "dynamic_color";

    // 分享功能相关设置
    private static final String PREF_DEFAULT_TRANSMISSION_METHOD = "default_transmission_method";
    private static final String PREF_DEFAULT_SHARE_EXPIRE_TIME = "default_share_expire_time";
    private static final String PREF_DEFAULT_SHARE_SAVEABLE = "default_share_saveable";
    private static final String PREF_SHARE_PASSWORD_LENGTH = "share_password_length";
    private static final String PREF_AUTO_REVOKE_AFTER_VIEW = "auto_revoke_after_view";

    // 默认值
    public static final boolean DEFAULT_AUTO_LOCK_ENABLED = true;
    public static final int DEFAULT_AUTO_LOCK_TIMEOUT = 30; // 秒
    public static final boolean DEFAULT_CLIPBOARD_CLEAR_ENABLED = true;
    public static final int DEFAULT_CLIPBOARD_CLEAR_TIMEOUT = 30; // 秒
    public static final boolean DEFAULT_BIOMETRIC_ENABLED = false;
    public static final boolean DEFAULT_SCREENSHOT_PROTECTION = true;

    // 新增默认值
    public static final String DEFAULT_AUTO_LOCK_MODE = "AFTER_1_MINUTE";
    public static final boolean DEFAULT_PIN_CODE_ENABLED = false;
    public static final boolean DEFAULT_AUTOFILL_ENABLED = true;
    public static final boolean DEFAULT_AUTOFILL_SUGGESTIONS = true;
    public static final boolean DEFAULT_AUTOFILL_COPY_TO_CLIPBOARD = false;
    public static final String DEFAULT_THEME_MODE = "SYSTEM";
    public static final boolean DEFAULT_DYNAMIC_COLOR = true;

    // 分享功能默认值
    public static final String DEFAULT_TRANSMISSION_METHOD = "QR_CODE"; // QR_CODE, BLUETOOTH, NFC, CLOUD
    public static final int DEFAULT_SHARE_EXPIRE_TIME = 60; // 分钟
    public static final boolean DEFAULT_SHARE_SAVEABLE = true;
    public static final int DEFAULT_SHARE_PASSWORD_LENGTH = 8;
    public static final boolean DEFAULT_AUTO_REVOKE_AFTER_VIEW = false;

    /**
     * 会话锁定模式枚举
     */
    public enum AutoLockMode {
        IMMEDIATELY("immediately", "立即锁定"),
        AFTER_1_MINUTE("after_1_minute", "1分钟后"),
        AFTER_5_MINUTES("after_5_minutes", "5分钟后"),
        AFTER_15_MINUTES("after_15_minutes", "15分钟后"),
        AFTER_30_MINUTES("after_30_minutes", "30分钟后"),
        NEVER("never", "从不");

        private final String value;
        private final String displayName;

        AutoLockMode(String value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }

        public String getValue() {
            return value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static AutoLockMode fromValue(String value) {
            for (AutoLockMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            return AFTER_1_MINUTE; // 默认值
        }
    }

    /**
     * 主题模式枚举
     */
    public enum ThemeMode {
        SYSTEM("system", "跟随系统"),
        LIGHT("light", "浅色"),
        DARK("dark", "深色");

        private final String value;
        private final String displayName;

        ThemeMode(String value, String displayName) {
            this.value = value;
            this.displayName = displayName;
        }

        public String getValue() {
            return value;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static ThemeMode fromValue(String value) {
            for (ThemeMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            return SYSTEM; // 默认值
        }
    }

    private final SharedPreferences prefs;

    public SecurityConfig(@NonNull Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 自动锁定设置
     */
    public boolean isAutoLockEnabled() {
        return prefs.getBoolean(PREF_AUTO_LOCK_ENABLED, DEFAULT_AUTO_LOCK_ENABLED);
    }

    public void setAutoLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_LOCK_ENABLED, enabled).apply();
    }

    public int getAutoLockTimeout() {
        return prefs.getInt(PREF_AUTO_LOCK_TIMEOUT, DEFAULT_AUTO_LOCK_TIMEOUT);
    }

    public void setAutoLockTimeout(int timeoutSeconds) {
        prefs.edit().putInt(PREF_AUTO_LOCK_TIMEOUT, timeoutSeconds).apply();
    }

    /**
     * 剪贴板清理设置
     */
    public boolean isClipboardClearEnabled() {
        return prefs.getBoolean(PREF_CLIPBOARD_CLEAR_ENABLED, DEFAULT_CLIPBOARD_CLEAR_ENABLED);
    }

    public void setClipboardClearEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_CLIPBOARD_CLEAR_ENABLED, enabled).apply();
    }

    public int getClipboardClearTimeout() {
        return prefs.getInt(PREF_CLIPBOARD_CLEAR_TIMEOUT, DEFAULT_CLIPBOARD_CLEAR_TIMEOUT);
    }

    public void setClipboardClearTimeout(int timeoutSeconds) {
        prefs.edit().putInt(PREF_CLIPBOARD_CLEAR_TIMEOUT, timeoutSeconds).apply();
    }

    /**
     * 生物识别设置
     */
    public boolean isBiometricEnabled() {
        return prefs.getBoolean(PREF_BIOMETRIC_ENABLED, DEFAULT_BIOMETRIC_ENABLED);
    }

    public void setBiometricEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_BIOMETRIC_ENABLED, enabled).apply();
    }

    /**
     * 截图保护设置
     */
    public boolean isScreenshotProtectionEnabled() {
        return prefs.getBoolean(PREF_SCREENSHOT_PROTECTION, DEFAULT_SCREENSHOT_PROTECTION);
    }

    public void setScreenshotProtectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_SCREENSHOT_PROTECTION, enabled).apply();
    }

    /**
     * 允许截图设置（与截图保护相反）
     * 默认为 false（不允许截图），即启用截图保护
     */
    public boolean isScreenshotAllowed() {
        return !isScreenshotProtectionEnabled();
    }

    public void setScreenshotAllowed(boolean allowed) {
        setScreenshotProtectionEnabled(!allowed);
    }

    /**
     * 重置所有设置为默认值
     */
    public void resetToDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_AUTO_LOCK_ENABLED, DEFAULT_AUTO_LOCK_ENABLED);
        editor.putInt(PREF_AUTO_LOCK_TIMEOUT, DEFAULT_AUTO_LOCK_TIMEOUT);
        editor.putBoolean(PREF_CLIPBOARD_CLEAR_ENABLED, DEFAULT_CLIPBOARD_CLEAR_ENABLED);
        editor.putInt(PREF_CLIPBOARD_CLEAR_TIMEOUT, DEFAULT_CLIPBOARD_CLEAR_TIMEOUT);
        editor.putBoolean(PREF_BIOMETRIC_ENABLED, DEFAULT_BIOMETRIC_ENABLED);
        editor.putBoolean(PREF_SCREENSHOT_PROTECTION, DEFAULT_SCREENSHOT_PROTECTION);
        // 新增配置项默认值
        editor.putString(PREF_AUTO_LOCK_MODE, DEFAULT_AUTO_LOCK_MODE);
        editor.putBoolean(PREF_PIN_CODE_ENABLED, DEFAULT_PIN_CODE_ENABLED);
        editor.putBoolean(PREF_AUTOFILL_ENABLED, DEFAULT_AUTOFILL_ENABLED);
        editor.putBoolean(PREF_AUTOFILL_SUGGESTIONS, DEFAULT_AUTOFILL_SUGGESTIONS);
        editor.putBoolean(PREF_AUTOFILL_COPY_TO_CLIPBOARD, DEFAULT_AUTOFILL_COPY_TO_CLIPBOARD);
        editor.putString(PREF_THEME_MODE, DEFAULT_THEME_MODE);
        editor.putBoolean(PREF_DYNAMIC_COLOR, DEFAULT_DYNAMIC_COLOR);
        // 分享功能默认值
        editor.putString(PREF_DEFAULT_TRANSMISSION_METHOD, DEFAULT_TRANSMISSION_METHOD);
        editor.putInt(PREF_DEFAULT_SHARE_EXPIRE_TIME, DEFAULT_SHARE_EXPIRE_TIME);
        editor.putBoolean(PREF_DEFAULT_SHARE_SAVEABLE, DEFAULT_SHARE_SAVEABLE);
        editor.putInt(PREF_SHARE_PASSWORD_LENGTH, DEFAULT_SHARE_PASSWORD_LENGTH);
        editor.putBoolean(PREF_AUTO_REVOKE_AFTER_VIEW, DEFAULT_AUTO_REVOKE_AFTER_VIEW);
        editor.apply();
    }

    /**
     * 清除所有设置
     */
    public void clear() {
        prefs.edit().clear().apply();
    }

    /**
     * 获取自动锁定的毫秒数
     */
    public long getAutoLockTimeoutMillis() {
        return getAutoLockTimeout() * 1000L;
    }

    /**
     * 获取剪贴板清理的毫秒数
     */
    public long getClipboardClearTimeoutMillis() {
        return getClipboardClearTimeout() * 1000L;
    }

    /**
     * 应用默认设置（首次安装时调用）
     */
    public void applyDefaults() {
        // 只有当设置不存在时才应用默认值
        if (!prefs.contains(PREF_AUTO_LOCK_ENABLED)) {
            resetToDefaults();
        }
    }

    // ========== 新增配置方法 ==========

    /**
     * 自动锁定模式设置
     */
    public AutoLockMode getAutoLockMode() {
        String value = prefs.getString(PREF_AUTO_LOCK_MODE, DEFAULT_AUTO_LOCK_MODE);
        return AutoLockMode.fromValue(value);
    }

    public void setAutoLockMode(AutoLockMode mode) {
        prefs.edit().putString(PREF_AUTO_LOCK_MODE, mode.getValue()).apply();
    }

    /**
     * PIN码设置
     */
    public boolean isPinCodeEnabled() {
        return prefs.getBoolean(PREF_PIN_CODE_ENABLED, DEFAULT_PIN_CODE_ENABLED);
    }

    public void setPinCodeEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_PIN_CODE_ENABLED, enabled).apply();
    }

    /**
     * 设置PIN码
     */
    public boolean setPinCode(String pinCode) {
        if (pinCode == null || pinCode.length() < 4 || pinCode.length() > 20) {
            return false;
        }
        String hash = SecurityUtils.sha256(pinCode);
        prefs.edit()
            .putString(PREF_PIN_CODE_HASH, hash)
            .putBoolean(PREF_PIN_CODE_ENABLED, true)
            .apply();
        return true;
    }

    /**
     * 验证PIN码
     */
    public boolean verifyPinCode(String pinCode) {
        if (pinCode == null || !isPinCodeEnabled()) {
            return false;
        }
        String storedHash = prefs.getString(PREF_PIN_CODE_HASH, null);
        if (storedHash == null) {
            return false;
        }
        return SecurityUtils.sha256(pinCode).equals(storedHash);
    }

    /**
     * 清除PIN码
     */
    public boolean clearPinCode() {
        prefs.edit()
            .remove(PREF_PIN_CODE_HASH)
            .putBoolean(PREF_PIN_CODE_ENABLED, false)
            .apply();
        return true;
    }

    /**
     * 自动填充服务设置
     */
    public boolean isAutofillEnabled() {
        return prefs.getBoolean(PREF_AUTOFILL_ENABLED, DEFAULT_AUTOFILL_ENABLED);
    }

    public void setAutofillEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTOFILL_ENABLED, enabled).apply();
    }

    public boolean isAutofillSuggestionsEnabled() {
        return prefs.getBoolean(PREF_AUTOFILL_SUGGESTIONS, DEFAULT_AUTOFILL_SUGGESTIONS);
    }

    public void setAutofillSuggestionsEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTOFILL_SUGGESTIONS, enabled).apply();
    }

    public boolean isAutofillCopyToClipboard() {
        return prefs.getBoolean(PREF_AUTOFILL_COPY_TO_CLIPBOARD, DEFAULT_AUTOFILL_COPY_TO_CLIPBOARD);
    }

    public void setAutofillCopyToClipboard(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTOFILL_COPY_TO_CLIPBOARD, enabled).apply();
    }

    /**
     * 主题模式设置
     */
    public ThemeMode getThemeMode() {
        String value = prefs.getString(PREF_THEME_MODE, DEFAULT_THEME_MODE);
        return ThemeMode.fromValue(value);
    }

    public void setThemeMode(ThemeMode mode) {
        prefs.edit().putString(PREF_THEME_MODE, mode.getValue()).apply();
    }

    /**
     * 动态颜色设置
     */
    public boolean isDynamicColorEnabled() {
        return prefs.getBoolean(PREF_DYNAMIC_COLOR, DEFAULT_DYNAMIC_COLOR);
    }

    public void setDynamicColorEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_DYNAMIC_COLOR, enabled).apply();
    }

    /**
     * 根据自动锁定模式获取超时时间（毫秒）
     */
    public long getAutoLockTimeoutMillisForMode() {
        AutoLockMode mode = getAutoLockMode();
        switch (mode) {
            case IMMEDIATELY:
                return 0;
            case AFTER_1_MINUTE:
                return 60 * 1000L;
            case AFTER_5_MINUTES:
                return 5 * 60 * 1000L;
            case AFTER_15_MINUTES:
                return 15 * 60 * 1000L;
            case AFTER_30_MINUTES:
                return 30 * 60 * 1000L;
            case NEVER:
                return Long.MAX_VALUE;
            default:
                return 60 * 1000L; // 默认1分钟
        }
    }

    // ========== 分享功能设置方法 ==========

    /**
     * 默认传输方式设置
     */
    public String getDefaultTransmissionMethod() {
        return prefs.getString(PREF_DEFAULT_TRANSMISSION_METHOD, DEFAULT_TRANSMISSION_METHOD);
    }

    public void setDefaultTransmissionMethod(String method) {
        prefs.edit().putString(PREF_DEFAULT_TRANSMISSION_METHOD, method).apply();
    }

    /**
     * 默认分享过期时间设置（分钟）
     */
    public int getDefaultShareExpireTime() {
        return prefs.getInt(PREF_DEFAULT_SHARE_EXPIRE_TIME, DEFAULT_SHARE_EXPIRE_TIME);
    }

    public void setDefaultShareExpireTime(int minutes) {
        prefs.edit().putInt(PREF_DEFAULT_SHARE_EXPIRE_TIME, minutes).apply();
    }

    /**
     * 默认分享是否可保存
     */
    public boolean isDefaultShareSaveable() {
        return prefs.getBoolean(PREF_DEFAULT_SHARE_SAVEABLE, DEFAULT_SHARE_SAVEABLE);
    }

    public void setDefaultShareSaveable(boolean saveable) {
        prefs.edit().putBoolean(PREF_DEFAULT_SHARE_SAVEABLE, saveable).apply();
    }

    /**
     * 分享密码长度设置
     */
    public int getSharePasswordLength() {
        return prefs.getInt(PREF_SHARE_PASSWORD_LENGTH, DEFAULT_SHARE_PASSWORD_LENGTH);
    }

    public void setSharePasswordLength(int length) {
        if (length < 6 || length > 16) {
            throw new IllegalArgumentException("Share password length must be between 6 and 16");
        }
        prefs.edit().putInt(PREF_SHARE_PASSWORD_LENGTH, length).apply();
    }

    /**
     * 查看后自动撤销设置
     */
    public boolean isAutoRevokeAfterView() {
        return prefs.getBoolean(PREF_AUTO_REVOKE_AFTER_VIEW, DEFAULT_AUTO_REVOKE_AFTER_VIEW);
    }

    public void setAutoRevokeAfterView(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_REVOKE_AFTER_VIEW, enabled).apply();
    }
}