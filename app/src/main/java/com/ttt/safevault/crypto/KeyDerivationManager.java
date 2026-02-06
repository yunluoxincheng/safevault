package com.ttt.safevault.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 密钥派生管理器（已废弃 - 请使用 Argon2KeyDerivationManager）
 *
 * @deprecated 此类使用 PBKDF2 进行密钥派生，已被 Argon2KeyDerivationManager 替代
 * Argon2id 提供更强的安全性，抗 GPU/ASIC 攻击能力更强
 * 请使用 {@link Argon2KeyDerivationManager} 替代
 */
public class KeyDerivationManager {
    private static final String TAG = "KeyDerivationManager";
    private static final String PREFS_NAME = "key_derivation_prefs";
    private static final String KEY_SALT_PREFIX = "user_salt_";

    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int DERIVED_KEY_LENGTH = 256; // bits
    private static final int SALT_LENGTH = 32; // bytes
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    private final Context context;
    private final SharedPreferences prefs;

    public KeyDerivationManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 从主密码确定性派生RSA密钥对
     *
     * @param masterPassword 主密码
     * @param userEmail 用户邮箱（用于生成用户特定的盐值）
     * @return RSA密钥对
     */
    public KeyPair deriveKeyPairFromMasterPassword(
            @NonNull String masterPassword,
            @NonNull String userEmail
    ) {
        try {
            // 1. 获取或生成用户盐值
            String salt = getOrGenerateUserSalt(userEmail);

            // 2. 使用 PBKDF2 从主密码派生种子
            SecretKey seedKey = deriveSeedFromMasterPassword(masterPassword, salt);

            // 3. 使用种子作为随机数生成器的种子
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            secureRandom.setSeed(seedKey.getEncoded());

            // 4. 确定性生成RSA密钥对
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048, secureRandom);
            KeyPair keyPair = keyGen.generateKeyPair();

            Log.d(TAG, "Successfully derived key pair for user: " + userEmail);
            return keyPair;

        } catch (Exception e) {
            Log.e(TAG, "Failed to derive key pair", e);
            throw new RuntimeException("密钥派生失败", e);
        }
    }

    /**
     * 获取公钥（用于分享加密）
     */
    public PublicKey getPublicKey(@NonNull String masterPassword, @NonNull String userEmail) {
        KeyPair keyPair = deriveKeyPairFromMasterPassword(masterPassword, userEmail);
        return keyPair.getPublic();
    }

    /**
     * 获取私钥（用于分享解密）
     */
    public PrivateKey getPrivateKey(@NonNull String masterPassword, @NonNull String userEmail) {
        KeyPair keyPair = deriveKeyPairFromMasterPassword(masterPassword, userEmail);
        return keyPair.getPrivate();
    }

    /**
     * 从主密码和盐值派生种子
     */
    private SecretKey deriveSeedFromMasterPassword(
            @NonNull String masterPassword,
            @NonNull String salt
    ) {
        try {
            byte[] saltBytes = Base64.decode(salt, Base64.NO_WRAP);

            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            KeySpec spec = new PBEKeySpec(
                    masterPassword.toCharArray(),
                    saltBytes,
                    PBKDF2_ITERATIONS,
                    DERIVED_KEY_LENGTH
            );

            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");

        } catch (Exception e) {
            Log.e(TAG, "Failed to derive seed", e);
            throw new RuntimeException("种子派生失败", e);
        }
    }

    /**
     * 获取或生成用户盐值
     * 盐值会持久化保存，确保每次派生得到相同的密钥对
     */
    public String getOrGenerateUserSalt(@NonNull String userEmail) {
        String key = KEY_SALT_PREFIX + userEmail;
        String existingSalt = prefs.getString(key, null);

        if (existingSalt != null) {
            Log.d(TAG, "Found existing salt for user: " + userEmail);
            return existingSalt;
        }

        // 生成新盐值
        byte[] salt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        String saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP);

        // 保存盐值
        prefs.edit().putString(key, saltBase64).apply();
        Log.d(TAG, "Generated new salt for user: " + userEmail);

        return saltBase64;
    }

    /**
     * 清除用户的盐值（谨慎使用，会改变派生的密钥对）
     */
    public void clearUserSalt(@NonNull String userEmail) {
        String key = KEY_SALT_PREFIX + userEmail;
        prefs.edit().remove(key).apply();
        Log.w(TAG, "Cleared salt for user: " + userEmail);
    }

    /**
     * 从邮箱生成用户ID
     * 用于标识唯一用户
     *
     * @param email 用户邮箱
     * @return 用户ID
     */
    @NonNull
    public String generateUserId(@NonNull String email) {
        // 简单实现：使用邮箱的哈希作为用户ID
        return "user_" + Base64.encodeToString(
            email.getBytes(),
            Base64.NO_WRAP | Base64.URL_SAFE
        ).substring(0, 16);
    }
}
