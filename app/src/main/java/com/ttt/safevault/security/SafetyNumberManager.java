package com.ttt.safevault.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * 安全码管理器
 * 用于生成和验证公钥的安全指纹，防止中间人攻击
 *
 * 参考行业最佳实践（Signal、WhatsApp）：
 * - 短指纹：5组2位数字，便于口头传达
 * - 长指纹：完整SHA-256哈希，用于高级验证
 * - 验证状态：本地存储已验证的公钥指纹
 */
public class SafetyNumberManager {
    private static final String TAG = "SafetyNumberManager";
    private static final String PREFS_NAME = "safety_numbers";
    private static final String KEY_VERIFIED_USERS = "verified_users";

    private static volatile SafetyNumberManager INSTANCE;
    private final Context context;
    private final SharedPreferences prefs;

    private SafetyNumberManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取 SafetyNumberManager 单例
     */
    public static SafetyNumberManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SafetyNumberManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SafetyNumberManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 生成短指纹（5组2位数字）
     * 格式：12-34-56-78-90
     * 便于口头传达（电话验证）和面对面核对
     *
     * @param publicKey 公钥
     * @return 短指纹字符串
     */
    @NonNull
    public String generateShortFingerprint(@NonNull PublicKey publicKey) {
        try {
            // 1. SHA-256 哈希公钥编码
            byte[] publicKeyBytes = publicKey.getEncoded();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes);

            // 2. 取前 4 字节，转换为 5 组 2 位数字
            int[] digits = new int[5];
            for (int i = 0; i < 4; i++) {
                digits[i] = (hash[i] & 0xFF) % 100;  // 0-99
            }
            // 第5组：前4字节之和模100
            int sum = 0;
            for (int i = 0; i < 4; i++) {
                sum += (hash[i] & 0xFF);
            }
            digits[4] = sum % 100;

            // 3. 格式化为 12-34-56-78-90
            return String.format("%02d-%02d-%02d-%02d-%02d",
                digits[0], digits[1], digits[2], digits[3], digits[4]);

        } catch (Exception e) {
            Log.e(TAG, "生成短指纹失败", e);
            return "00-00-00-00-00";
        }
    }

    /**
     * 生成长指纹（完整 SHA-256 哈希）
     * 64字符十六进制字符串，支持复制粘贴到其他渠道
     *
     * @param publicKey 公钥
     * @return 长指纹字符串（十六进制）
     */
    @NonNull
    public String generateFullFingerprint(@NonNull PublicKey publicKey) {
        try {
            byte[] publicKeyBytes = publicKey.getEncoded();
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(publicKeyBytes);

            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            // 每8个字符加一个空格，便于阅读
            String full = hexString.toString();
            return formatFingerprintWithSpaces(full);

        } catch (Exception e) {
            Log.e(TAG, "生成长指纹失败", e);
            return "生成失败";
        }
    }

    /**
     * 将长指纹格式化为带空格的形式（每8个字符一组）
     */
    @NonNull
    private String formatFingerprintWithSpaces(@NonNull String fingerprint) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < fingerprint.length(); i += 8) {
            if (i > 0) {
                formatted.append(" ");
            }
            int end = Math.min(i + 8, fingerprint.length());
            formatted.append(fingerprint.substring(i, end));
        }
        return formatted.toString();
    }

    /**
     * 检查用户是否已验证安全码
     *
     * @param username 用户名（通常是邮箱）
     * @param publicKey 公钥
     * @return true表示已验证且公钥未变化
     */
    public boolean isVerified(@NonNull String username, @NonNull PublicKey publicKey) {
        try {
            String shortFingerprint = generateShortFingerprint(publicKey);
            String storedKey = "verified_" + username;
            String storedFingerprint = prefs.getString(storedKey, null);

            if (storedFingerprint == null) {
                return false; // 未验证过
            }

            // 检查公钥是否变化
            return storedFingerprint.equals(shortFingerprint);

        } catch (Exception e) {
            Log.e(TAG, "检查验证状态失败", e);
            return false;
        }
    }

    /**
     * 检查公钥是否已变化（针对已验证的用户）
     *
     * @param username 用户名
     * @param currentPublicKey 当前公钥
     * @return 如果公钥已变化返回true，否则返回false
     */
    public boolean hasPublicKeyChanged(@NonNull String username, @NonNull PublicKey currentPublicKey) {
        try {
            String currentFingerprint = generateShortFingerprint(currentPublicKey);
            String storedKey = "verified_" + username;
            String storedFingerprint = prefs.getString(storedKey, null);

            if (storedFingerprint == null) {
                return false; // 从未验证过，不算变化
            }

            // 对比指纹
            boolean changed = !storedFingerprint.equals(currentFingerprint);
            if (changed) {
                Log.w(TAG, "检测到公钥变化: " + username);
                Log.w(TAG, "旧指纹: " + storedFingerprint);
                Log.w(TAG, "新指纹: " + currentFingerprint);
            }

            return changed;

        } catch (Exception e) {
            Log.e(TAG, "检测公钥变化失败", e);
            return false;
        }
    }

    /**
     * 标记用户为已验证
     *
     * @param username 用户名
     * @param publicKey 公钥
     */
    public void markAsVerified(@NonNull String username, @NonNull PublicKey publicKey) {
        try {
            String shortFingerprint = generateShortFingerprint(publicKey);
            String fullFingerprint = generateFullFingerprint(publicKey);
            String publicKeyBase64 = Base64.encodeToString(
                publicKey.getEncoded(), Base64.NO_WRAP);

            // 存储验证信息
            SharedPreferences.Editor editor = prefs.edit();
            String prefix = "verified_" + username;

            editor.putString(prefix, shortFingerprint);
            editor.putString(prefix + "_full", fullFingerprint);
            editor.putString(prefix + "_publickey", publicKeyBase64);
            editor.putLong(prefix + "_time", System.currentTimeMillis());

            // 添加到已验证用户列表
            Set<String> verifiedUsers = prefs.getStringSet(KEY_VERIFIED_USERS, new HashSet<>());
            Set<String> newVerifiedUsers = new HashSet<>(verifiedUsers);
            newVerifiedUsers.add(username);
            editor.putStringSet(KEY_VERIFIED_USERS, newVerifiedUsers);

            editor.apply();

            Log.d(TAG, "已标记用户为已验证: " + username);

        } catch (Exception e) {
            Log.e(TAG, "标记验证状态失败", e);
        }
    }

    /**
     * 清除用户的验证状态
     *
     * @param username 用户名
     */
    public void clearVerification(@NonNull String username) {
        try {
            SharedPreferences.Editor editor = prefs.edit();
            String prefix = "verified_" + username;

            editor.remove(prefix);
            editor.remove(prefix + "_full");
            editor.remove(prefix + "_publickey");
            editor.remove(prefix + "_time");

            // 从已验证用户列表中移除
            Set<String> verifiedUsers = prefs.getStringSet(KEY_VERIFIED_USERS, new HashSet<>());
            Set<String> newVerifiedUsers = new HashSet<>(verifiedUsers);
            newVerifiedUsers.remove(username);
            editor.putStringSet(KEY_VERIFIED_USERS, newVerifiedUsers);

            editor.apply();

            Log.d(TAG, "已清除用户验证状态: " + username);

        } catch (Exception e) {
            Log.e(TAG, "清除验证状态失败", e);
        }
    }

    /**
     * 获取已存储的短指纹
     *
     * @param username 用户名
     * @return 已存储的短指纹，如果不存在返回null
     */
    @Nullable
    public String getStoredShortFingerprint(@NonNull String username) {
        return prefs.getString("verified_" + username, null);
    }

    /**
     * 获取已存储的长指纹
     *
     * @param username 用户名
     * @return 已存储的长指纹，如果不存在返回null
     */
    @Nullable
    public String getStoredFullFingerprint(@NonNull String username) {
        return prefs.getString("verified_" + username + "_full", null);
    }

    /**
     * 获取验证时间戳
     *
     * @param username 用户名
     * @return 验证时间戳（毫秒），如果未验证返回0
     */
    public long getVerificationTime(@NonNull String username) {
        return prefs.getLong("verified_" + username + "_time", 0);
    }

    /**
     * 获取所有已验证的用户
     *
     * @return 已验证用户名的集合
     */
    @NonNull
    public Set<String> getVerifiedUsers() {
        return prefs.getStringSet(KEY_VERIFIED_USERS, new HashSet<>());
    }

    /**
     * 获取已验证用户数量
     */
    public int getVerifiedUserCount() {
        return getVerifiedUsers().size();
    }
}
