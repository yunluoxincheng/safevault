package com.ttt.safevault.security;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 安全工具类
 * 提供各种安全相关的静态方法
 */
public final class SecurityUtils {

    private static final String TAG = "SecurityUtils";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 密码强度检查的正则表达式
    private static final Pattern[] PASSWORD_PATTERNS = {
            Pattern.compile(".*[A-Z].*"), // 至少一个大写字母
            Pattern.compile(".*[a-z].*"), // 至少一个小写字母
            Pattern.compile(".*\\d.*"),    // 至少一个数字
            Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*") // 至少一个特殊字符
    };

    private SecurityUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 生成随机盐值
     */
    @NonNull
    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        SECURE_RANDOM.nextBytes(salt);
        return salt;
    }

    /**
     * 生成随机IV（初始化向量）
     */
    @NonNull
    public static byte[] generateIV() {
        byte[] iv = new byte[16];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * 使用SHA-256哈希密码
     */
    @NonNull
    public static String hashPassword(@NonNull String password, @NonNull byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            digest.update(salt);
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));

            return bytesToHex(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to hash password", e);
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    /**
     * 使用SHA-256哈希数据
     */
    @NonNull
    public static String sha256(@NonNull String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to compute SHA-256", e);
            return "";
        }
    }

    /**
     * 使用SHA-512哈希数据
     */
    @NonNull
    public static String sha512(@NonNull String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-512");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to compute SHA-512", e);
            return "";
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    @NonNull
    public static String bytesToHex(@NonNull byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    /**
     * 十六进制字符串转字节数组
     */
    @NonNull
    public static byte[] hexToBytes(@NonNull String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 检查密码强度
     * @param password 密码
     * @return 强度等级：0-弱，1-中，2-强
     */
    public static int checkPasswordStrength(@NonNull String password) {
        if (password.length() < 8) {
            return 0; // 太短
        }

        int score = 0;

        // 检查长度
        if (password.length() >= 12) {
            score++;
        }

        // 检查字符类型
        for (Pattern pattern : PASSWORD_PATTERNS) {
            if (pattern.matcher(password).matches()) {
                score++;
            }
        }

        // 检查常见弱密码模式
        if (isCommonWeakPassword(password)) {
            score = 0;
        }

        // 转换为强度等级
        if (score >= 4) {
            return 2; // 强
        } else if (score >= 2) {
            return 1; // 中
        } else {
            return 0; // 弱
        }
    }

    /**
     * 检查是否为常见的弱密码
     */
    private static boolean isCommonWeakPassword(@NonNull String password) {
        String lowerPassword = password.toLowerCase();

        // 常见弱密码列表
        String[] weakPasswords = {
                "password", "123456", "123456789", "qwerty", "abc123",
                "password123", "admin", "letmein", "welcome", "monkey",
                "1234567890", "password1", "qwertyuiop", "123123", "starwars"
        };

        for (String weak : weakPasswords) {
            if (lowerPassword.contains(weak)) {
                return true;
            }
        }

        // 检查连续字符或重复字符
        if (hasSequentialChars(password) || hasRepeatingChars(password)) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否包含连续字符
     */
    private static boolean hasSequentialChars(@NonNull String password) {
        int sequenceCount = 0;

        for (int i = 1; i < password.length(); i++) {
            char prev = password.charAt(i - 1);
            char curr = password.charAt(i);

            if (curr == prev + 1 || curr == prev - 1) {
                sequenceCount++;
                if (sequenceCount >= 2) {
                    return true;
                }
            } else {
                sequenceCount = 0;
            }
        }

        return false;
    }

    /**
     * 检查是否有太多重复字符
     */
    private static boolean hasRepeatingChars(@NonNull String password) {
        int repeatCount = 0;

        for (int i = 1; i < password.length(); i++) {
            if (password.charAt(i) == password.charAt(i - 1)) {
                repeatCount++;
                if (repeatCount >= 2) {
                    return true;
                }
            } else {
                repeatCount = 0;
            }
        }

        return false;
    }

    /**
     * 检查设备是否已root
     */
    public static boolean isDeviceRooted() {
        // 检查su文件
        String[] paths = {
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
        };

        for (String path : paths) {
            if (new File(path).exists()) {
                return true;
            }
        }

        // 检查build tags
        String buildTags = Build.TAGS;
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true;
        }

        return false;
    }

    /**
     * 检查应用是否处于调试模式
     */
    public static boolean isAppInDebugMode(@NonNull Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    /**
     * 检查是否可以调试
     */
    public static boolean isDebuggable(@NonNull Context context) {
        return isAppInDebugMode(context) || android.os.Debug.isDebuggerConnected();
    }

    /**
     * 检查是否处于模拟器环境
     */
    public static boolean isEmulator() {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.toLowerCase().contains("vbox") ||
                Build.FINGERPRINT.toLowerCase().contains("test-keys") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk".equals(Build.PRODUCT));
    }

    /**
     * 检查开发者选项是否开启
     */
    public static boolean isDeveloperOptionsEnabled(@NonNull Context context) {
        return Settings.Secure.getInt(context.getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;
    }

    /**
     * 检查是否允许未知来源
     */
    public static boolean isUnknownSourcesEnabled(@NonNull Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * 检查ADB调试是否开启
     */
    public static boolean isAdbEnabled(@NonNull Context context) {
        return Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.ADB_ENABLED, 0) != 0;
    }

    /**
     * 获取应用签名哈希
     */
    @Nullable
    public static String getAppSignatureHash(@NonNull Context context) {
        try {
            int flags = PackageManager.GET_SIGNING_CERTIFICATES;
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), flags);

            if (packageInfo.signingInfo != null) {
                android.content.pm.SigningInfo signingInfo = packageInfo.signingInfo;
                if (signingInfo.hasMultipleSigners()) {
                    // Use APK signing scheme
                    for (android.content.pm.Signature signature : signingInfo.getApkContentsSigners()) {
                        byte[] signatureBytes = signature.toByteArray();
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        md.update(signatureBytes);
                        return bytesToHex(md.digest());
                    }
                } else {
                    // Use debug signing certificate
                    for (android.content.pm.Signature signature : signingInfo.getApkContentsSigners()) {
                        byte[] signatureBytes = signature.toByteArray();
                        MessageDigest md = MessageDigest.getInstance("SHA-256");
                        md.update(signatureBytes);
                        return bytesToHex(md.digest());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get app signature", e);
        }

        return null;
    }

    /**
     * 验证应用签名
     */
    public static boolean verifyAppSignature(@NonNull Context context, @NonNull String expectedHash) {
        String actualHash = getAppSignatureHash(context);
        return !TextUtils.isEmpty(actualHash) && actualHash.equals(expectedHash);
    }

    /**
     * 生成安全的随机数
     */
    public static int secureRandomInt(int bound) {
        return SECURE_RANDOM.nextInt(bound);
    }

    /**
     * 生成安全的随机字符串
     */
    @NonNull
    public static String secureRandomString(int length, @NonNull String allowedChars) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(allowedChars.charAt(SECURE_RANDOM.nextInt(allowedChars.length())));
        }
        return result.toString();
    }

    /**
     * 清除字符串内容（用于敏感数据）
     */
    public static void clearString(@Nullable StringBuilder sb) {
        if (sb != null) {
            sb.setLength(0);
            // 填充随机数据
            for (int i = 0; i < sb.capacity(); i++) {
                sb.append((char) SECURE_RANDOM.nextInt(256));
            }
            sb.setLength(0);
        }
    }

    /**
     * 清除字符数组内容（用于密码等）
     */
    public static void clearCharArray(@Nullable char[] array) {
        if (array != null) {
            SECURE_RANDOM.nextBytes(new byte[array.length]);
            for (int i = 0; i < array.length; i++) {
                array[i] = 0;
            }
        }
    }

    /**
     * 清除字节数组内容
     */
    public static void clearByteArray(@Nullable byte[] array) {
        if (array != null) {
            SECURE_RANDOM.nextBytes(array);
        }
    }

    /**
     * 安全比较两个字符数组（防止时序攻击）
     */
    public static boolean secureEquals(@Nullable char[] a, @Nullable char[] b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }

        return result == 0;
    }

    /**
     * 安全比较两个字节数组（防止时序攻击）
     */
    public static boolean secureEquals(@Nullable byte[] a, @Nullable byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }

        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }

        return result == 0;
    }
}