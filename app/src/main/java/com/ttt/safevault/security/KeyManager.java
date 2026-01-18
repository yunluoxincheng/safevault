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
 */
public class KeyManager {
    private static final String TAG = "KeyManager";
    private static final String PREFS_NAME = "key_prefs";
    private static final String KEY_PUBLIC_KEY = "public_key";
    private static final String KEY_PRIVATE_KEY = "private_key";
    private static final String KEY_DEVICE_ID = "device_id";

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

    private KeyManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
     * 解密私钥（从云端下载或本地存储）
     *
     * @param encryptedPrivateKey Base64编码的加密私钥
     * @param masterPassword      主密码
     * @param email               用户邮箱
     * @param iv                  初始化向量（Base64编码）
     * @return 解密后的RSA私钥
     */
    public PrivateKey decryptPrivateKey(String encryptedPrivateKey, String masterPassword, String email, String iv) {
        try {
            // 1. 获取用户盐值
            String salt = getUserSalt(email);
            if (salt == null) {
                throw new IllegalStateException("未找到用户盐值，无法解密");
            }

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

            Log.d(TAG, "Private key decrypted and imported for user: " + email);
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

            if (encryptedPrivateKey == null || iv == null) {
                return false;
            }

            // 尝试解密，如果成功则密码正确
            decryptPrivateKey(encryptedPrivateKey, masterPassword, email, iv);
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
}
