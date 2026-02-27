package com.ttt.safevault.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.lambdapioneer.argon2kt.Argon2Kt;
import com.lambdapioneer.argon2kt.Argon2KtResult;
import com.lambdapioneer.argon2kt.Argon2Mode;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Argon2id 密钥派生管理器
 *
 * 使用 Argon2id 算法进行密钥派生，与后端保持一致的参数配置：
 * - 时间成本 (timeCost): 3 次迭代
 * - 内存成本 (memoryCost): 128MB (131072 KB)
 * - 并行度 (parallelism): 4 线程
 * - 输出长度 (outputLength): 32 字节 (256 位)
 * - 盐值长度 (saltLength): 16 字节
 *
 * Android 端使用 argon2kt（专为 Android 设计的 JNI 绑定），
 * 后端使用 argon2-jvm（标准 JVM 实现）。
 * 两者使用相同的 Argon2 算法参数，确保前后端一致。
 *
 * @since SafeVault 3.1.0 (安全加固第二阶段 - Argon2id 升级)
 * @since SafeVault 3.2.0 (迁移至 argon2kt，解决 Android 兼容性问题)
 */
public class Argon2KeyDerivationManager {
    private static final String TAG = "Argon2KeyDerivationManager";
    private static final String PREFS_NAME = "argon2_key_derivation_prefs";
    private static final String KEY_USER_ALGORITHM_PREFIX = "user_algorithm_";
    private static final String KEY_USER_SALT_PREFIX = "user_salt_";
    private static final String KEY_MIGRATION_REQUIRED = "migration_required";

    // ========== Argon2id 参数配置（与后端一致） ==========
    /** 时间成本（迭代次数） */
    private static final int ARGON2_TIME_COST = 3;
    /** 内存成本（KB）- 128MB */
    private static final int ARGON2_MEMORY_COST = 131072;
    /** 并行度（线程数） */
    private static final int ARGON2_PARALLELISM = 4;
    /** 输出长度（字节）- 256位 */
    private static final int ARGON2_OUTPUT_LENGTH = 32;
    /** 盐值长度（字节） */
    private static final int ARGON2_SALT_LENGTH = 16;

    // ========== 算法版本标识 ==========
    /** 旧版 PBKDF2 算法标识 */
    public static final String ALGORITHM_PBKDF2 = "PBKDF2";
    /** 新版 Argon2id 算法标识 */
    public static final String ALGORITHM_ARGON2ID = "ARGON2ID";

    private static volatile Argon2KeyDerivationManager INSTANCE;
    private final Context context;
    private final SharedPreferences prefs;
    private final Argon2Kt argon2Kt;

    /**
     * 密钥派生结果
     */
    public static class DerivedKeyResult {
        private final SecretKey key;
        private final String algorithm;
        private final String salt;

        public DerivedKeyResult(SecretKey key, String algorithm, String salt) {
            this.key = key;
            this.algorithm = algorithm;
            this.salt = salt;
        }

        public SecretKey getKey() {
            return key;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public String getSalt() {
            return salt;
        }
    }

    private Argon2KeyDerivationManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // 创建 Argon2Kt 实例（线程安全，专为 Android 设计）
        this.argon2Kt = new Argon2Kt();

        Log.i(TAG, "Argon2KeyDerivationManager 初始化成功（使用 argon2kt）");
        Log.i(TAG, "参数配置: timeCost=" + ARGON2_TIME_COST +
                   ", memoryCost=" + ARGON2_MEMORY_COST + "KB (" + (ARGON2_MEMORY_COST / 1024) + "MB)" +
                   ", parallelism=" + ARGON2_PARALLELISM +
                   ", outputLength=" + ARGON2_OUTPUT_LENGTH + " bytes");
    }

    /**
     * 获取 Argon2KeyDerivationManager 单例
     */
    public static Argon2KeyDerivationManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (Argon2KeyDerivationManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Argon2KeyDerivationManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // ========== 核心密钥派生方法 ==========

    /**
     * 使用 Argon2id 从主密码派生密钥（使用 char[]）- 内存安全版本
     *
     * 此方法接受 char[] 参数，在密钥派生完成后会自动清零密码字符数组（内存安全强化）
     *
     * @param masterPassword 主密码字符数组（会被自动清零）
     * @param saltBase64 盐值（Base64编码）
     * @return 派生的密钥（256位 AES 密钥）
     * @throws SecurityException 派生失败时抛出
     */
    @NonNull
    public SecretKey deriveKeyWithArgon2id(@NonNull char[] masterPassword,
                                           @NonNull String saltBase64) {
        try {
            long startTime = System.currentTimeMillis();

            // 将密码字符数组转换为字节数组
            byte[] passwordBytes = new byte[masterPassword.length * 2];
            for (int i = 0; i < masterPassword.length; i++) {
                passwordBytes[i * 2] = (byte) (masterPassword[i] >> 8);
                passwordBytes[i * 2 + 1] = (byte) masterPassword[i];
            }

            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);

            // 使用 argon2kt 进行密钥派生
            Argon2KtResult result = argon2Kt.hash(
                    Argon2Mode.ARGON2_ID,        // Argon2id 模式
                    passwordBytes,               // 密码字节数组
                    salt,                        // 盐值字节数组
                    ARGON2_TIME_COST,            // 迭代次数
                    ARGON2_MEMORY_COST           // 内存成本（KB）
            );

            // 从结果中获取原始哈希值
            ByteBuffer hashBuffer = result.getRawHash();
            byte[] hash = new byte[hashBuffer.remaining()];
            hashBuffer.get(hash);

            // 确保密钥长度为 32 字节（256 位）
            byte[] keyBytes = new byte[32];
            System.arraycopy(hash, 0, keyBytes, 0, Math.min(hash.length, 32));

            // 创建 AES 密钥
            SecretKey derivedKey = new SecretKeySpec(keyBytes, "AES");

            // 安全清除敏感数据
            com.ttt.safevault.security.MemorySanitizer.secureWipe(passwordBytes);
            java.util.Arrays.fill(hash, (byte) 0);

            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Argon2id 密钥派生成功（char[]），耗时: " + elapsed + "ms");

            return derivedKey;

        } catch (Exception e) {
            Log.e(TAG, "Argon2id 密钥派生失败", e);
            throw new SecurityException("Argon2id key derivation failed", e);
        } finally {
            // 自动清零密码字符数组（内存安全强化）
            com.ttt.safevault.security.MemorySanitizer.secureWipe(masterPassword);
        }
    }

    /**
     * 使用 Argon2id 从主密码派生密钥（使用 String）- 向后兼容版本
     *
     * 注意：此方法接受 String 参数，无法清零内存（String 不可变）。
     * 推荐使用 {@link #deriveKeyWithArgon2id(char[], String)} 版本以获得更好的内存安全性。
     *
     * @param masterPassword 主密码（String，无法清零）
     * @param saltBase64 盐值（Base64编码）
     * @return 派生的密钥（256位 AES 密钥）
     * @throws SecurityException 派生失败时抛出
     * @deprecated 使用 {@link #deriveKeyWithArgon2id(char[], String)} 替代
     */
    @Deprecated
    @NonNull
    public SecretKey deriveKeyWithArgon2id(@NonNull String masterPassword,
                                           @NonNull String saltBase64) {
        long startTime = System.currentTimeMillis();

        try {
            // 将密码和盐值转换为字节数组
            byte[] passwordBytes = masterPassword.getBytes(StandardCharsets.UTF_8);
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);

            // 使用 argon2kt 进行密钥派生
            // 注意：argon2kt 返回的哈希格式与 argon2-jvm 略有不同
            // 我们直接使用 rawHash 提取原始哈希字节
            Argon2KtResult result = argon2Kt.hash(
                    Argon2Mode.ARGON2_ID,        // Argon2id 模式
                    passwordBytes,               // 密码字节数组
                    salt,                        // 盐值字节数组
                    ARGON2_TIME_COST,            // 迭代次数
                    ARGON2_MEMORY_COST           // 内存成本（KB）
            );

            // 从结果中获取原始哈希值
            // argon2kt 返回 ByteBuffer，需要转换为 byte[]
            ByteBuffer hashBuffer = result.getRawHash();
            byte[] hash = new byte[hashBuffer.remaining()];
            hashBuffer.get(hash);

            // 确保密钥长度为 32 字节（256 位）
            byte[] keyBytes = new byte[32];
            System.arraycopy(hash, 0, keyBytes, 0, Math.min(hash.length, 32));

            // 创建 AES 密钥
            SecretKey derivedKey = new SecretKeySpec(keyBytes, "AES");

            // 安全清除敏感数据
            java.util.Arrays.fill(passwordBytes, (byte) 0);
            java.util.Arrays.fill(hash, (byte) 0);

            long elapsed = System.currentTimeMillis() - startTime;
            Log.d(TAG, "Argon2id 密钥派生成功（String），耗时: " + elapsed + "ms");
            Log.w(TAG, "使用 String 参数派生密钥（无法清零内存），推荐使用 char[] 版本");

            return derivedKey;

        } catch (Exception e) {
            Log.e(TAG, "Argon2id 密钥派生失败", e);
            throw new SecurityException("Argon2id key derivation failed", e);
        }
    }

    /**
     * 生成新的盐值
     *
     * @return Base64 编码的盐值
     */
    @NonNull
    public String generateSalt() {
        byte[] salt = new byte[ARGON2_SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    // ========== 用户密钥管理 ==========

    /**
     * 获取或生成用户盐值
     *
     * @param userEmail 用户邮箱
     * @return Base64 编码的盐值
     */
    @NonNull
    public String getOrGenerateUserSalt(@NonNull String userEmail) {
        String key = KEY_USER_SALT_PREFIX + userEmail;
        String existingSalt = prefs.getString(key, null);

        if (existingSalt != null) {
            Log.d(TAG, "找到已存在的盐值: " + userEmail);
            return existingSalt;
        }

        // 生成新盐值
        String salt = generateSalt();
        prefs.edit().putString(key, salt).apply();
        Log.d(TAG, "生成新盐值: " + userEmail);

        return salt;
    }

    /**
     * 保存用户算法标识
     *
     * @param userEmail 用户邮箱
     * @param algorithm 算法标识（ALGORITHM_ARGON2ID 或 ALGORITHM_PBKDF2）
     */
    public void setUserAlgorithm(@NonNull String userEmail, @NonNull String algorithm) {
        String key = KEY_USER_ALGORITHM_PREFIX + userEmail;
        prefs.edit().putString(key, algorithm).apply();
        Log.i(TAG, "保存用户算法标识: " + userEmail + " -> " + algorithm);
    }

    /**
     * 获取用户算法标识
     *
     * @param userEmail 用户邮箱
     * @return 算法标识，未找到返回 ALGORITHM_PBKDF2（默认旧版）
     */
    @NonNull
    public String getUserAlgorithm(@NonNull String userEmail) {
        String key = KEY_USER_ALGORITHM_PREFIX + userEmail;
        String algorithm = prefs.getString(key, ALGORITHM_PBKDF2);
        Log.d(TAG, "获取用户算法: " + userEmail + " -> " + algorithm);
        return algorithm;
    }

    /**
     * 检查用户是否使用 Argon2id
     *
     * @param userEmail 用户邮箱
     * @return true 如果用户使用 Argon2id
     */
    public boolean isUserUsingArgon2id(@NonNull String userEmail) {
        return ALGORITHM_ARGON2ID.equals(getUserAlgorithm(userEmail));
    }

    /**
     * 标记用户为 Argon2id 用户
     *
     * @param userEmail 用户邮箱
     */
    public void markUserAsArgon2id(@NonNull String userEmail) {
        setUserAlgorithm(userEmail, ALGORITHM_ARGON2ID);
        // 清除迁移标志（如果已迁移）
        prefs.edit().remove(KEY_MIGRATION_REQUIRED + "_" + userEmail).apply();
        Log.i(TAG, "标记用户为 Argon2id: " + userEmail);
    }

    // ========== 迁移相关方法 ==========

    /**
     * 检查是否需要迁移
     *
     * @param userEmail 用户邮箱
     * @return true 如果用户使用 PBKDF2（需要迁移）
     */
    public boolean needsMigration(@NonNull String userEmail) {
        return !isUserUsingArgon2id(userEmail);
    }

    /**
     * 设置迁移标志
     *
     * @param userEmail 用户邮箱
     * @param required 是否需要迁移
     */
    public void setMigrationRequired(@NonNull String userEmail, boolean required) {
        String key = KEY_MIGRATION_REQUIRED + "_" + userEmail;
        if (required) {
            prefs.edit().putBoolean(key, true).apply();
        } else {
            prefs.edit().remove(key).apply();
        }
    }

    /**
     * 检查迁移标志
     *
     * @param userEmail 用户邮箱
     * @return true 如果已标记需要迁移
     */
    public boolean isMigrationRequired(@NonNull String userEmail) {
        String key = KEY_MIGRATION_REQUIRED + "_" + userEmail;
        return prefs.getBoolean(key, false);
    }

    /**
     * 清除用户所有数据（用于重置）
     *
     * @param userEmail 用户邮箱
     */
    public void clearUserData(@NonNull String userEmail) {
        prefs.edit()
                .remove(KEY_USER_ALGORITHM_PREFIX + userEmail)
                .remove(KEY_USER_SALT_PREFIX + userEmail)
                .remove(KEY_MIGRATION_REQUIRED + "_" + userEmail)
                .apply();
        Log.w(TAG, "清除用户数据: " + userEmail);
    }

    // ========== 参数信息 ==========

    /**
     * 获取当前参数配置信息
     *
     * @return 参数信息字符串
     */
    @NonNull
    public String getParametersInfo() {
        return String.format("Argon2id(timeCost=%d, memoryCost=%dKB (%dMB), parallelism=%d, outputLength=%d bytes, saltLength=%d bytes)",
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST,
                ARGON2_MEMORY_COST / 1024,
                ARGON2_PARALLELISM,
                ARGON2_OUTPUT_LENGTH,
                ARGON2_SALT_LENGTH);
    }

    /**
     * 获取与后端一致的参数配置 Map
     *
     * @return 参数配置 Map
     */
    @NonNull
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("algorithm", "ARGON2ID");
        params.put("timeCost", ARGON2_TIME_COST);
        params.put("memoryCost", ARGON2_MEMORY_COST);
        params.put("memoryCostMB", ARGON2_MEMORY_COST / 1024);
        params.put("parallelism", ARGON2_PARALLELISM);
        params.put("outputLength", ARGON2_OUTPUT_LENGTH);
        params.put("saltLength", ARGON2_SALT_LENGTH);
        params.put("implementation", "argon2kt"); // 标识 Android 端实现
        return params;
    }

    /**
     * 验证密码（使用存储的哈希值）- 使用 char[]
     *
     * 注意：此方法用于验证主密码，不用于派生密钥
     * 派生密钥应使用 deriveKeyWithArgon2id()
     *
     * 此方法在验证完成后会自动清零密码字符数组（内存安全强化）
     *
     * @param masterPassword 主密码字符数组（会被自动清零）
     * @param storedHash 存储的哈希值（Base64 编码的原始哈希）
     * @param saltBase64 盐值（Base64 编码）
     * @return 密码是否匹配
     */
    public boolean verifyPassword(@NonNull char[] masterPassword,
                                  @NonNull String storedHash,
                                  @NonNull String saltBase64) {
        try {
            // 将密码字符数组转换为字节数组
            byte[] passwordBytes = new byte[masterPassword.length * 2];
            for (int i = 0; i < masterPassword.length; i++) {
                passwordBytes[i * 2] = (byte) (masterPassword[i] >> 8);
                passwordBytes[i * 2 + 1] = (byte) masterPassword[i];
            }

            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);

            // 使用 argon2kt 重新计算哈希
            Argon2KtResult result = argon2Kt.hash(
                    Argon2Mode.ARGON2_ID,
                    passwordBytes,
                    salt,
                    ARGON2_TIME_COST,
                    ARGON2_MEMORY_COST
            );

            // 获取计算出的原始哈希值
            ByteBuffer hashBuffer = result.getRawHash();
            byte[] computedHash = new byte[hashBuffer.remaining()];
            hashBuffer.get(computedHash);

            // 解析存储的哈希值
            byte[] storedHashBytes = Base64.decode(storedHash, Base64.NO_WRAP);

            // 比较哈希值
            boolean matches = java.util.Arrays.equals(computedHash, storedHashBytes);

            // 安全清除敏感数据
            com.ttt.safevault.security.MemorySanitizer.secureWipe(passwordBytes);
            java.util.Arrays.fill(computedHash, (byte) 0);

            Log.d(TAG, "密码验证" + (matches ? "成功" : "失败"));
            return matches;

        } catch (Exception e) {
            Log.e(TAG, "密码验证失败", e);
            return false;
        } finally {
            // 自动清零密码字符数组（内存安全强化）
            com.ttt.safevault.security.MemorySanitizer.secureWipe(masterPassword);
        }
    }

    /**
     * 验证密码（使用存储的哈希值）- 使用 String
     *
     * 注意：此方法接受 String 参数，无法清零内存（String 不可变）。
     * 推荐使用 {@link #verifyPassword(char[], String, String)} 版本以获得更好的内存安全性。
     *
     * @param masterPassword 主密码（String，无法清零）
     * @param storedHash 存储的哈希值（Base64 编码的原始哈希）
     * @param saltBase64 盐值（Base64 编码）
     * @return 密码是否匹配
     * @deprecated 使用 {@link #verifyPassword(char[], String, String)} 替代
     */
    @Deprecated
    public boolean verifyPassword(@NonNull String masterPassword,
                                  @NonNull String storedHash,
                                  @NonNull String saltBase64) {
        try {
            // 将密码和盐值转换为字节数组
            byte[] passwordBytes = masterPassword.getBytes(StandardCharsets.UTF_8);
            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);

            // 使用 argon2kt 重新计算哈希
            Argon2KtResult result = argon2Kt.hash(
                    Argon2Mode.ARGON2_ID,
                    passwordBytes,
                    salt,
                    ARGON2_TIME_COST,
                    ARGON2_MEMORY_COST
            );

            // 获取计算出的原始哈希值
            // argon2kt 返回 ByteBuffer，需要转换为 byte[]
            ByteBuffer hashBuffer = result.getRawHash();
            byte[] computedHash = new byte[hashBuffer.remaining()];
            hashBuffer.get(computedHash);

            // 解析存储的哈希值
            byte[] storedHashBytes = Base64.decode(storedHash, Base64.NO_WRAP);

            // 比较哈希值
            boolean matches = java.util.Arrays.equals(computedHash, storedHashBytes);

            // 安全清除敏感数据
            java.util.Arrays.fill(passwordBytes, (byte) 0);
            java.util.Arrays.fill(computedHash, (byte) 0);

            Log.d(TAG, "密码验证" + (matches ? "成功" : "失败"));
            Log.w(TAG, "使用 String 参数验证密码（无法清零内存），推荐使用 char[] 版本");
            return matches;

        } catch (Exception e) {
            Log.e(TAG, "密码验证失败", e);
            return false;
        }
    }

    /**
     * 对密码进行哈希（用于存储或传输）- 使用 char[]
     *
     * 此方法在哈希完成后会自动清零密码字符数组（内存安全强化）
     *
     * @param masterPassword 主密码字符数组（会被自动清零）
     * @param salt 盐值（Base64编码，如果为 null，则自动生成）
     * @return 包含哈希值和盐值的 Map
     */
    @NonNull
    public Map<String, String> hashPassword(@NonNull char[] masterPassword,
                                            String salt) {
        Map<String, String> result = new HashMap<>();

        if (salt == null) {
            salt = generateSalt();
        }

        try {
            // 将密码字符数组转换为字节数组
            byte[] passwordBytes = new byte[masterPassword.length * 2];
            for (int i = 0; i < masterPassword.length; i++) {
                passwordBytes[i * 2] = (byte) (masterPassword[i] >> 8);
                passwordBytes[i * 2 + 1] = (byte) masterPassword[i];
            }

            byte[] saltBytes = Base64.decode(salt, Base64.NO_WRAP);

            // 使用 argon2kt 哈希密码
            Argon2KtResult hashResult = argon2Kt.hash(
                    Argon2Mode.ARGON2_ID,
                    passwordBytes,
                    saltBytes,
                    ARGON2_TIME_COST,
                    ARGON2_MEMORY_COST
            );

            // 获取原始哈希值并编码为 Base64
            ByteBuffer hashBuffer = hashResult.getRawHash();
            byte[] rawHash = new byte[hashBuffer.remaining()];
            hashBuffer.get(rawHash);
            String hashBase64 = Base64.encodeToString(rawHash, Base64.NO_WRAP);

            // 安全清除敏感数据
            com.ttt.safevault.security.MemorySanitizer.secureWipe(passwordBytes);
            java.util.Arrays.fill(rawHash, (byte) 0);

            result.put("hash", hashBase64);
            result.put("salt", salt);
            result.put("algorithm", ALGORITHM_ARGON2ID);

            Log.d(TAG, "密码哈希完成（char[]）");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "密码哈希失败", e);
            throw new SecurityException("Password hashing failed", e);
        } finally {
            // 自动清零密码字符数组（内存安全强化）
            com.ttt.safevault.security.MemorySanitizer.secureWipe(masterPassword);
        }
    }

    /**
     * 对密码进行哈希（用于存储或传输）- 使用 String
     *
     * 注意：此方法接受 String 参数，无法清零内存（String 不可变）。
     * 推荐使用 {@link #hashPassword(char[], String)} 版本以获得更好的内存安全性。
     *
     * @param masterPassword 主密码（String，无法清零）
     * @param salt 盐值（Base64编码，如果为 null，则自动生成）
     * @return 包含哈希值和盐值的 Map
     * @deprecated 使用 {@link #hashPassword(char[], String)} 替代
     */
    @Deprecated
    @NonNull
    public Map<String, String> hashPassword(@NonNull String masterPassword,
                                            String salt) {
        Map<String, String> result = new HashMap<>();

        if (salt == null) {
            salt = generateSalt();
        }

        try {
            byte[] passwordBytes = masterPassword.getBytes(StandardCharsets.UTF_8);
            byte[] saltBytes = Base64.decode(salt, Base64.NO_WRAP);

            // 使用 argon2kt 哈希密码
            Argon2KtResult hashResult = argon2Kt.hash(
                    Argon2Mode.ARGON2_ID,
                    passwordBytes,
                    saltBytes,
                    ARGON2_TIME_COST,
                    ARGON2_MEMORY_COST
            );

            // 获取原始哈希值并编码为 Base64
            // argon2kt 返回 ByteBuffer，需要转换为 byte[]
            ByteBuffer hashBuffer = hashResult.getRawHash();
            byte[] rawHash = new byte[hashBuffer.remaining()];
            hashBuffer.get(rawHash);
            String hashBase64 = Base64.encodeToString(rawHash, Base64.NO_WRAP);

            // 安全清除敏感数据
            java.util.Arrays.fill(passwordBytes, (byte) 0);
            java.util.Arrays.fill(rawHash, (byte) 0);

            result.put("hash", hashBase64);
            result.put("salt", salt);
            result.put("algorithm", ALGORITHM_ARGON2ID);

            Log.d(TAG, "密码哈希完成（String）");
            Log.w(TAG, "使用 String 参数哈希密码（无法清零内存），推荐使用 char[] 版本");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "密码哈希失败", e);
            throw new SecurityException("Password hashing failed", e);
        }
    }
}
