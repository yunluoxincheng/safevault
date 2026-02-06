package com.ttt.safevault.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 密钥管理器
 * 负责生成和存储RSA密钥对，以及主密码派生密钥
 *
 * ⚠️ 安全警告：当前RSA私钥以Base64明文形式存储在SharedPreferences中
 * TODO: 第二阶段需要迁移到AndroidKeyStore（见proposal.md）
 */
public class KeyManager {
    private static final String TAG = "KeyManager";
    private static final String PREFS_NAME = "key_prefs";
    private static final String KEY_PUBLIC_KEY = "public_key";
    private static final String KEY_PRIVATE_KEY = "private_key";
    private static final String KEY_DEVICE_ID = "device_id";

    // 密钥迁移状态标记（为第二阶段迁移做准备）
    private static final String KEY_MIGRATION_STATUS = "key_migration_status";
    private static final String KEY_STORAGE_TYPE = "key_storage_type"; // "LEGACY" or "ANDROID_KEYSTORE"

    /**
     * 密钥存储类型枚举
     */
    public enum KeyStorageType {
        /**
         * 旧式存储：SharedPreferences明文存储（不安全）
         * ⚠️ 安全风险：私钥可被root设备或备份应用读取
         */
        LEGACY,

        /**
         * 新式存储：AndroidKeyStore硬件保护存储（安全）
         * ✅ 安全特性：私钥受硬件保护，导出受限
         */
        ANDROID_KEYSTORE
    }

    // 密钥派生相关常量
    private static final int PBKDF2_ITERATIONS = 100000;  // PBKDF2 迭代次数
    private static final int DERIVED_KEY_LENGTH = 256;    // 派生密钥长度（位）
    private static final int SALT_LENGTH = 32;            // 盐值长度（字节）
    private static final int IV_LENGTH = 16;              // IV长度（字节）

    // 密钥派生相关存储键
    private static final String KEY_DERIVED_KEY_SALT = "derived_key_salt";
    private static final String KEY_ENCRYPTED_PRIVATE_KEY = "encrypted_private_key";
    private static final String KEY_PRIVATE_KEY_IV = "private_key_iv";

    private static volatile KeyManager INSTANCE;
    private final Context context;
    private final SharedPreferences prefs;
    private KeyPair keyPair;
    private String deviceId;
    private SecureKeyStorageManager secureStorage;

    private KeyManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.secureStorage = SecureKeyStorageManager.getInstance(context);
        loadOrGenerateKeys();
        loadOrGenerateDeviceId();
    }

    public static KeyManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (KeyManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new KeyManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 加载或生成密钥对
     */
    private void loadOrGenerateKeys() {
        String publicKeyStr = prefs.getString(KEY_PUBLIC_KEY, null);
        String privateKeyStr = prefs.getString(KEY_PRIVATE_KEY, null);

        if (publicKeyStr != null && privateKeyStr != null) {
            // 加载已保存的密钥
            try {
                this.keyPair = loadKeyPair(publicKeyStr, privateKeyStr);
                Log.d(TAG, "Loaded existing key pair");

                // 检查并标记密钥存储类型（为迁移做准备）
                KeyStorageType storageType = detectKeyStorageType();
                if (storageType == KeyStorageType.LEGACY) {
                    Log.w(TAG, "⚠️ [SECURITY] Keys stored in LEGACY format (SharedPreferences)");
                    Log.w(TAG, "⚠️ [SECURITY] Keys should be migrated to AndroidKeyStore (Phase 2)");
                    recordKeyStorageStatus(KeyStorageType.LEGACY);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load keys, generating new ones", e);
                generateAndSaveKeys();
            }
        } else {
            // 生成新密钥
            generateAndSaveKeys();
        }
    }

    /**
     * 加载或生成设备ID
     */
    private void loadOrGenerateDeviceId() {
        this.deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (this.deviceId == null) {
            this.deviceId = generateDeviceId();
            prefs.edit().putString(KEY_DEVICE_ID, this.deviceId).apply();
            Log.d(TAG, "Generated new device ID: " + deviceId);
        } else {
            Log.d(TAG, "Loaded existing device ID: " + deviceId);
        }
    }

    /**
     * 生成新的RSA密钥对
     */
    private void generateAndSaveKeys() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            this.keyPair = keyGen.generateKeyPair();

            // 保存密钥
            String publicKeyStr = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKeyStr = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());

            prefs.edit()
                    .putString(KEY_PUBLIC_KEY, publicKeyStr)
                    .putString(KEY_PRIVATE_KEY, privateKeyStr)
                    .apply();

            Log.d(TAG, "Generated and saved new key pair");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Failed to generate key pair", e);
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }

    /**
     * 生成设备ID
     */
    private String generateDeviceId() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * 从字符串加载密钥对
     */
    private KeyPair loadKeyPair(String publicKeyStr, String privateKeyStr) throws Exception {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);

            // 使用 KeyFactory 重建密钥
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            // 重建公钥 (X509 编码)
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
            PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

            // 重建私钥 (PKCS8 编码)
            PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load key pair, generating new ones", e);
            throw e;
        }
    }

    /**
     * 获取公钥（Base64编码）
     */
    public String getPublicKey() {
        if (keyPair != null && keyPair.getPublic() != null) {
            return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        }
        return null;
    }

    /**
     * 获取私钥
     */
    public PrivateKey getPrivateKey() {
        if (keyPair != null) {
            return keyPair.getPrivate();
        }
        return null;
    }

    // ========== 三层安全存储集成（第二阶段） ==========

    /**
     * 检查是否已迁移到三层安全存储
     *
     * @return 已迁移返回true，否则返回false
     */
    public boolean isMigratedToSecureStorage() {
        return secureStorage != null && secureStorage.isMigrated();
    }

    /**
     * 获取当前密钥存储类型
     *
     * @return 密钥存储类型
     */
    @NonNull
    public KeyStorageType getCurrentKeyStorageType() {
        if (isMigratedToSecureStorage()) {
            return KeyStorageType.ANDROID_KEYSTORE;
        }
        return detectKeyStorageType();
    }

    /**
     * 从三层安全存储获取私钥（使用DeviceKey）
     *
     * 注意：此方法需要生物识别认证，调用方需要处理UI交互
     *
     * @param deviceKey DeviceKey（从生物识别认证获取）
     * @return RSA私钥
     * @throws SecurityException 解密失败时抛出
     */
    @NonNull
    public PrivateKey getPrivateKeyFromSecureStorage(@NonNull javax.crypto.SecretKey deviceKey) {
        if (!isMigratedToSecureStorage()) {
            throw new IllegalStateException("未迁移到三层安全存储");
        }

        try {
            // DeviceKey解密DataKey
            javax.crypto.SecretKey dataKey = secureStorage.decryptDataKeyWithDevice(deviceKey);
            // DataKey解密RSA私钥
            return secureStorage.decryptRsaPrivateKey(dataKey);
        } catch (Exception e) {
            Log.e(TAG, "从安全存储获取私钥失败", e);
            throw new SecurityException("Failed to get private key from secure storage", e);
        }
    }

    /**
     * 从三层安全存储获取私钥（使用主密码）
     *
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码）
     * @return RSA私钥
     * @throws SecurityException 解密失败时抛出
     */
    @NonNull
    public PrivateKey getPrivateKeyFromSecureStorage(@NonNull String masterPassword,
                                                     @NonNull String saltBase64) {
        if (!isMigratedToSecureStorage()) {
            throw new IllegalStateException("未迁移到三层安全存储");
        }

        try {
            // PasswordKey解密DataKey
            javax.crypto.SecretKey dataKey = secureStorage.decryptDataKeyWithPassword(masterPassword, saltBase64);
            // DataKey解密RSA私钥
            return secureStorage.decryptRsaPrivateKey(dataKey);
        } catch (Exception e) {
            Log.e(TAG, "从安全存储获取私钥失败", e);
            throw new SecurityException("Failed to get private key from secure storage", e);
        }
    }

    /**
     * 从三层安全存储获取公钥
     *
     * @return RSA公钥（Base64编码），未找到返回null
     */
    @Nullable
    public String getPublicKeyFromSecureStorage() {
        if (!isMigratedToSecureStorage()) {
            return null;
        }

        try {
            java.security.PublicKey publicKey = secureStorage.getRsaPublicKey();
            if (publicKey != null) {
                return Base64.getEncoder().encodeToString(publicKey.getEncoded());
            }
        } catch (Exception e) {
            Log.e(TAG, "从安全存储获取公钥失败", e);
        }
        return null;
    }

    /**
     * 检查并执行迁移到三层安全存储
     *
     * 此方法应在应用启动时调用，如果检测到旧存储则会自动迁移
     *
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码）
     * @return 迁移结果
     */
    @NonNull
    public SecureKeyStorageManager.MigrationResult checkAndMigrate(@NonNull String masterPassword,
                                                                    @NonNull String saltBase64) {
        // 如果已迁移，直接返回成功
        if (isMigratedToSecureStorage()) {
            Log.d(TAG, "已迁移到三层安全存储，跳过迁移");
            SecureKeyStorageManager.MigrationResult result = new SecureKeyStorageManager.MigrationResult();
            // 创建一个成功结果（没有备份）
            return SecureKeyStorageManager.MigrationResult.success(null);
        }

        // 如果没有旧密钥，不需要迁移
        if (keyPair == null || keyPair.getPrivate() == null) {
            Log.d(TAG, "没有旧密钥，跳过迁移");
            SecureKeyStorageManager.MigrationResult result = new SecureKeyStorageManager.MigrationResult();
            return SecureKeyStorageManager.MigrationResult.success(null);
        }

        // 执行迁移
        Log.i(TAG, "开始迁移密钥到三层安全存储...");
        return secureStorage.migrateFromLegacy(this, masterPassword, saltBase64);
    }

    /**
     * 获取SecureKeyStorageManager实例
     *
     * @return SecureKeyStorageManager实例
     */
    @NonNull
    public SecureKeyStorageManager getSecureStorage() {
        return secureStorage;
    }

    /**
     * 获取设备ID
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * 清除密钥（用于重置）
     */
    public void clearKeys() {
        prefs.edit()
                .remove(KEY_PUBLIC_KEY)
                .remove(KEY_PRIVATE_KEY)
                .apply();
        keyPair = null;
        generateAndSaveKeys();
        Log.d(TAG, "Cleared and regenerated keys");
    }

    // ========== 密钥派生相关方法 ==========

    /**
     * 从主密码派生密钥（PBKDF2WithHmacSHA256）
     *
     * @param masterPassword 主密码
     * @param salt           盐值（Base64编码）
     * @return 派生密钥（用于AES加密）
     */
    public SecretKey deriveKeyFromMasterPassword(String masterPassword, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(
                    masterPassword.toCharArray(),
                    saltBytes,
                    PBKDF2_ITERATIONS,
                    DERIVED_KEY_LENGTH
            );

            byte[] derivedKeyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(derivedKeyBytes, "AES");
        } catch (Exception e) {
            Log.e(TAG, "Failed to derive key from master password", e);
            throw new RuntimeException("密钥派生失败", e);
        }
    }

    /**
     * 生成用户特定的盐值
     *
     * @param email 用户邮箱
     * @return Base64编码的盐值
     */
    public String generateSaltForUser(String email) {
        try {
            // 使用邮箱和时间戳生成唯一盐值
            String input = email + System.currentTimeMillis();
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            // 可以将邮箱信息混入盐值，增加用户特定性
            byte[] emailBytes = email.getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < salt.length && i < emailBytes.length; i++) {
                salt[i] ^= emailBytes[i];
            }

            return Base64.getEncoder().encodeToString(salt);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate salt", e);
            throw new RuntimeException("盐值生成失败", e);
        }
    }

    /**
     * 保存用户的派生密钥盐值
     *
     * @param email 用户邮箱
     * @param salt  盐值（Base64编码）
     */
    public void saveUserSalt(String email, String salt) {
        prefs.edit()
                .putString(KEY_DERIVED_KEY_SALT + "_" + email, salt)
                .apply();
        Log.d(TAG, "Saved salt for user: " + email);
    }

    /**
     * 获取用户的派生密钥盐值
     *
     * @param email 用户邮箱
     * @return 盐值（Base64编码），如果不存在返回null
     */
    public String getUserSalt(String email) {
        return prefs.getString(KEY_DERIVED_KEY_SALT + "_" + email, null);
    }

    /**
     * 生成随机IV
     *
     * @return Base64编码的IV
     */
    public String generateRandomIV() {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
    }

    /**
     * 使用派生密钥加密数据
     *
     * @param data      要加密的数据
     * @param derivedKey 派生密钥
     * @param iv        初始化向量（Base64编码）
     * @return Base64编码的加密数据
     */
    public String encryptWithDerivedKey(byte[] data, SecretKey derivedKey, String iv) {
        try {
            byte[] ivBytes = Base64.getDecoder().decode(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.ENCRYPT_MODE, derivedKey, ivSpec);

            byte[] encrypted = cipher.doFinal(data);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt with derived key", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 使用派生密钥解密数据
     *
     * @param encryptedData Base64编码的加密数据
     * @param derivedKey    派生密钥
     * @param iv            初始化向量（Base64编码）
     * @return 解密后的数据
     */
    public byte[] decryptWithDerivedKey(String encryptedData, SecretKey derivedKey, String iv) {
        try {
            byte[] ivBytes = Base64.getDecoder().decode(iv);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedData);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, derivedKey, ivSpec);

            return cipher.doFinal(encryptedBytes);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt with derived key", e);
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 加密私钥（用于上传到云端）
     *
     * @param privateKey  RSA私钥
     * @param masterPassword 主密码
     * @param email       用户邮箱
     * @return 加密结果对象，包含加密后的私钥、IV和盐值
     */
    public EncryptedPrivateKey encryptPrivateKey(PrivateKey privateKey, String masterPassword, String email) {
        try {
            // 1. 生成或获取用户盐值
            String salt = getUserSalt(email);
            if (salt == null) {
                salt = generateSaltForUser(email);
                saveUserSalt(email, salt);
            }

            // 2. 从主密码派生密钥
            SecretKey derivedKey = deriveKeyFromMasterPassword(masterPassword, salt);

            // 3. 生成随机IV
            String iv = generateRandomIV();

            // 4. 加密私钥
            byte[] privateKeyBytes = privateKey.getEncoded();
            String encryptedPrivateKey = encryptWithDerivedKey(privateKeyBytes, derivedKey, iv);

            // 5. 保存加密的私钥和IV到本地（用于后续解密）
            prefs.edit()
                    .putString(KEY_ENCRYPTED_PRIVATE_KEY, encryptedPrivateKey)
                    .putString(KEY_PRIVATE_KEY_IV, iv)
                    .apply();

            Log.d(TAG, "Private key encrypted for user: " + email);
            return new EncryptedPrivateKey(encryptedPrivateKey, iv, salt);
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt private key", e);
            throw new RuntimeException("私钥加密失败", e);
        }
    }

    /**
     * 解密私钥（从云端下载，使用提供的盐值）
     *
     * @param encryptedPrivateKey Base64编码的加密私钥
     * @param masterPassword      主密码
     * @param salt                盐值（Base64编码）- 从云端获取
     * @param iv                  初始化向量（Base64编码）
     * @return 解密后的RSA私钥
     */
    public PrivateKey decryptPrivateKey(String encryptedPrivateKey, String masterPassword, String salt, String iv) {
        try {
            // 1. 保存用户盐值到本地（供后续使用）
            saveUserSalt("current", salt);

            // 2. 从主密码派生密钥
            SecretKey derivedKey = deriveKeyFromMasterPassword(masterPassword, salt);

            // 3. 解密私钥
            byte[] privateKeyBytes = decryptWithDerivedKey(encryptedPrivateKey, derivedKey, iv);

            // 4. 重建RSA私钥对象
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
            PrivateKey privateKey = keyFactory.generatePrivate(spec);

            // 5. 更新内存中的密钥对
            if (this.keyPair != null) {
                // 保留公钥，更新私钥
                this.keyPair = new KeyPair(this.keyPair.getPublic(), privateKey);
            }

            // 6. 保存到本地存储
            String privateKeyStr = Base64.getEncoder().encodeToString(privateKey.getEncoded());
            String publicKeyStr = Base64.getEncoder().encodeToString(this.keyPair.getPublic().getEncoded());
            prefs.edit()
                    .putString(KEY_PUBLIC_KEY, publicKeyStr)
                    .putString(KEY_PRIVATE_KEY, privateKeyStr)
                    .apply();

            // 7. 保存加密的私钥和IV到本地（用于后续解密）
            prefs.edit()
                    .putString(KEY_ENCRYPTED_PRIVATE_KEY, encryptedPrivateKey)
                    .putString(KEY_PRIVATE_KEY_IV, iv)
                    .apply();

            Log.d(TAG, "Private key decrypted and imported successfully");
            return privateKey;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt private key", e);
            throw new RuntimeException("私钥解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证主密码是否正确（通过尝试解密私钥）
     *
     * @param masterPassword 主密码
     * @param email          用户邮箱
     * @return 密码是否正确
     */
    public boolean verifyMasterPassword(String masterPassword, String email) {
        try {
            String encryptedPrivateKey = prefs.getString(KEY_ENCRYPTED_PRIVATE_KEY, null);
            String iv = prefs.getString(KEY_PRIVATE_KEY_IV, null);
            String salt = getUserSalt(email);

            if (encryptedPrivateKey == null || iv == null || salt == null) {
                return false;
            }

            // 尝试解密，如果成功则密码正确
            decryptPrivateKey(encryptedPrivateKey, masterPassword, salt, iv);
            return true;
        } catch (Exception e) {
            Log.d(TAG, "Master password verification failed", e);
            return false;
        }
    }

    /**
     * 清除用户的密钥派生数据
     *
     * @param email 用户邮箱
     */
    public void clearUserDerivedKeyData(String email) {
        prefs.edit()
                .remove(KEY_DERIVED_KEY_SALT + "_" + email)
                .remove(KEY_ENCRYPTED_PRIVATE_KEY)
                .remove(KEY_PRIVATE_KEY_IV)
                .apply();
        Log.d(TAG, "Cleared derived key data for user: " + email);
    }

    /**
     * 加密后的私钥封装类
     */
    public static class EncryptedPrivateKey {
        private final String encryptedData;
        private final String iv;
        private final String salt;

        public EncryptedPrivateKey(String encryptedData, String iv, String salt) {
            this.encryptedData = encryptedData;
            this.iv = iv;
            this.salt = salt;
        }

        public String getEncryptedData() {
            return encryptedData;
        }

        public String getIv() {
            return iv;
        }

        public String getSalt() {
            return salt;
        }
    }

    // ========== 密钥迁移相关方法（第二阶段准备） ==========

    /**
     * 检测当前密钥存储类型
     *
     * @return 密钥存储类型（LEGACY 或 ANDROID_KEYSTORE）
     */
    public KeyStorageType detectKeyStorageType() {
        // 检查是否已记录存储类型
        String recordedType = prefs.getString(KEY_STORAGE_TYPE, null);
        if (recordedType != null) {
            return KeyStorageType.valueOf(recordedType);
        }

        // 检测当前存储方式
        String publicKeyStr = prefs.getString(KEY_PUBLIC_KEY, null);
        String privateKeyStr = prefs.getString(KEY_PRIVATE_KEY, null);

        if (publicKeyStr != null && privateKeyStr != null) {
            // 密钥存储在SharedPreferences中 = LEGACY
            Log.w(TAG, "[KeyMigration] Detected LEGACY key storage (SharedPreferences)");
            return KeyStorageType.LEGACY;
        }

        // 无密钥存储
        Log.d(TAG, "[KeyMigration] No keys stored yet");
        return null;
    }

    /**
     * 记录密钥存储状态到SharedPreferences
     *
     * @param storageType 密钥存储类型
     */
    private void recordKeyStorageStatus(KeyStorageType storageType) {
        if (storageType == null) {
            return;
        }

        // 只记录一次，避免重复日志
        String lastRecorded = prefs.getString(KEY_STORAGE_TYPE, null);
        if (storageType.name().equals(lastRecorded)) {
            return;
        }

        prefs.edit()
                .putString(KEY_STORAGE_TYPE, storageType.name())
                .putLong(KEY_MIGRATION_STATUS, System.currentTimeMillis())
                .apply();

        Log.i(TAG, "[KeyMigration] Key storage status recorded: " + storageType.name());
        Log.i(TAG, "[KeyMigration] Status recorded at: " + new java.util.Date());

        // 输出迁移建议
        if (storageType == KeyStorageType.LEGACY) {
            Log.w(TAG, "╔═══════════════════════════════════════════════════════════════╗");
            Log.w(TAG, "║  ⚠️  SECURITY WARNING: Legacy Key Storage Detected             ║");
            Log.w(TAG, "║  -------------------------------------------------------------  ║");
            Log.w(TAG, "║  Current: Private keys stored in SharedPreferences            ║");
            Log.w(TAG, "║  Risk:    Keys readable by root/backup apps                    ║");
            Log.w(TAG, "║  Action:   Migrate to AndroidKeyStore (Phase 2)                ║");
            Log.w(TAG, "║  Reference: openspec/changes/security-hardening-phase1/        ║");
            Log.w(TAG, "╚═══════════════════════════════════════════════════════════════╝");
        }
    }

    /**
     * 获取密钥迁移状态
     *
     * @return 迁移状态时间戳（毫秒），如果未记录则返回0
     */
    public long getMigrationStatusTimestamp() {
        return prefs.getLong(KEY_MIGRATION_STATUS, 0);
    }

    /**
     * 检查是否需要迁移密钥
     *
     * @return true如果密钥需要迁移到AndroidKeyStore
     */
    public boolean needsMigration() {
        KeyStorageType currentType = detectKeyStorageType();
        return currentType == KeyStorageType.LEGACY;
    }

    /**
     * 获取当前密钥存储类型（供外部查询）
     *
     * @return 当前密钥存储类型
     */
    public KeyStorageType getCurrentKeyStorageType() {
        return detectKeyStorageType();
    }

    /**
     * 标记密钥已迁移（第二阶段使用）
     * 此方法将在第二阶段迁移实现时调用
     */
    public void markKeysMigratedToKeyStore() {
        prefs.edit()
                .putString(KEY_STORAGE_TYPE, KeyStorageType.ANDROID_KEYSTORE.name())
                .putLong(KEY_MIGRATION_STATUS, System.currentTimeMillis())
                .apply();

        Log.i(TAG, "╔═══════════════════════════════════════════════════════════════╗");
        Log.i(TAG, "║  ✅ KEY MIGRATION COMPLETED                                   ║");
        Log.i(TAG, "║  -------------------------------------------------------------  ║");
        Log.i(TAG, "║  Keys successfully migrated to AndroidKeyStore                ║");
        Log.i(TAG, "║  Private keys now protected by hardware                       ║");
        Log.i(TAG, "║  Migration completed at: " + new java.util.Date() + "           ║");
        Log.i(TAG, "╚═══════════════════════════════════════════════════════════════╝");
    }
}
