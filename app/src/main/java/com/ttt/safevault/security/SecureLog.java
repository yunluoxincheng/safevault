package com.ttt.safevault.security;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 安全日志工具类（内存安全强化）
 *
 * 提供安全的日志输出方法，防止敏感信息泄露到日志：
 * - Token 仅记录前缀（前 10 字符）
 * - 密钥仅记录算法和长度
 * - 密码不记录任何内容
 * - 敏感数据使用占位符替代
 *
 * 使用示例：
 * <pre>
 * SecureLog.d(TAG, "Token: " + SecureLog.maskToken(token));
 * SecureLog.d(TAG, "Key: " + SecureLog.maskSecretKey(key));
 * </pre>
 *
 * @since SafeVault 3.5.0 (内存安全强化)
 */
public final class SecureLog {
    /** Token 前缀长度 */
    private static final int TOKEN_PREFIX_LENGTH = 10;
    /** 占位符 */
    private static final String PLACEHOLDER = "***";

    /** 私有构造函数（工具类） */
    private SecureLog() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 安全的 debug 日志
     *
     * @param tag 日志标签
     * @param msg 日志消息（不应包含敏感信息）
     */
    public static void d(@NonNull String tag, @NonNull String msg) {
        Log.d(tag, msg);
    }

    /**
     * 安全的 info 日志
     *
     * @param tag 日志标签
     * @param msg 日志消息（不应包含敏感信息）
     */
    public static void i(@NonNull String tag, @NonNull String msg) {
        Log.i(tag, msg);
    }

    /**
     * 安全的 warning 日志
     *
     * @param tag 日志标签
     * @param msg 日志消息（不应包含敏感信息）
     */
    public static void w(@NonNull String tag, @NonNull String msg) {
        Log.w(tag, msg);
    }

    /**
     * 安全的 error 日志
     *
     * @param tag 日志标签
     * @param msg 日志消息（不应包含敏感信息）
     */
    public static void e(@NonNull String tag, @NonNull String msg) {
        Log.e(tag, msg);
    }

    /**
     * 安全的 error 日志（带异常）
     *
     * @param tag 日志标签
     * @param msg 日志消息（不应包含敏感信息）
     * @param tr 异常对象
     */
    public static void e(@NonNull String tag, @NonNull String msg, @Nullable Throwable tr) {
        Log.e(tag, msg, tr);
    }

    // ========== 敏感数据脱敏方法 ==========

    /**
     * 脱敏 Token（仅显示前缀）
     *
     * @param token 原始 Token
     * @return 脱敏后的 Token（格式：前10字符...）
     */
    @NonNull
    public static String maskToken(@Nullable String token) {
        if (token == null || token.isEmpty()) {
            return PLACEHOLDER;
        }

        if (token.length() <= TOKEN_PREFIX_LENGTH) {
            return PLACEHOLDER;
        }

        String prefix = token.substring(0, TOKEN_PREFIX_LENGTH);
        return prefix + "...";
    }

    /**
     * 脱敏密钥（仅显示算法和长度）
     *
     * @param key 密钥对象
     * @return 脱敏后的密钥信息
     */
    @NonNull
    public static String maskSecretKey(@Nullable javax.crypto.SecretKey key) {
        if (key == null) {
            return PLACEHOLDER;
        }

        return key.getAlgorithm() + "[" + getKeyLength(key) + " bits]";
    }

    /**
     * 脱敏字符数组密码（不记录任何内容）
     *
     * @param password 密码字符数组
     * @return 占位符
     */
    @NonNull
    public static String maskPassword(@Nullable char[] password) {
        return PLACEHOLDER;
    }

    /**
     * 脱敏字节数组（仅显示长度）
     *
     * @param data 字节数组
     * @return 脱敏后的数据信息
     */
    @NonNull
    public static String maskByteArray(@Nullable byte[] data) {
        if (data == null) {
            return "null";
        }

        return "byte[" + data.length + "]";
    }

    /**
     * 脱敏敏感数据（仅显示类型和长度）
     *
     * @param sensitiveData SensitiveData 对象
     * @return 脱敏后的数据信息
     */
    @NonNull
    public static String maskSensitiveData(@Nullable SensitiveData<?> sensitiveData) {
        if (sensitiveData == null) {
            return "null";
        }

        return "SensitiveData{length=" + sensitiveData.length() + ", closed=" + sensitiveData.isClosed() + "}";
    }

    /**
     * 脱敏字符串（仅显示前缀）
     *
     * @param str 原始字符串
     * @return 脱敏后的字符串
     */
    @NonNull
    public static String maskString(@Nullable String str) {
        if (str == null || str.isEmpty()) {
            return PLACEHOLDER;
        }

        int visibleLength = Math.min(TOKEN_PREFIX_LENGTH, str.length());
        String prefix = str.substring(0, visibleLength);
        return prefix + "...";
    }

    /**
     * 获取密钥长度（位）
     *
     * @param key 密钥对象
     * @return 密钥长度（位），无法获取返回 0
     */
    private static int getKeyLength(@NonNull javax.crypto.SecretKey key) {
        try {
            byte[] encoded = key.getEncoded();
            if (encoded == null) {
                return 0;
            }
            return encoded.length * 8;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 验证字符串是否为安全的日志内容
     *
     * 检查字符串是否可能包含敏感信息：
     * - Token 模式（Bearer xxx, eyJxxx）
     * - 密钥模式（多个连续的 base64 字符）
     *
     * @param msg 待验证的字符串
     * @return true 表示安全，false 表示可能包含敏感信息
     */
    public static boolean isSafeLogMessage(@Nullable String msg) {
        if (msg == null || msg.isEmpty()) {
            return true;
        }

        // 检查 JWT Token 模式（eyJ 开头）
        if (msg.contains("eyJ") && msg.length() > 50) {
            return false;
        }

        // 检查 Bearer Token 模式
        if (msg.toLowerCase().contains("bearer ") && msg.length() > 20) {
            return false;
        }

        // 检查可能的密钥模式（长 base64 字符串）
        if (msg.matches(".*[A-Za-z0-9+/]{40,}.*")) {
            return false;
        }

        return true;
    }

    /**
     * 安全的日志输出（自动检查敏感信息）
     *
     * 如果检测到可能包含敏感信息，自动脱敏
     *
     * @param tag 日志标签
     * @param msg 日志消息
     */
    public static void safeDebug(@NonNull String tag, @NonNull String msg) {
        if (!isSafeLogMessage(msg)) {
            msg = "[POTENTIALLY SENSITIVE] " + maskString(msg);
        }
        Log.d(tag, msg);
    }

    /**
     * 安全的日志输出（自动检查敏感信息）
     *
     * 如果检测到可能包含敏感信息，自动脱敏
     *
     * @param tag 日志标签
     * @param msg 日志消息
     */
    public static void safeInfo(@NonNull String tag, @NonNull String msg) {
        if (!isSafeLogMessage(msg)) {
            msg = "[POTENTIALLY SENSITIVE] " + maskString(msg);
        }
        Log.i(tag, msg);
    }
}
