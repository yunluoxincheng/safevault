package com.ttt.safevault.security;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.Argon2KeyDerivationManager;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 备份加密管理器（三层安全架构）
 *
 * 使用 Argon2id + AES-256-GCM 加密备份数据
 *
 * 两种模式：
 * 1. 本地备份：使用 SessionGuard.DataKey（会话中已有）
 * 2. 云端同步：使用 Argon2id + 固定 salt（基于用户邮箱）
 *
 * @since SafeVault 3.4.0 (移除旧安全架构，完全迁移到三层架构)
 * @since SafeVault 3.8.0 (合并 CryptoSession 到 SessionGuard)
 */
public class BackupEncryptionManager {
    private static final String TAG = "BackupEncryptionManager";
    private static final String ALGORITHM_AES = "AES";
    private static final String TRANSFORMATION_GCM = "AES/GCM/NoPadding";

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private static volatile BackupEncryptionManager INSTANCE;
    private final Context context;
    private final SessionGuard sessionGuard;
    private final Argon2KeyDerivationManager argon2Manager;

    private BackupEncryptionManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.sessionGuard = SessionGuard.getInstance();
        this.argon2Manager = Argon2KeyDerivationManager.getInstance(context);
        Log.i(TAG, "BackupEncryptionManager 初始化成功（使用 Argon2id）");
    }

    /**
     * 获取 BackupEncryptionManager 单例
     */
    public static BackupEncryptionManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (BackupEncryptionManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new BackupEncryptionManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // ========== 本地备份模式（使用 DataKey） ==========

    /**
     * 为本地备份加密数据
     *
     * 使用会话中的 DataKey 进行加密，无需密码
     *
     * @param plaintext 明文
     * @return 加密结果
     * @throws IllegalStateException 如果会话未解锁
     */
    @NonNull
    public LocalBackupResult encryptForLocalBackup(@NonNull String plaintext) {
        SecretKey dataKey = sessionGuard.getDataKey();
        if (dataKey == null) {
            throw new IllegalStateException("会话未解锁，无法进行本地备份");
        }

        try {
            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            // 准备加密
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, gcmSpec);

            // 加密
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            // GCM模式：密文末尾的 16 字节是认证标签（authTag）
            int tagLength = GCM_TAG_LENGTH_BITS / 8; // 128 bits = 16 bytes
            byte[] authTagBytes = new byte[tagLength];
            byte[] actualCiphertext = new byte[ciphertext.length - tagLength];

            // 分离密文和认证标签
            System.arraycopy(ciphertext, 0, actualCiphertext, 0, actualCiphertext.length);
            System.arraycopy(ciphertext, actualCiphertext.length, authTagBytes, 0, tagLength);

            String encryptedData = Base64.encodeToString(actualCiphertext, Base64.NO_WRAP);
            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);
            String authTag = Base64.encodeToString(authTagBytes, Base64.NO_WRAP);

            Log.d(TAG, "本地备份加密成功");
            return new LocalBackupResult(encryptedData, ivBase64, authTag);

        } catch (Exception e) {
            Log.e(TAG, "本地备份加密失败", e);
            throw new SecurityException("本地备份加密失败", e);
        }
    }

    /**
     * 解密本地备份数据
     *
     * 使用会话中的 DataKey 进行解密
     *
     * @param encryptedData 加密的数据
     * @param iv IV
     * @param authTag GCM 认证标签
     * @return 解密后的明文
     * @throws IllegalStateException 如果会话未解锁
     */
    @NonNull
    public String decryptLocalBackup(@NonNull String encryptedData,
                                     @NonNull String iv,
                                     @NonNull String authTag) {
        SecretKey dataKey = sessionGuard.getDataKey();
        if (dataKey == null) {
            throw new IllegalStateException("会话未解锁，无法解密本地备份");
        }

        try {
            // 解码数据
            byte[] ciphertext = Base64.decode(encryptedData, Base64.NO_WRAP);
            byte[] ivBytes = Base64.decode(iv, Base64.NO_WRAP);
            byte[] authTagBytes = Base64.decode(authTag, Base64.NO_WRAP);

            // 重新组合密文和认证标签
            byte[] combined = new byte[ciphertext.length + authTagBytes.length];
            System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
            System.arraycopy(authTagBytes, 0, combined, ciphertext.length, authTagBytes.length);

            // 解密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, gcmSpec);

            byte[] plaintextBytes = cipher.doFinal(combined);
            return new String(plaintextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "本地备份解密失败", e);
            throw new SecurityException("本地备份解密失败", e);
        }
    }

    // ========== 云端同步模式（使用 Argon2id + 固定 salt） ==========

    /**
     * 为云端同步加密数据
     *
     * 使用 Argon2id + 固定 salt（基于用户邮箱）进行加密
     * Salt 应该从 Argon2KeyDerivationManager 获取或生成
     *
     * @param plaintext 明文
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码）
     * @return 加密结果
     */
    @NonNull
    public CloudBackupResult encryptForCloudSync(@NonNull String plaintext,
                                                 @NonNull String masterPassword,
                                                 @NonNull String saltBase64) {
        try {
            // 生成随机 IV（每次加密都使用新的IV）
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            // 使用 Argon2id 派生密钥
            SecretKey key = argon2Manager.deriveKeyWithArgon2id(masterPassword, saltBase64);

            // 准备加密
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            // 加密（GCM 模式的 doFinal 返回值已包含 authTag 在末尾）
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            // GCM模式：密文末尾的 16 字节是认证标签（authTag）
            // 分离密文和认证标签，以便后端存储和验证
            int tagLength = GCM_TAG_LENGTH_BITS / 8; // 128 bits = 16 bytes
            byte[] authTagBytes = new byte[tagLength];
            byte[] actualCiphertext = new byte[ciphertext.length - tagLength];

            // 分离密文和认证标签
            System.arraycopy(ciphertext, 0, actualCiphertext, 0, actualCiphertext.length);
            System.arraycopy(ciphertext, actualCiphertext.length, authTagBytes, 0, tagLength);

            String encryptedData = Base64.encodeToString(actualCiphertext, Base64.NO_WRAP);
            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);
            String authTag = Base64.encodeToString(authTagBytes, Base64.NO_WRAP);

            Log.d(TAG, "云端同步加密成功（authTag 已分离）");
            return new CloudBackupResult(encryptedData, ivBase64, saltBase64, authTag);

        } catch (Exception e) {
            Log.e(TAG, "云端同步加密失败", e);
            throw new SecurityException("云端同步加密失败", e);
        }
    }

    /**
     * 解密云端同步数据
     *
     * 使用 Argon2id + 固定 salt 进行解密
     *
     * @param encryptedData 加密的数据（如果 authTag 为 null，则已包含 authTag）
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码）
     * @param iv IV
     * @param authTag GCM 认证标签（如果为 null，表示已包含在 encryptedData 中）
     * @return 解密后的明文
     */
    @NonNull
    public String decryptCloudSync(@NonNull String encryptedData,
                                   @NonNull String masterPassword,
                                   @NonNull String saltBase64,
                                   @NonNull String iv,
                                   @Nullable String authTag) {
        try {
            // 使用 Argon2id 派生密钥
            SecretKey key = argon2Manager.deriveKeyWithArgon2id(masterPassword, saltBase64);

            // 解码数据
            byte[] ciphertext = Base64.decode(encryptedData, Base64.NO_WRAP);
            byte[] ivBytes = Base64.decode(iv, Base64.NO_WRAP);

            // 如果 authTag 为 null 或空，说明 authTag 已包含在 encryptedData 中（向后兼容旧数据）
            // 否则需要重组密文和 authTag（新标准格式）
            byte[] combined;
            if (authTag == null || authTag.isEmpty()) {
                // authTag 已包含在密文中（旧格式），直接使用
                combined = ciphertext;
                Log.d(TAG, "解密：authTag 已包含在密文中（旧格式）");
            } else {
                // 重组密文和分离的 authTag（新标准格式）
                byte[] authTagBytes = Base64.decode(authTag, Base64.NO_WRAP);
                combined = new byte[ciphertext.length + authTagBytes.length];
                System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
                System.arraycopy(authTagBytes, 0, combined, ciphertext.length, authTagBytes.length);
                Log.d(TAG, "解密：重组密文和分离的 authTag（标准格式）");
            }

            // 解密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            byte[] plaintextBytes = cipher.doFinal(combined);
            return new String(plaintextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "云端同步解密失败", e);
            throw new SecurityException("云端同步解密失败", e);
        }
    }

    // ========== 通用方法 ==========

    /**
     * 生成随机 IV
     *
     * @return Base64 编码的 IV
     */
    @NonNull
    public String generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        return Base64.encodeToString(iv, Base64.NO_WRAP);
    }

    /**
     * 获取或生成用户 salt（用于云端同步）
     *
     * @param userEmail 用户邮箱
     * @return Base64 编码的 salt
     */
    @NonNull
    public String getOrGenerateUserSalt(@NonNull String userEmail) {
        return argon2Manager.getOrGenerateUserSalt(userEmail);
    }

    // ========== 云端同步专用方法（固定参数） ==========

    /**
     * 为云端同步加密数据（使用固定参数）
     *
     * 使用 Argon2id 固定高配参数（128MB, 3 iterations, 4 parallelism）进行加密，
     * 确保跨设备、跨安装的密钥派生结果一致。
     *
     * @param plaintext 明文
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码，应使用 {@link com.ttt.safevault.crypto.Argon2KeyDerivationManager#generateCloudSalt()} 生成）
     * @return 加密结果（包含加密数据、IV、salt、authTag）
     */
    @NonNull
    public CloudBackupResult encryptForCloudSyncWithFixedParams(@NonNull String plaintext,
                                                                 @NonNull String masterPassword,
                                                                 @NonNull String saltBase64) {
        try {
            // 生成随机 IV（每次加密都使用新的IV）
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            // 使用固定参数的 Argon2id 派生密钥
            SecretKey key = argon2Manager.deriveKeyWithArgon2idForCloud(masterPassword, saltBase64);

            // 准备加密
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            // 加密
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            // 分离密文和认证标签
            int tagLength = GCM_TAG_LENGTH_BITS / 8;
            byte[] authTagBytes = new byte[tagLength];
            byte[] actualCiphertext = new byte[ciphertext.length - tagLength];

            System.arraycopy(ciphertext, 0, actualCiphertext, 0, actualCiphertext.length);
            System.arraycopy(ciphertext, actualCiphertext.length, authTagBytes, 0, tagLength);

            String encryptedData = Base64.encodeToString(actualCiphertext, Base64.NO_WRAP);
            String ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP);
            String authTag = Base64.encodeToString(authTagBytes, Base64.NO_WRAP);

            Log.d(TAG, "云端同步加密成功（固定参数 + authTag 已分离）");
            return new CloudBackupResult(encryptedData, ivBase64, saltBase64, authTag);

        } catch (Exception e) {
            Log.e(TAG, "云端同步加密失败（固定参数）", e);
            throw new SecurityException("云端同步加密失败（固定参数）", e);
        }
    }

    /**
     * 解密云端同步数据（使用固定参数）
     *
     * 使用 Argon2id 固定高配参数进行解密。
     *
     * @param encryptedData 加密的数据
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64编码，应从云端获取）
     * @param iv IV
     * @param authTag GCM 认证标签
     * @return 解密后的明文
     */
    @NonNull
    public String decryptCloudSyncWithFixedParams(@NonNull String encryptedData,
                                                   @NonNull String masterPassword,
                                                   @NonNull String saltBase64,
                                                   @NonNull String iv,
                                                   @Nullable String authTag) {
        try {
            // 使用固定参数的 Argon2id 派生密钥
            SecretKey key = argon2Manager.deriveKeyWithArgon2idForCloud(masterPassword, saltBase64);

            // 解码数据
            byte[] ciphertext = Base64.decode(encryptedData, Base64.NO_WRAP);
            byte[] ivBytes = Base64.decode(iv, Base64.NO_WRAP);

            // 重组密文和认证标签
            byte[] combined;
            if (authTag == null || authTag.isEmpty()) {
                combined = ciphertext;
                Log.d(TAG, "解密（固定参数）：authTag 已包含在密文中（旧格式）");
            } else {
                byte[] authTagBytes = Base64.decode(authTag, Base64.NO_WRAP);
                combined = new byte[ciphertext.length + authTagBytes.length];
                System.arraycopy(ciphertext, 0, combined, 0, ciphertext.length);
                System.arraycopy(authTagBytes, 0, combined, ciphertext.length, authTagBytes.length);
                Log.d(TAG, "解密（固定参数）：重组密文和分离的 authTag（标准格式）");
            }

            // 解密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            byte[] plaintextBytes = cipher.doFinal(combined);
            return new String(plaintextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "云端同步解密失败（固定参数）", e);
            throw new SecurityException("云端同步解密失败（固定参数）", e);
        }
    }

    // ========== 内部类 ==========

    /**
     * 本地备份结果（使用 DataKey 加密）
     */
    public static class LocalBackupResult {
        private final String encryptedData;
        private final String iv;
        private final String authTag;

        public LocalBackupResult(@NonNull String encryptedData,
                                @NonNull String iv,
                                @NonNull String authTag) {
            this.encryptedData = encryptedData;
            this.iv = iv;
            this.authTag = authTag;
        }

        @NonNull
        public String getEncryptedData() {
            return encryptedData;
        }

        @NonNull
        public String getIv() {
            return iv;
        }

        @NonNull
        public String getAuthTag() {
            return authTag;
        }
    }

    /**
     * 云端备份结果（使用 Argon2id 加密）
     */
    public static class CloudBackupResult {
        private final String encryptedData;
        private final String iv;
        private final String salt;
        private final String authTag;

        public CloudBackupResult(@NonNull String encryptedData,
                                @NonNull String iv,
                                @NonNull String salt,
                                @NonNull String authTag) {
            this.encryptedData = encryptedData;
            this.iv = iv;
            this.salt = salt;
            this.authTag = authTag;
        }

        @NonNull
        public String getEncryptedData() {
            return encryptedData;
        }

        @NonNull
        public String getIv() {
            return iv;
        }

        @NonNull
        public String getSalt() {
            return salt;
        }

        @NonNull
        public String getAuthTag() {
            return authTag;
        }
    }
}
