package com.ttt.safevault.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.security.SecurityUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 加密管理器
 * 使用AES-256-GCM进行加密解密
 * 主密钥派生自用户主密码 + 盐值
 */
public class CryptoManager {

    private static final String TAG = "CryptoManager";
    private static final String PREFS_NAME = "crypto_prefs";
    private static final String PREF_SALT = "master_salt";
    private static final String PREF_VERIFY_HASH = "verify_hash";
    private static final String PREF_INITIALIZED = "initialized";
    private static final String PREF_SESSION_KEY = "session_master_key";
    private static final String PREF_SESSION_IV = "session_master_iv";
    private static final String PREF_UNLOCK_TIME = "unlock_time";
    private static final String PREF_IS_LOCKED = "is_locked";  // 明确的锁定标志
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000; // 30分钟会话超时
    
    private static final String KEYSTORE_ALIAS = "SafeVaultSessionKey";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 256;
    private static final int IV_SIZE = 12; // GCM推荐IV大小
    private static final int TAG_SIZE = 128; // GCM认证标签大小
    private static final int PBKDF2_ITERATIONS = 100000;

    private final Context context;
    private final SharedPreferences prefs;
    private SecretKey masterKey;
    private boolean isUnlocked = false;
    private String sessionPassword; // 临时会话密码（仅用于导出/导入操作）

    /**
     * 会话密码提供者接口
     * 用于在会话恢复时获取主密码
     */
    public interface SessionPasswordProvider {
        String getMasterPassword();
    }

    private static volatile SessionPasswordProvider sessionPasswordProvider;

    /**
     * 设置会话密码提供者
     * 应该在应用初始化时由 AccountManager 调用
     */
    public static void setSessionPasswordProvider(SessionPasswordProvider provider) {
        sessionPasswordProvider = provider;
    }

    public CryptoManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 检查是否已初始化（设置过主密码）
     */
    public boolean isInitialized() {
        return prefs.getBoolean(PREF_INITIALIZED, false);
    }

    /**
     * 初始化，设置主密码
     */
    public boolean initialize(@NonNull String masterPassword) {
        try {
            // 生成随机盐值
            byte[] salt = new byte[32];
            new SecureRandom().nextBytes(salt);

            // 派生主密钥
            SecretKey key = deriveKey(masterPassword, salt);

            // 生成验证哈希
            String verifyHash = generateVerifyHash(masterPassword, salt);

            // 保存盐值和验证哈希
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_SALT, Base64.encodeToString(salt, Base64.NO_WRAP));
            editor.putString(PREF_VERIFY_HASH, verifyHash);
            editor.putBoolean(PREF_INITIALIZED, true);
            editor.remove(PREF_IS_LOCKED);  // 清除锁定标志
            editor.apply();

            // 设置为已解锁
            this.masterKey = key;
            this.isUnlocked = true;
            this.sessionPassword = masterPassword; // 临时存储密码用于导出/导入

            // 持久化会话密钥，供自动填充服务使用
            persistSessionKey(key);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize", e);
            return false;
        }
    }

    /**
     * 使用主密码解锁
     */
    public boolean unlock(@NonNull String masterPassword) {
        try {
            String saltBase64 = prefs.getString(PREF_SALT, null);
            String storedHash = prefs.getString(PREF_VERIFY_HASH, null);

            if (saltBase64 == null || storedHash == null) {
                return false;
            }

            byte[] salt = Base64.decode(saltBase64, Base64.NO_WRAP);

            // 验证密码
            String verifyHash = generateVerifyHash(masterPassword, salt);
            if (!storedHash.equals(verifyHash)) {
                return false;
            }

            // 派生主密钥
            this.masterKey = deriveKey(masterPassword, salt);
            this.isUnlocked = true;
            this.sessionPassword = masterPassword; // 临时存储密码用于导出/导入

            // 清除锁定标志（允许会话恢复）
            prefs.edit().remove(PREF_IS_LOCKED).apply();

            // 持久化会话密钥，供自动填充服务使用
            persistSessionKey(this.masterKey);

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to unlock", e);
            return false;
        }
    }

    /**
     * 锁定，清除内存中的密钥
     * 设置锁定标志，阻止会话恢复
     */
    public void lock() {
        Log.d(TAG, "=== lock() 被调用 ===");
        Log.d(TAG, "锁定前状态: isUnlocked=" + this.isUnlocked + ", masterKey=" + (this.masterKey != null ? "存在" : "null"));

        this.masterKey = null;
        this.isUnlocked = false;
        this.sessionPassword = null; // 清除会话密码

        // 先设置锁定标志（同步），防止会话恢复
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_IS_LOCKED, true);
        boolean committed = editor.commit();  // 使用 commit 确保立即生效
        Log.d(TAG, "设置锁定标志 PREF_IS_LOCKED=true, commit=" + committed);

        // 再清除持久化的会话密钥（同步）
        clearSessionKeySync();

        // 验证锁定标志已设置
        boolean isLockedFlag = prefs.getBoolean(PREF_IS_LOCKED, false);
        Log.d(TAG, "验证锁定标志: PREF_IS_LOCKED=" + isLockedFlag);
        Log.d(TAG, "锁定后状态: isUnlocked=" + this.isUnlocked + ", masterKey=" + (this.masterKey != null ? "存在" : "null"));
        Log.d(TAG, "=== lock() 完成 ===");
    }

    /**
     * 检查是否已解锁
     * 如果内存中没有密钥，尝试从持久化存储恢复
     */
    public boolean isUnlocked() {
        // 先检查内存中的状态
        if (isUnlocked && masterKey != null) {
            Log.d(TAG, "isUnlocked: 内存中已解锁 (isUnlocked=" + isUnlocked + ", masterKey exists)");
            return true;
        }

        Log.d(TAG, "isUnlocked: 内存中未锁定，尝试恢复会话...");
        Log.d(TAG, "isUnlocked: PREF_IS_LOCKED=" + prefs.getBoolean(PREF_IS_LOCKED, false));

        // 尝试从持久化存储恢复会话密钥
        boolean restored = tryRestoreSession();
        Log.d(TAG, "isUnlocked: 会话恢复结果=" + restored + ", 最终状态=" + (isUnlocked && masterKey != null));
        return restored;
    }
    
    /**
     * 获取主密钥（如果需要会自动恢复会话）
     */
    @Nullable
    public SecretKey getMasterKey() {
        if (masterKey != null) {
            return masterKey;
        }

        // 尝试恢复会话
        if (tryRestoreSession()) {
            return masterKey;
        }

        return null;
    }

    /**
     * 获取会话密码（用于导出/导入操作）
     * 注意：密码仅在内存中临时存储，锁定后会被清除
     */
    @Nullable
    public String getMasterPassword() {
        if (!isUnlocked()) {
            throw new IllegalStateException("CryptoManager is locked");
        }

        // 如果内存中没有密码，尝试从会话密码提供者获取
        if (sessionPassword == null && sessionPasswordProvider != null) {
            Log.d(TAG, "Session password is null, trying to get from provider");
            sessionPassword = sessionPasswordProvider.getMasterPassword();
            if (sessionPassword != null) {
                Log.d(TAG, "Session password restored from provider");
            }
        }

        return sessionPassword;
    }

    /**
     * 加密字符串
     */
    @Nullable
    public EncryptedData encrypt(@Nullable String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }

        if (!isUnlocked()) {
            throw new IllegalStateException("CryptoManager is locked");
        }

        try {
            // 生成随机IV
            byte[] iv = new byte[IV_SIZE];
            new SecureRandom().nextBytes(iv);

            // 初始化加密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);

            // 加密
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return new EncryptedData(
                    Base64.encodeToString(encrypted, Base64.NO_WRAP),
                    Base64.encodeToString(iv, Base64.NO_WRAP)
            );
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed", e);
            return null;
        }
    }

    /**
     * 解密字符串
     */
    @Nullable
    public String decrypt(@Nullable String encryptedBase64, @Nullable String ivBase64) {
        if (encryptedBase64 == null || ivBase64 == null) {
            return null;
        }

        if (!isUnlocked()) {
            throw new IllegalStateException("CryptoManager is locked");
        }

        try {
            byte[] encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP);
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);

            // 初始化解密器
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec);

            // 解密
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Decryption failed", e);
            return null;
        }
    }

    /**
     * 更改主密码
     */
    public boolean changeMasterPassword(@NonNull String oldPassword, @NonNull String newPassword) {
        // 先验证旧密码
        String saltBase64 = prefs.getString(PREF_SALT, null);
        String storedHash = prefs.getString(PREF_VERIFY_HASH, null);

        if (saltBase64 == null || storedHash == null) {
            return false;
        }

        try {
            byte[] oldSalt = Base64.decode(saltBase64, Base64.NO_WRAP);
            String verifyHash = generateVerifyHash(oldPassword, oldSalt);

            if (!storedHash.equals(verifyHash)) {
                return false; // 旧密码错误
            }

            // 生成新盐值
            byte[] newSalt = new byte[32];
            new SecureRandom().nextBytes(newSalt);

            // 派生新主密钥
            SecretKey newKey = deriveKey(newPassword, newSalt);
            String newVerifyHash = generateVerifyHash(newPassword, newSalt);

            // 保存新的盐值和验证哈希
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP));
            editor.putString(PREF_VERIFY_HASH, newVerifyHash);
            editor.apply();

            // 更新内存中的密钥
            this.masterKey = newKey;

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to change master password", e);
            return false;
        }
    }

    /**
     * 使用PBKDF2从密码派生密钥
     */
    private SecretKey deriveKey(@NonNull String password, @NonNull byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_SIZE
        );

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        spec.clearPassword();

        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 生成验证哈希
     */
    private String generateVerifyHash(@NonNull String password, @NonNull byte[] salt) {
        // 使用密码+盐值生成哈希，用于验证密码正确性
        String combined = password + Base64.encodeToString(salt, Base64.NO_WRAP);
        return SecurityUtils.sha256(combined);
    }
    
    /**
     * 持久化会话密钥，使用 Android Keystore 加密保护
     */
    private void persistSessionKey(SecretKey key) {
        try {
            // 获取或创建 Keystore 密钥
            SecretKey keystoreKey = getOrCreateKeystoreKey();
            if (keystoreKey == null) {
                Log.e(TAG, "Failed to get keystore key");
                return;
            }

            // 使用 Keystore 密钥加密会话密钥
            // 注意：对于 Android Keystore 中的 GCM 模式密钥，不能提供自定义 IV
            // 必须让系统自动生成 IV
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey);

            byte[] encryptedKey = cipher.doFinal(key.getEncoded());
            byte[] iv = cipher.getIV();

            // 保存加密后的密钥和IV
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(PREF_SESSION_KEY, Base64.encodeToString(encryptedKey, Base64.NO_WRAP));
            editor.putString(PREF_SESSION_IV, Base64.encodeToString(iv, Base64.NO_WRAP));
            editor.putLong(PREF_UNLOCK_TIME, System.currentTimeMillis());
            editor.apply();

            Log.d(TAG, "Session key persisted successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist session key", e);
        }
    }
    
    /**
     * 清除持久化的会话密钥（异步版本）
     */
    private void clearSessionKey() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PREF_SESSION_KEY);
        editor.remove(PREF_SESSION_IV);
        editor.remove(PREF_UNLOCK_TIME);
        editor.apply();
        Log.d(TAG, "Session key cleared (async)");
    }

    /**
     * 清除持久化的会话密钥（同步版本）
     * 在 lock() 时使用，确保立即生效
     */
    private void clearSessionKeySync() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(PREF_SESSION_KEY);
        editor.remove(PREF_SESSION_IV);
        editor.remove(PREF_UNLOCK_TIME);
        editor.commit();  // 使用 commit 确保立即生效
        Log.d(TAG, "Session key cleared (sync)");
    }
    
    /**
     * 尝试从持久化存储恢复会话密钥
     * 如果应用已锁定（PREF_IS_LOCKED == true），则不恢复会话
     */
    private boolean tryRestoreSession() {
        // 首先检查是否已锁定
        if (prefs.getBoolean(PREF_IS_LOCKED, false)) {
            Log.d(TAG, "App is locked, cannot restore session");
            return false;
        }

        try {
            String encryptedKeyBase64 = prefs.getString(PREF_SESSION_KEY, null);
            String ivBase64 = prefs.getString(PREF_SESSION_IV, null);
            long unlockTime = prefs.getLong(PREF_UNLOCK_TIME, 0);

            if (encryptedKeyBase64 == null || ivBase64 == null) {
                Log.d(TAG, "No session key found");
                return false;
            }

            // 检查会话是否超时
            if (System.currentTimeMillis() - unlockTime > SESSION_TIMEOUT_MS) {
                Log.d(TAG, "Session expired");
                clearSessionKey();
                return false;
            }

            // 获取 Keystore 密钥
            SecretKey keystoreKey = getKeystoreKey();
            if (keystoreKey == null) {
                Log.e(TAG, "Keystore key not found");
                return false;
            }

            // 解密主密钥
            byte[] encryptedKey = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP);
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, keystoreKey, spec);

            byte[] keyBytes = cipher.doFinal(encryptedKey);

            this.masterKey = new SecretKeySpec(keyBytes, "AES");
            this.isUnlocked = true;

            // 尝试恢复会话密码（用于云端同步）
            if (sessionPasswordProvider != null) {
                this.sessionPassword = sessionPasswordProvider.getMasterPassword();
                if (this.sessionPassword != null) {
                    Log.d(TAG, "Session password restored from provider");
                } else {
                    Log.d(TAG, "Session password provider returned null (user may not have biometric enabled)");
                }
            } else {
                Log.d(TAG, "No session password provider available");
            }

            Log.d(TAG, "Session restored successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore session", e);
            clearSessionKey();
            return false;
        }
    }
    
    /**
     * 获取或创建 Android Keystore 中的密钥
     */
    private SecretKey getOrCreateKeystoreKey() {
        try {
            SecretKey key = getKeystoreKey();
            if (key != null) {
                return key;
            }
            
            // 创建新密钥
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
            
            KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            
            keyGenerator.init(keySpec);
            return keyGenerator.generateKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get or create keystore key", e);
            return null;
        }
    }
    
    /**
     * 从 Android Keystore 获取密钥
     */
    @Nullable
    private SecretKey getKeystoreKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            
            if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                return (SecretKey) keyStore.getKey(KEYSTORE_ALIAS, null);
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get keystore key", e);
            return null;
        }
    }

    /**
     * 加密数据封装类
     */
    public static class EncryptedData {
        public final String ciphertext;
        public final String iv;

        public EncryptedData(String ciphertext, String iv) {
            this.ciphertext = ciphertext;
            this.iv = iv;
        }
    }
}
