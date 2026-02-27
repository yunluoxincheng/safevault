package com.ttt.safevault.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import com.ttt.safevault.crypto.Argon2KeyDerivationManager;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 三层安全密钥存储管理器（安全加固第二阶段 - Argon2id升级）
 *
 * 架构概述：
 * ┌────────────────────────┐
 * │      Level 3 (最终)     │
 * │   RSA 私钥              │
 * │   用 DataKey 加密       │
 * └──────────▲─────────────┘
 *                     │
 *                     │ DataKey (随机256bit)
 *                     │
 * ┌──────────┴─────────────┐
 * │      Level 2 (可备份)   │
 * │ DataKey 被两把锁包裹：  │
 * │                        │
 * │ ① PasswordKey 加密     │ ← 可跨设备恢复（Argon2id派生）
 * │ ② DeviceKey 加密       │ ← 本机快速解锁
 * └──────────▲─────────────┘
 *                     │
 * ┌──────────┴─────────────┐
 * │      Level 1 (根)       │
 * │ PasswordKey  + DeviceKey│
 * └────────────────────────┘
 *
 * 安全特性：
 * - RSA私钥永远不会以明文形式存储
 * - 支持云端备份（PasswordKey加密的DataKey）
 * - 支持本地快速解锁（DeviceKey加密的DataKey）
 * - 使用AES-GCM加密（带完整性验证）
 * - 使用commit()保证原子性写入
 * - PasswordKey 使用 Argon2id 派生（与后端一致）
 *
 * Argon2id 参数（与后端 Argon2PasswordHasher 一致）：
 * - 时间成本: 3 次迭代
 * - 内存成本: 128MB (131072 KB)
 * - 并行度: 4 线程
 * - 输出长度: 32 字节 (256 位)
 *
 * @since SafeVault 3.2.0 (安全加固第二阶段 - Argon2id 升级)
 */
public class SecureKeyStorageManager {
    private static final String TAG = "SecureKeyStorageManager";
    private static final String PREFS_NAME = "secure_key_storage";

    // ========== Level 1: 根层常量 ==========
    /** PasswordKey派生迭代次数（PBKDF2） */
    public static final int PASSWORD_KEY_ITERATIONS = 600000;
    /** PasswordKey派生密钥长度（位） */
    private static final int PASSWORD_KEY_LENGTH = 256;

    /** DeviceKey别名（AndroidKeyStore） */
    public static final String DEVICE_KEY_ALIAS = "safevault_device_key";
    /** DeviceKey加密算法 */
    private static final String DEVICE_KEY_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;
    /** DeviceKey密钥长度（位） */
    private static final int DEVICE_KEY_SIZE = 256;
    /** DeviceKey生物识别有效期（秒） */
    private static final int DEVICE_KEY_AUTH_TIMEOUT = 30;

    // ========== Level 2: 中间层常量 ==========
    /** DataKey密钥长度（位） */
    public static final int DATA_KEY_SIZE = 256;
    /** DataKey加密算法（AEAD模式） */
    public static final String DATA_KEY_ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    /** GCM认证标签长度（位） */
    private static final int GCM_TAG_LENGTH = 128;
    /** GCM IV长度（字节） */
    private static final int GCM_IV_LENGTH = 12;

    // ========== Level 3: 数据层常量 ==========
    /** RSA密钥长度（位） */
    private static final int RSA_KEY_SIZE = 2048;
    /** RSA密钥算法 */
    private static final String RSA_ALGORITHM = "RSA";

    // ========== SharedPreferences存储键 ==========
    /** PasswordKey加密的DataKey（用于云端备份） */
    private static final String PASSWORD_ENCRYPTED_DATA_KEY = "password_encrypted_data_key";
    /** DeviceKey加密的DataKey（用于本地快速解锁） */
    private static final String DEVICE_ENCRYPTED_DATA_KEY = "device_encrypted_data_key";
    /** DataKey加密的RSA私钥 */
    private static final String ENCRYPTED_RSA_PRIVATE_KEY = "encrypted_rsa_private_key";
    /** RSA公钥（X.509编码，Base64） */
    private static final String RSA_PUBLIC_KEY = "rsa_public_key";
    /** 密钥版本标识 */
    private static final String KEY_VERSION = "key_version";
    /** 密钥版本值（三层架构） */
    private static final String KEY_VERSION_V3 = "v3";

    /** 迁移状态键 */
    private static final String MIGRATION_STATUS = "migration_status";
    /** 迁移时间戳键 */
    private static final String MIGRATION_TIMESTAMP = "migration_timestamp";
    /** 迁移错误消息键 */
    private static final String MIGRATION_ERROR = "migration_error";

    private static volatile SecureKeyStorageManager INSTANCE;
    private final Context context;
    private final SharedPreferences prefs;
    private final Argon2KeyDerivationManager argon2Manager;

    private SecureKeyStorageManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.argon2Manager = Argon2KeyDerivationManager.getInstance(context);
        Log.i(TAG, "SecureKeyStorageManager 初始化成功（使用 Argon2id）");
        Log.i(TAG, argon2Manager.getParametersInfo());
    }

    /**
     * 获取SecureKeyStorageManager单例
     */
    public static SecureKeyStorageManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (SecureKeyStorageManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SecureKeyStorageManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // ========== Level 1: PasswordKey派生 ==========

    /**
     * 从主密码派生 PasswordKey（Level 1 根层）- 使用 char[]
     *
     * 使用 Argon2id 算法（与后端一致）
     * 参数：timeCost=3, memoryCost=128MB, parallelism=4
     * 用于加密 DataKey 以便云端备份
     *
     * 此方法在密钥派生完成后会自动清零密码字符数组（内存安全强化）
     *
     * @param masterPassword 主密码字符数组（会被自动清零）
     * @param saltBase64 盐值（Base64编码）
     * @return PasswordKey（256位AES密钥）
     * @throws SecurityException 派生失败时抛出
     */
    @NonNull
    public SecretKey derivePasswordKey(@NonNull char[] masterPassword, @NonNull String saltBase64) {
        try {
            // 使用 Argon2id 派生密钥（与后端一致）
            SecretKey passwordKey = argon2Manager.deriveKeyWithArgon2id(masterPassword, saltBase64);
            Log.d(TAG, "PasswordKey 派生成功（Argon2id: t=3, m=128MB, p=4）");
            return passwordKey;
        } finally {
            // 自动清零密码字符数组（内存安全强化）
            MemorySanitizer.secureWipe(masterPassword);
            Log.d(TAG, "密码字符数组已自动清零");
        }
    }

    /**
     * 从主密码派生 PasswordKey（Level 1 根层）- 使用 String
     *
     * 使用 Argon2id 算法（与后端一致）
     * 参数：timeCost=3, memoryCost=128MB, parallelism=4
     * 用于加密 DataKey 以便云端备份
     *
     * 注意：此方法接受 String 参数，无法清零内存（String 不可变）。
     * 推荐使用 {@link #derivePasswordKey(char[], String)} 版本以获得更好的内存安全性。
     *
     * @param masterPassword 主密码（String，无法清零）
     * @param saltBase64 盐值（Base64编码）
     * @return PasswordKey（256位AES密钥）
     * @throws SecurityException 派生失败时抛出
     * @deprecated 使用 {@link #derivePasswordKey(char[], String)} 替代，获得更好的内存安全性
     */
    @Deprecated
    @NonNull
    public SecretKey derivePasswordKey(@NonNull String masterPassword, @NonNull String saltBase64) {
        // 使用 Argon2id 派生密钥（与后端一致）
        SecretKey passwordKey = argon2Manager.deriveKeyWithArgon2id(masterPassword, saltBase64);
        Log.d(TAG, "PasswordKey 派生成功（Argon2id: t=3, m=128MB, p=4）");
        Log.w(TAG, "使用 String 参数派生密钥（无法清零内存），推荐使用 char[] 版本");
        return passwordKey;
    }

    /**
     * 从主密码派生PasswordKey（使用 PBKDF2 - 向后兼容）
     *
     * 使用PBKDF2WithHmacSHA256算法，600,000次迭代
     * 仅用于旧数据迁移，新用户应使用 derivePasswordKey()
     *
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码）
     * @return PasswordKey（256位AES密钥）
     * @throws SecurityException 派生失败时抛出
     * @deprecated 使用 Argon2id 替代，仅保留用于迁移
     */
    @Deprecated
    public SecretKey derivePasswordKeyWithPBKDF2(@NonNull String masterPassword, @NonNull String saltBase64) {
        try {
            byte[] salt = android.util.Base64.decode(saltBase64, android.util.Base64.NO_WRAP);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            KeySpec spec = new PBEKeySpec(
                    masterPassword.toCharArray(),
                    salt,
                    PASSWORD_KEY_ITERATIONS,
                    PASSWORD_KEY_LENGTH
            );

            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            SecretKey passwordKey = new SecretKeySpec(keyBytes, "AES");

            Log.w(TAG, "PasswordKey 派生成功（PBKDF2 600,000次迭代 - 已废弃）");
            return passwordKey;

        } catch (Exception e) {
            Log.e(TAG, "PasswordKey 派生失败（PBKDF2）", e);
            throw new SecurityException("Failed to derive PasswordKey (PBKDF2)", e);
        }
    }

    // ========== Level 1: DeviceKey管理 ==========

    /**
     * 获取或创建DeviceKey（Level 1 根层）
     *
     * DeviceKey存储在AndroidKeyStore中，受硬件保护
     * 需要生物识别认证才能使用，有效期30秒
     *
     * @return DeviceKey（AES-256-GCM密钥），失败返回null
     */
    @Nullable
    public SecretKey getOrCreateDeviceKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            // 检查密钥是否已存在
            if (keyStore.containsAlias(DEVICE_KEY_ALIAS)) {
                SecretKey existingKey = (SecretKey) keyStore.getKey(DEVICE_KEY_ALIAS, null);
                Log.d(TAG, "DeviceKey已存在于AndroidKeyStore");
                return existingKey;
            }

            // 生成新的DeviceKey
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    DEVICE_KEY_ALGORITHM,
                    "AndroidKeyStore"
            );

            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    DEVICE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(DEVICE_KEY_SIZE)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(DEVICE_KEY_AUTH_TIMEOUT)
                    .build();

            keyGenerator.init(spec);
            SecretKey deviceKey = keyGenerator.generateKey();

            Log.i(TAG, "DeviceKey创建成功（AndroidKeyStore保护，30秒有效期）");
            return deviceKey;

        } catch (Exception e) {
            Log.e(TAG, "DeviceKey创建失败", e);
            return null;
        }
    }

    // ========== Level 2: DataKey生成 ==========

    /**
     * 生成随机DataKey（Level 2 中间层）
     *
     * DataKey是256位随机AES密钥，用于加密RSA私钥
     * 被双重加密存储：PasswordKey加密（可备份）+ DeviceKey加密（本地解锁）
     *
     * @return DataKey（256位AES密钥）
     * @throws SecurityException 生成失败时抛出
     */
    public SecretKey generateDataKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(DATA_KEY_SIZE);
            SecretKey dataKey = keyGenerator.generateKey();

            Log.d(TAG, "DataKey生成成功（256位随机密钥）");
            return dataKey;

        } catch (Exception e) {
            Log.e(TAG, "DataKey生成失败", e);
            throw new SecurityException("Failed to generate DataKey", e);
        }
    }

    // ========== Level 2: DataKey双重加密存储 ==========

    /**
     * 双重加密DataKey并保存（Level 2 中间层）
     *
     * 使用PasswordKey和DeviceKey分别加密DataKey
     * PasswordKey加密版本用于云端备份（可跨设备恢复）
     * DeviceKey加密版本用于本地快速解锁（生物识别）
     * 使用commit()同步保存，确保原子性写入
     *
     * @param dataKey 待加密的DataKey
     * @param passwordKey PasswordKey（从主密码派生）
     * @param deviceKey DeviceKey（AndroidKeyStore）
     * @return 成功返回true，失败返回false
     */
    public boolean encryptAndSaveDataKey(@NonNull SecretKey dataKey,
                                          @NonNull SecretKey passwordKey,
                                          @NonNull SecretKey deviceKey) {
        try {
            // 1. 用PasswordKey加密DataKey（用于云端备份）- 这是必须的
            String passwordEncrypted = encryptKeyWithAES(dataKey, passwordKey);
            if (passwordEncrypted == null) {
                Log.e(TAG, "PasswordKey加密DataKey失败");
                return false;
            }

            // 2. 用DeviceKey加密DataKey（用于本地快速解锁）- 这是可选的
            // 如果用户还没有通过生物识别认证，DeviceKey加密会失败，但不影响整体流程
            String deviceEncrypted = null;
            try {
                deviceEncrypted = encryptKeyWithAES(dataKey, deviceKey);
                if (deviceEncrypted != null) {
                    Log.d(TAG, "DeviceKey加密DataKey成功");
                } else {
                    Log.w(TAG, "DeviceKey加密DataKey返回null（可能需要生物识别认证）");
                }
            } catch (Exception e) {
                Log.w(TAG, "DeviceKey加密DataKey失败（可能需要生物识别认证）: " + e.getMessage());
                // 不返回false，继续流程
            }

            // 3. 使用commit()同步保存（原子性）
            SharedPreferences.Editor editor = prefs.edit()
                    .putString(PASSWORD_ENCRYPTED_DATA_KEY, passwordEncrypted);

            // 只有当DeviceKey加密成功时才保存
            if (deviceEncrypted != null) {
                editor.putString(DEVICE_ENCRYPTED_DATA_KEY, deviceEncrypted);
            }

            boolean saved = editor.commit();

            if (!saved) {
                Log.e(TAG, "DataKey保存失败（commit返回false）");
                return false;
            }

            // 4. 验证完整性（只验证PasswordKey版本，DeviceKey版本是可选的）
            if (!validateDataKeyStorage(false)) {
                Log.e(TAG, "DataKey存储完整性验证失败");
                return false;
            }

            if (deviceEncrypted != null) {
                Log.i(TAG, "DataKey双重加密保存成功");
            } else {
                Log.i(TAG, "DataKey保存成功（仅PasswordKey加密，DeviceKey加密将在启用生物识别后完成）");
            }
            return true;

        } catch (Exception e) {
            Log.e(TAG, "DataKey加密存储失败", e);
            return false;
        }
    }

    /**
     * 使用AES-GCM加密密钥
     *
     * @param keyToEncrypt 待加密的密钥
     * @param encryptionKey 加密密钥
     * @return Base64格式的加密数据（格式：base64(IV).base64(encryptedData)）
     *         对于 AndroidKeyStore 密钥，IV 由系统自动生成并附加在密文前
     */
    @Nullable
    private String encryptKeyWithAES(@NonNull SecretKey keyToEncrypt,
                                      @NonNull SecretKey encryptionKey) {
        try {
            Cipher cipher = Cipher.getInstance(DATA_KEY_ENCRYPTION_ALGORITHM);

            // 检查是否为 AndroidKeyStore 密钥（更可靠的检测方法）
            // AndroidKeyStore 密钥的 getEncoded() 返回 null
            // 类名包含 "AndroidKeyStore" 或来自 keymaster 包
            boolean isKeystoreKey = isAndroidKeyStoreKey(encryptionKey);

            byte[] iv;
            byte[] encrypted;

            if (isKeystoreKey) {
                // AndroidKeyStore 密钥：不允许提供自定义 IV，系统会自动生成
                Log.d(TAG, "使用 AndroidKeyStore 密钥加密，IV 由系统自动生成");
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey);
                encrypted = cipher.doFinal(keyToEncrypt.getEncoded());
                // 从 cipher 获取系统生成的 IV
                iv = cipher.getIV();
            } else {
                // 普通密钥：可以使用自定义 IV
                Log.d(TAG, "使用普通密钥加密，生成自定义 IV");
                iv = new byte[GCM_IV_LENGTH];
                new SecureRandom().nextBytes(iv);

                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, spec);

                encrypted = cipher.doFinal(keyToEncrypt.getEncoded());
            }

            // 格式：base64(IV).base64(encryptedData)
            return android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP) + "." +
                   android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP);

        } catch (Exception e) {
            Log.e(TAG, "AES-GCM加密失败", e);
            return null;
        }
    }

    /**
     * 检查密钥是否来自 AndroidKeyStore
     *
     * @param key 待检查的密钥
     * @return 如果是 AndroidKeyStore 密钥返回 true
     */
    private boolean isAndroidKeyStoreKey(@NonNull SecretKey key) {
        // 方法1：检查密钥编码是否为 null（AndroidKeyStore 密钥无法导出）
        if (key.getEncoded() == null) {
            Log.d(TAG, "检测到 AndroidKeyStore 密钥（getEncoded() == null）");
            return true;
        }

        // 方法2：检查类名
        String className = key.getClass().getName();
        if (className.contains("AndroidKeyStore") || className.contains("keymaster")) {
            Log.d(TAG, "检测到 AndroidKeyStore 密钥（类名: " + className + "）");
            return true;
        }

        // 方法3：检查密钥格式
        String format = key.getFormat();
        if (format == null) {
            Log.d(TAG, "检测到 AndroidKeyStore 密钥（format == null）");
            return true;
        }

        Log.d(TAG, "检测到普通密钥（类名: " + className + ", 格式: " + format + "）");
        return false;
    }

    /**
     * 使用AES-GCM解密密钥
     *
     * @param encryptedData 加密数据（格式：base64(IV).base64(encryptedData)）
     * @param decryptionKey 解密密钥
     * @return 解密后的密钥
     * @throws SecurityException 解密失败时抛出
     */
    @NonNull
    private SecretKey decryptKeyWithAES(@NonNull String encryptedData,
                                         @NonNull SecretKey decryptionKey) {
        try {
            String[] parts = encryptedData.split("\\.");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted data format");
            }

            byte[] iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP);
            byte[] encryptedBytes = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(DATA_KEY_ENCRYPTION_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, decryptionKey, spec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new SecretKeySpec(decryptedBytes, "AES");

        } catch (javax.crypto.AEADBadTagException e) {
            Log.e(TAG, "解密失败：认证标签不匹配（可能是密钥错误或密文被篡改）", e);
            throw new SecurityException("Decryption failed: AEAD tag mismatch", e);
        } catch (Exception e) {
            Log.e(TAG, "AES-GCM解密失败", e);
            throw new SecurityException("Decryption failed", e);
        }
    }

    // ========== Level 2: DataKey解密 ==========

    /**
     * 使用DeviceKey解密DataKey（本地快速解锁）
     *
     * @param deviceKey DeviceKey（AndroidKeyStore）
     * @return DataKey
     * @throws SecurityException 解密失败时抛出
     */
    @NonNull
    public SecretKey decryptDataKeyWithDevice(@NonNull SecretKey deviceKey) {
        String encrypted = prefs.getString(DEVICE_ENCRYPTED_DATA_KEY, null);
        if (encrypted == null) {
            throw new IllegalStateException("未找到DeviceKey加密的DataKey");
        }
        return decryptKeyWithAES(encrypted, deviceKey);
    }

    /**
     * 使用 PasswordKey 解密 DataKey（跨设备恢复）- 使用 char[]
     *
     * 此方法在密钥派生完成后会自动清零密码字符数组（内存安全强化）
     *
     * @param masterPassword 主密码字符数组（会被自动清零）
     * @param saltBase64 盐值（Base64编码）
     * @return DataKey
     * @throws SecurityException 解密失败时抛出
     */
    @NonNull
    public SecretKey decryptDataKeyWithPassword(@NonNull char[] masterPassword,
                                                @NonNull String saltBase64) {
        try {
            String encrypted = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
            if (encrypted == null) {
                throw new IllegalStateException("未找到 PasswordKey 加密的 DataKey");
            }
            SecretKey passwordKey = derivePasswordKey(masterPassword, saltBase64);
            return decryptKeyWithAES(encrypted, passwordKey);
        } finally {
            // 确保密码被清零（derivePasswordKey 已经清零，这里作为双重保险）
            MemorySanitizer.secureWipe(masterPassword);
        }
    }

    /**
     * 使用 PasswordKey 解密 DataKey（跨设备恢复）- 使用 String
     *
     * 注意：此方法接受 String 参数，无法清零内存（String 不可变）。
     * 推荐使用 {@link #decryptDataKeyWithPassword(char[], String)} 版本以获得更好的内存安全性。
     *
     * @param masterPassword 主密码（String，无法清零）
     * @param saltBase64 盐值（Base64编码）
     * @return DataKey
     * @throws SecurityException 解密失败时抛出
     * @deprecated 使用 {@link #decryptDataKeyWithPassword(char[], String)} 替代
     */
    @Deprecated
    @NonNull
    public SecretKey decryptDataKeyWithPassword(@NonNull String masterPassword,
                                                @NonNull String saltBase64) {
        String encrypted = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
        if (encrypted == null) {
            throw new IllegalStateException("未找到 PasswordKey 加密的 DataKey");
        }
        SecretKey passwordKey = derivePasswordKey(masterPassword, saltBase64);
        Log.w(TAG, "使用 String 参数解密 DataKey（无法清零内存），推荐使用 char[] 版本");
        return decryptKeyWithAES(encrypted, passwordKey);
    }

    // ========== Level 3: RSA私钥加密存储 ==========

    /**
     * 加密并保存RSA私钥（Level 3 数据层）
     *
     * 使用DataKey加密RSA私钥，公钥明文存储
     * 使用commit()同步保存，确保密钥对完整性
     *
     * @param privateKey RSA私钥
     * @param dataKey DataKey
     * @param publicKey RSA公钥
     * @return 成功返回true，失败返回false
     */
    public boolean encryptAndSaveRsaPrivateKey(@NonNull PrivateKey privateKey,
                                                @NonNull SecretKey dataKey,
                                                @NonNull PublicKey publicKey) {
        try {
            // 1. 编码RSA私钥为PKCS8格式
            byte[] privateKeyBytes = privateKey.getEncoded();

            // 2. 使用DataKey加密
            String encryptedPrivateKey = encryptKeyWithAES(
                    new SecretKeySpec(privateKeyBytes, "AES"),
                    dataKey
            );
            if (encryptedPrivateKey == null) {
                Log.e(TAG, "RSA私钥加密失败");
                return false;
            }

            // 3. 编码公钥为X.509格式
            byte[] publicKeyBytes = publicKey.getEncoded();
            String publicKeyBase64 = android.util.Base64.encodeToString(
                    publicKeyBytes,
                    android.util.Base64.NO_WRAP
            );

            // 4. 使用commit()同步保存（原子性）
            boolean saved = prefs.edit()
                    .putString(ENCRYPTED_RSA_PRIVATE_KEY, encryptedPrivateKey)
                    .putString(RSA_PUBLIC_KEY, publicKeyBase64)
                    .putString(KEY_VERSION, KEY_VERSION_V3)
                    .commit();

            if (!saved) {
                Log.e(TAG, "RSA密钥对保存失败（commit返回false）");
                return false;
            }

            // 5. 验证密钥对完整性
            if (!validateKeyPair()) {
                Log.e(TAG, "RSA密钥对完整性验证失败");
                return false;
            }

            Log.i(TAG, "RSA私钥加密保存成功（三层架构v3）");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "RSA私钥加密存储失败", e);
            return false;
        }
    }

    /**
     * 解密RSA私钥（Level 3 数据层）
     *
     * 使用DataKey解密RSA私钥
     * 私钥字节在使用后会被立即擦除（Task 6.1: 私钥内存安全管理）
     *
     * @param dataKey DataKey
     * @return RSA私钥
     * @throws SecurityException 解密失败时抛出
     */
    @NonNull
    public PrivateKey decryptRsaPrivateKey(@NonNull SecretKey dataKey) {
        byte[] privateKeyBytes = null;
        try {
            String encrypted = prefs.getString(ENCRYPTED_RSA_PRIVATE_KEY, null);
            if (encrypted == null) {
                throw new IllegalStateException("未找到加密的RSA私钥");
            }

            // 解析并解密
            String[] parts = encrypted.split("\\.");
            byte[] iv = android.util.Base64.decode(parts[0], android.util.Base64.NO_WRAP);
            byte[] encryptedData = android.util.Base64.decode(parts[1], android.util.Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(DATA_KEY_ENCRYPTION_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, spec);

            privateKeyBytes = cipher.doFinal(encryptedData);

            // 重建PrivateKey
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            return keyFactory.generatePrivate(keySpec);

        } catch (javax.crypto.AEADBadTagException e) {
            Log.e(TAG, "RSA私钥解密失败：认证标签不匹配", e);
            throw new SecurityException("RSA private key decryption failed: AEAD tag mismatch", e);
        } catch (Exception e) {
            Log.e(TAG, "RSA私钥解密失败", e);
            throw new SecurityException("Failed to decrypt RSA private key", e);
        } finally {
            // Task 6.1: 私钥内存安全管理
            // 立即擦除内存中的私钥字节，防止内存转储攻击
            if (privateKeyBytes != null) {
                Arrays.fill(privateKeyBytes, (byte) 0);
                Log.d(TAG, "RSA私钥字节已从内存中擦除");
            }
        }
    }

    /**
     * 获取RSA公钥
     *
     * @return RSA公钥，未找到返回null
     */
    @Nullable
    public PublicKey getRsaPublicKey() {
        try {
            String publicKeyBase64 = prefs.getString(RSA_PUBLIC_KEY, null);
            if (publicKeyBase64 == null) {
                return null;
            }

            byte[] publicKeyBytes = android.util.Base64.decode(
                    publicKeyBase64,
                    android.util.Base64.NO_WRAP
            );

            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
            return keyFactory.generatePublic(keySpec);

        } catch (Exception e) {
            Log.e(TAG, "RSA公钥解析失败", e);
            return null;
        }
    }

    // ========== 云端备份 ==========

    /**
     * 创建云端备份包
     *
     * 返回的CloudBackup包含PasswordKey加密的DataKey
     * 可以安全地上传到云端，因为没有DeviceKey或明文私钥
     *
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码）
     * @return 云端备份对象
     */
    @Nullable
    public CloudBackup createCloudBackup(@NonNull String masterPassword,
                                          @NonNull String saltBase64) {
        try {
            String encryptedDataKey = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
            if (encryptedDataKey == null) {
                throw new IllegalStateException("未找到可备份的DataKey");
            }

            CloudBackup backup = new CloudBackup();
            backup.setEncryptedDataKey(encryptedDataKey);
            backup.setSalt(saltBase64);
            backup.setTimestamp(System.currentTimeMillis());
            backup.setVersion(KEY_VERSION_V3);

            Log.d(TAG, "云端备份包创建成功");
            return backup;

        } catch (Exception e) {
            Log.e(TAG, "云端备份创建失败", e);
            return null;
        }
    }

    /**
     * 从云端备份恢复DataKey
     *
     * @param backup 云端备份对象
     * @param masterPassword 主密码
     * @return DataKey
     * @throws SecurityException 恢复失败时抛出
     */
    @NonNull
    public SecretKey restoreFromCloudBackup(@NonNull CloudBackup backup,
                                             @NonNull String masterPassword) {
        try {
            SecretKey passwordKey = derivePasswordKey(masterPassword, backup.getSalt());
            return decryptKeyWithAES(backup.getEncryptedDataKey(), passwordKey);
        } catch (Exception e) {
            Log.e(TAG, "从云端备份恢复失败", e);
            throw new SecurityException("Failed to restore from cloud backup", e);
        }
    }

    // ========== 旧存储迁移 ==========
    // 注意：旧安全架构已完全移除（SafeVault 3.4.0），迁移方法已废弃

    /**
     * 从旧存储迁移密钥到三层安全架构
     *
     * 此方法已废弃，因为旧安全架构（KeyManager、CryptoManager）已完全移除。
     * SafeVault 3.4.0+ 统一使用三层安全架构（Argon2id + AES-GCM）。
     *
     * 迁移流程：
     * 1. 从旧KeyManager获取RSA密钥对（SharedPreferences明文存储）
     * 2. 派生PasswordKey（从主密码）
     * 3. 获取或创建DeviceKey（AndroidKeyStore）
     * 4. 生成随机DataKey
     * 5. 双重加密DataKey并保存
     * 6. 用DataKey加密RSA私钥并保存
     * 7. 创建云端备份
     * 8. 标记迁移完成
     *
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码）
     * @param oldPublicKeyBase64 旧公钥（Base64编码）
     * @param oldPrivateKeyBytes 旧私钥字节（PKCS8格式）
     * @return 迁移结果
     * @deprecated 旧安全架构已完全移除，新用户直接使用三层架构
     */
    @Deprecated
    @NonNull
    public MigrationResult migrateFromLegacy(@NonNull String masterPassword,
                                             @NonNull String saltBase64,
                                             @NonNull String oldPublicKeyBase64,
                                             @NonNull byte[] oldPrivateKeyBytes) {
        MigrationResult result = new MigrationResult();

        try {
            // 1. 解析旧私钥
            KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(oldPrivateKeyBytes);
            java.security.PrivateKey oldPrivateKey = keyFactory.generatePrivate(keySpec);
            if (oldPrivateKey == null) {
                return result.failure("No RSA private key found in legacy storage");
            }

            // 2. 派生PasswordKey
            SecretKey passwordKey = derivePasswordKey(masterPassword, saltBase64);

            // 3. 获取或创建DeviceKey
            SecretKey deviceKey = getOrCreateDeviceKey();
            if (deviceKey == null) {
                return result.failure("Failed to create DeviceKey in AndroidKeyStore");
            }

            // 4. 生成随机DataKey
            SecretKey dataKey = generateDataKey();

            // 5. 双重加密DataKey并保存
            if (!encryptAndSaveDataKey(dataKey, passwordKey, deviceKey)) {
                return result.failure("Failed to encrypt and save DataKey");
            }

            // 6. 解析公钥
            byte[] publicKeyBytes = android.util.Base64.decode(oldPublicKeyBase64, android.util.Base64.NO_WRAP);
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            java.security.PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            // 7. 用DataKey加密RSA私钥并保存
            if (!encryptAndSaveRsaPrivateKey(oldPrivateKey, dataKey, publicKey)) {
                return result.failure("Failed to encrypt and save RSA private key");
            }

            // 8. 标记迁移完成
            setMigrationStatus(MigrationStatus.COMPLETED);
            Log.i(TAG, "密钥迁移成功：从旧存储迁移到三层安全架构");

            // 9. 创建云端备份包（可选）
            CloudBackup backup = createCloudBackup(masterPassword, saltBase64);

            return result.success(backup);

        } catch (Exception e) {
            setMigrationStatus(MigrationStatus.FAILED);
            setMigrationError("Migration failed: " + e.getMessage());
            Log.e(TAG, "密钥迁移失败", e);
            return result.failure("Migration failed: " + e.getMessage());
        }
    }

    // ========== 完整性验证 ==========

    /**
     * 验证DataKey存储完整性
     *
     * @param requireDeviceKey 是否要求DeviceKey加密版本存在
     *                         true: 完整验证（双重加密）
     *                         false: 基础验证（仅PasswordKey加密）
     * @return 完整返回true，否则返回false
     */
    public boolean validateDataKeyStorage(boolean requireDeviceKey) {
        String passwordEncrypted = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
        String deviceEncrypted = prefs.getString(DEVICE_ENCRYPTED_DATA_KEY, null);

        // PasswordKey加密版本是必须的
        if (passwordEncrypted == null) {
            Log.w(TAG, "DataKey存储不完整（缺少PasswordKey加密数据）");
            return false;
        }

        // 验证PasswordKey格式
        if (!passwordEncrypted.contains(".")) {
            Log.w(TAG, "PasswordKey加密数据格式无效");
            return false;
        }

        // 如果要求DeviceKey加密版本，则验证
        if (requireDeviceKey) {
            if (deviceEncrypted == null) {
                Log.w(TAG, "DataKey存储不完整（缺少DeviceKey加密数据）");
                return false;
            }

            // 验证DeviceKey格式
            if (!deviceEncrypted.contains(".")) {
                Log.w(TAG, "DeviceKey加密数据格式无效");
                return false;
            }
        }

        return true;
    }

    /**
     * 验证DataKey存储完整性（默认要求完整验证）
     *
     * @return 完整返回true，否则返回false
     */
    public boolean validateDataKeyStorage() {
        return validateDataKeyStorage(true);
    }

    /**
     * 验证RSA密钥对完整性
     *
     * @return 完整返回true，否则返回false
     */
    public boolean validateKeyPair() {
        String pubKey = prefs.getString(RSA_PUBLIC_KEY, null);
        String privKey = prefs.getString(ENCRYPTED_RSA_PRIVATE_KEY, null);
        String version = prefs.getString(KEY_VERSION, null);

        if (pubKey == null || privKey == null || version == null) {
            Log.w(TAG, "RSA密钥对不完整");
            return false;
        }

        if (!KEY_VERSION_V3.equals(version)) {
            Log.w(TAG, "密钥版本不正确: " + version);
            return false;
        }

        return true;
    }

    /**
     * 检查是否已迁移到三层架构
     *
     * 迁移条件：
     * 1. RSA密钥对已创建
     * 2. DataKey已用PasswordKey加密（DeviceKey加密是可选的，需要生物识别认证）
     *
     * @return 已迁移返回true，否则返回false
     */
    public boolean isMigrated() {
        return validateKeyPair() && validateDataKeyStorage(false);
    }

    // ========== 迁移状态管理 ==========

    /**
     * 迁移状态枚举
     */
    public enum MigrationStatus {
        /** 未开始 */
        NOT_STARTED,
        /** 进行中 */
        IN_PROGRESS,
        /** 已完成 */
        COMPLETED,
        /** 已失败 */
        FAILED
    }

    /**
     * 设置迁移状态
     *
     * @param status 迁移状态
     */
    public void setMigrationStatus(@NonNull MigrationStatus status) {
        prefs.edit()
                .putString(MIGRATION_STATUS, status.name())
                .putLong(MIGRATION_TIMESTAMP, System.currentTimeMillis())
                .commit();
        Log.i(TAG, "迁移状态更新: " + status.name());
    }

    /**
     * 获取迁移状态
     *
     * @return 迁移状态
     */
    @NonNull
    public MigrationStatus getMigrationStatus() {
        String status = prefs.getString(MIGRATION_STATUS, MigrationStatus.NOT_STARTED.name());
        try {
            return MigrationStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return MigrationStatus.NOT_STARTED;
        }
    }

    /**
     * 获取迁移时间戳
     *
     * @return 迁移时间戳（毫秒），未迁移返回0
     */
    public long getMigrationTimestamp() {
        return prefs.getLong(MIGRATION_TIMESTAMP, 0);
    }

    /**
     * 设置迁移错误消息
     *
     * @param error 错误消息
     */
    public void setMigrationError(@NonNull String error) {
        prefs.edit()
                .putString(MIGRATION_ERROR, error)
                .commit();
    }

    /**
     * 获取迁移错误消息
     *
     * @return 错误消息，无错误返回null
     */
    @Nullable
    public String getMigrationError() {
        return prefs.getString(MIGRATION_ERROR, null);
    }

    // ========== 内部类：迁移结果 ==========

    /**
     * 密钥迁移结果
     */
    public static class MigrationResult {
        private boolean success;
        private String errorMessage;
        private CloudBackup backup;

        /**
         * 创建成功结果
         *
         * @param backup 云端备份（可选）
         * @return 成功的MigrationResult
         */
        @NonNull
        public static MigrationResult success(@Nullable CloudBackup backup) {
            MigrationResult result = new MigrationResult();
            result.success = true;
            result.backup = backup;
            return result;
        }

        /**
         * 创建失败结果
         *
         * @param error 错误消息
         * @return 失败的MigrationResult
         */
        @NonNull
        public static MigrationResult failure(@NonNull String error) {
            MigrationResult result = new MigrationResult();
            result.success = false;
            result.errorMessage = error;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        @Nullable
        public CloudBackup getBackup() {
            return backup;
        }
    }

    // ========== 内部类：云端备份 ==========

    /**
     * 云端备份数据
     *
     * 只包含PasswordKey加密的DataKey
     * 不包含DeviceKey或明文私钥，确保安全
     */
    public static class CloudBackup {
        private String encryptedDataKey;
        private String salt;
        private long timestamp;
        private String version;

        @NonNull
        public String getEncryptedDataKey() {
            return encryptedDataKey;
        }

        public void setEncryptedDataKey(@NonNull String encryptedDataKey) {
            this.encryptedDataKey = encryptedDataKey;
        }

        @NonNull
        public String getSalt() {
            return salt;
        }

        public void setSalt(@NonNull String salt) {
            this.salt = salt;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(long timestamp) {
            this.timestamp = timestamp;
        }

        @NonNull
        public String getVersion() {
            return version;
        }

        public void setVersion(@NonNull String version) {
            this.version = version;
        }
    }

    // ========== 生物识别解锁（统一使用三层架构） ==========

    /**
     * 使用生物识别解锁（直接使用三层架构）
     *
     * 解锁流程：
     * 1. 获取DeviceKey（会触发生物识别认证，30秒有效期）
     * 2. 使用DeviceKey解密DataKey
     * 3. 使用DataKey解密RSA私钥
     * 4. 返回PrivateKey供CryptoManager初始化
     *
     * 注意：此方法不再需要存储/解密主密码，完全基于三层架构
     *
     * @return RSA私钥，解锁失败返回null
     */
    @Nullable
    public PrivateKey unlockWithBiometric() {
        try {
            // 1. 获取DeviceKey（需要生物识别认证）
            SecretKey deviceKey = getOrCreateDeviceKey();
            if (deviceKey == null) {
                Log.e(TAG, "生物识别解锁失败：无法获取DeviceKey");
                return null;
            }

            // 2. 使用DeviceKey解密DataKey
            SecretKey dataKey = decryptDataKeyWithDevice(deviceKey);

            // 3. 使用DataKey解密RSA私钥
            PrivateKey privateKey = decryptRsaPrivateKey(dataKey);

            Log.i(TAG, "生物识别解锁成功（三层架构）");
            return privateKey;

        } catch (SecurityException e) {
            Log.e(TAG, "生物识别解锁失败：" + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "生物识别解锁异常", e);
            return null;
        }
    }

    /**
     * 使用生物识别解锁并返回 DataKey（用于会话恢复）
     *
     * 解锁流程：
     * 1. 获取DeviceKey（需要生物识别认证，30秒有效期）
     * 2. 使用DeviceKey解密DataKey
     * 3. 返回DataKey供CryptoSession缓存
     *
     * 此方法用于生物识别登录后的会话恢复，将 DataKey 缓存到 CryptoSession 中，
     * 使得后续的加密解密操作可以正常进行。
     *
     * 注意：调用此方法前，用户必须已经通过生物识别UI认证（由 BiometricAuthManager 触发）
     *
     * @return DataKey（SecretKey），解锁失败返回null
     */
    @Nullable
    public SecretKey unlockDataKeyWithBiometric() {
        try {
            Log.d(TAG, "unlockDataKeyWithBiometric() 被调用");

            // 1. 获取DeviceKey（需要生物识别认证）
            SecretKey deviceKey = getOrCreateDeviceKey();
            if (deviceKey == null) {
                Log.e(TAG, "生物识别解锁失败：无法获取DeviceKey");
                return null;
            }

            // 2. 使用DeviceKey解密DataKey
            SecretKey dataKey = decryptDataKeyWithDevice(deviceKey);

            Log.i(TAG, "生物识别解锁成功（DataKey已恢复）");
            return dataKey;

        } catch (SecurityException e) {
            Log.e(TAG, "生物识别解锁失败：" + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "生物识别解锁异常", e);
            return null;
        }
    }

    /**
     * 检查DeviceKey是否存在
     *
     * @return 存在返回true，否则返回false
     */
    public boolean hasDeviceKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            return keyStore.containsAlias(DEVICE_KEY_ALIAS);
        } catch (Exception e) {
            Log.e(TAG, "检查DeviceKey失败", e);
            return false;
        }
    }

    /**
     * 检查加密的DataKey是否存在
     *
     * @return 存在返回true，否则返回false
     */
    public boolean hasEncryptedDataKey() {
        String passwordEncrypted = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
        String deviceEncrypted = prefs.getString(DEVICE_ENCRYPTED_DATA_KEY, null);
        return passwordEncrypted != null && deviceEncrypted != null;
    }

    /**
     * 检查 PasswordKey 加密的 DataKey 是否存在
     *
     * 用于启用生物识别时检查是否有必要的数据
     *
     * @return 存在返回true，否则返回false
     */
    public boolean hasPasswordEncryptedDataKey() {
        String passwordEncrypted = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
        return passwordEncrypted != null;
    }

    /**
     * 检查 DeviceKey 加密的 DataKey 是否存在
     *
     * 用于使用生物识别解锁时检查生物识别路径是否完整
     *
     * @return 存在返回true，否则返回false
     */
    public boolean hasDeviceEncryptedDataKey() {
        String deviceEncrypted = prefs.getString(DEVICE_ENCRYPTED_DATA_KEY, null);
        return deviceEncrypted != null;
    }

    /**
     * 清除生物识别相关数据
     *
     * 注意：此方法只清除DeviceKey，不影响PasswordKey和DataKey
     * 用户仍可使用主密码解锁
     */
    public void clearBiometricData() {
        // 清除DeviceKey加密的DataKey（保留PasswordKey加密的版本用于云端备份）
        prefs.edit().remove(DEVICE_ENCRYPTED_DATA_KEY).commit();

        // 删除AndroidKeyStore中的DeviceKey
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(DEVICE_KEY_ALIAS)) {
                keyStore.deleteEntry(DEVICE_KEY_ALIAS);
                Log.i(TAG, "DeviceKey已删除，生物识别解锁已禁用");
            }
        } catch (Exception e) {
            Log.e(TAG, "删除DeviceKey失败", e);
        }
    }

    /**
     * 完成生物识别注册（DeviceKey加密路径初始化）
     *
     * 在用户首次启用生物识别时调用。此时：
     * - 用户已通过生物识别UI认证，DeviceKey已获得授权
     * - PasswordKey加密的DataKey已存在
     * - 需要使用DeviceKey重新加密DataKey并保存
     *
     * @param masterPassword 主密码（用于派生PasswordKey并解密DataKey）
     * @param saltBase64 盐值
     * @return 成功返回true，失败返回false
     */
    public boolean completeBiometricEnrollment(@NonNull String masterPassword,
                                              @NonNull String saltBase64) {
        try {
            Log.d(TAG, "开始完成生物识别注册（DeviceKey加密路径初始化）");

            // 1. 获取DeviceKey（此时已通过生物识别认证，可用）
            SecretKey deviceKey = getOrCreateDeviceKey();
            if (deviceKey == null) {
                Log.e(TAG, "生物识别注册失败：无法获取DeviceKey");
                return false;
            }

            // 2. 检查PasswordKey加密的DataKey是否存在
            String passwordEncryptedDataKey = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
            if (passwordEncryptedDataKey == null) {
                Log.e(TAG, "生物识别注册失败：PasswordKey加密的DataKey不存在");
                return false;
            }

            // 3. 派生PasswordKey
            SecretKey passwordKey = derivePasswordKey(masterPassword, saltBase64);

            // 4. 使用PasswordKey解密DataKey
            SecretKey dataKey = decryptKeyWithAES(passwordEncryptedDataKey, passwordKey);

            // 5. 使用DeviceKey重新加密DataKey
            String deviceEncrypted = encryptKeyWithAES(dataKey, deviceKey);
            if (deviceEncrypted == null) {
                Log.e(TAG, "生物识别注册失败：DeviceKey加密DataKey失败");
                return false;
            }

            // 6. 保存DeviceKey加密的DataKey
            boolean saved = prefs.edit()
                    .putString(DEVICE_ENCRYPTED_DATA_KEY, deviceEncrypted)
                    .commit();

            if (!saved) {
                Log.e(TAG, "生物识别注册失败：保存DeviceKey加密DataKey失败");
                return false;
            }

            // 7. 验证完整性
            if (!validateDataKeyStorage(true)) {
                Log.e(TAG, "生物识别注册失败：DataKey存储完整性验证失败");
                return false;
            }

            Log.i(TAG, "生物识别注册成功（DeviceKey加密路径已初始化）");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "生物识别注册异常", e);
            return false;
        }
    }

    /**
     * 完成生物识别注册（使用会话中的 DataKey）
     *
     * 当用户已通过主密码验证（DataKey 在内存中）时调用此方法
     * 无需重新派生 PasswordKey 和解密 DataKey，直接使用会话中的 DataKey
     *
     * @param dataKey 会话中的 DataKey（已解密）
     * @param saltBase64 盐值（用于验证，不使用）
     * @return 成功返回true，失败返回false
     */
    public boolean completeBiometricEnrollmentWithDataKey(@NonNull SecretKey dataKey,
                                                         @NonNull String saltBase64) {
        try {
            Log.d(TAG, "开始完成生物识别注册（使用会话中的 DataKey）");

            // 1. 获取DeviceKey（此时已通过生物识别认证，可用）
            SecretKey deviceKey = getOrCreateDeviceKey();
            if (deviceKey == null) {
                Log.e(TAG, "生物识别注册失败：无法获取DeviceKey");
                return false;
            }

            // 2. 检查PasswordKey加密的DataKey是否存在（验证完整性）
            String passwordEncryptedDataKey = prefs.getString(PASSWORD_ENCRYPTED_DATA_KEY, null);
            if (passwordEncryptedDataKey == null) {
                Log.e(TAG, "生物识别注册失败：PasswordKey加密的DataKey不存在");
                return false;
            }

            // 3. 直接使用提供的DataKey，无需重新解密
            Log.d(TAG, "使用会话中的 DataKey，跳过 PasswordKey 派生和解密步骤");

            // 4. 使用DeviceKey重新加密DataKey
            String deviceEncrypted = encryptKeyWithAES(dataKey, deviceKey);
            if (deviceEncrypted == null) {
                Log.e(TAG, "生物识别注册失败：DeviceKey加密DataKey失败");
                return false;
            }

            // 5. 保存DeviceKey加密的DataKey
            boolean saved = prefs.edit()
                    .putString(DEVICE_ENCRYPTED_DATA_KEY, deviceEncrypted)
                    .commit();

            if (!saved) {
                Log.e(TAG, "生物识别注册失败：保存DeviceKey加密DataKey失败");
                return false;
            }

            // 6. 验证完整性
            if (!validateDataKeyStorage(true)) {
                Log.e(TAG, "生物识别注册失败：DataKey存储完整性验证失败");
                return false;
            }

            Log.i(TAG, "生物识别注册成功（使用会话中的 DataKey，跳过主密码派生）");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "生物识别注册异常（使用会话 DataKey）", e);
            return false;
        }
    }

    /**
     * 重新生成DeviceKey（用于生物识别重置）
     *
     * 注意：调用后需要重新调用encryptAndSaveDataKey()来保存新的DeviceKey加密版本
     *
     * @return 新的DeviceKey，失败返回null
     */
    @Nullable
    public SecretKey regenerateDeviceKey() {
        try {
            // 先删除旧的DeviceKey
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(DEVICE_KEY_ALIAS)) {
                keyStore.deleteEntry(DEVICE_KEY_ALIAS);
                Log.i(TAG, "旧DeviceKey已删除");
            }

            // 生成新的DeviceKey
            return getOrCreateDeviceKey();
        } catch (Exception e) {
            Log.e(TAG, "重新生成DeviceKey失败", e);
            return null;
        }
    }

    // ========== 工具方法：清除所有数据 ==========

    /**
     * 清除所有安全存储数据
     *
     * 警告：此操作不可逆！
     */
    public void clearAll() {
        prefs.edit()
                .remove(PASSWORD_ENCRYPTED_DATA_KEY)
                .remove(DEVICE_ENCRYPTED_DATA_KEY)
                .remove(ENCRYPTED_RSA_PRIVATE_KEY)
                .remove(RSA_PUBLIC_KEY)
                .remove(KEY_VERSION)
                .remove(MIGRATION_STATUS)
                .remove(MIGRATION_TIMESTAMP)
                .remove(MIGRATION_ERROR)
                .commit();

        // 清除AndroidKeyStore中的DeviceKey
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(DEVICE_KEY_ALIAS)) {
                keyStore.deleteEntry(DEVICE_KEY_ALIAS);
                Log.i(TAG, "DeviceKey已从AndroidKeyStore删除");
            }
        } catch (Exception e) {
            Log.e(TAG, "删除DeviceKey失败", e);
        }

        Log.w(TAG, "所有安全存储数据已清除");
    }
}
