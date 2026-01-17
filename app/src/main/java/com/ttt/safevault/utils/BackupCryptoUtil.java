package com.ttt.safevault.utils;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 备份文件加密工具类
 * 使用 AES-256-GCM 加密备份数据
 */
public class BackupCryptoUtil {

    private static final String TAG = "BackupCryptoUtil";
    private static final String ALGORITHM_AES = "AES";
    private static final String TRANSFORMATION_GCM = "AES/GCM/NoPadding";
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";

    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int PBKDF2_ITERATIONS = 100000;
    private static final int DERIVED_KEY_LENGTH = 256;

    /**
     * 从密码派生密钥
     *
     * @param password 密码
     * @param salt     盐值（Base64编码）
     * @return 派生的密钥
     */
    public static SecretKey deriveKey(String password, String salt) {
        try {
            byte[] saltBytes = Base64.getDecoder().decode(salt);

            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            KeySpec spec = new PBEKeySpec(
                    password.toCharArray(),
                    saltBytes,
                    PBKDF2_ITERATIONS,
                    DERIVED_KEY_LENGTH
            );

            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, ALGORITHM_AES);
        } catch (Exception e) {
            Log.e(TAG, "Failed to derive key", e);
            throw new RuntimeException("密钥派生失败", e);
        }
    }

    /**
     * 生成随机盐值
     *
     * @return Base64编码的盐值
     */
    public static String generateSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * 生成随机IV
     *
     * @return Base64编码的IV
     */
    public static String generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        new SecureRandom().nextBytes(iv);
        return Base64.getEncoder().encodeToString(iv);
    }

    /**
     * 加密数据
     *
     * @param plaintext 明文
     * @param password  密码
     * @return 加密结果（包含 iv, salt, encryptedData, authTag）
     */
    public static EncryptionResult encrypt(String plaintext, String password) {
        try {
            // 生成盐值和IV
            String salt = generateSalt();
            String iv = generateIV();

            // 派生密钥
            SecretKey key = deriveKey(password, salt);

            // 准备加密
            byte[] ivBytes = Base64.getDecoder().decode(iv);
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            // 加密
            byte[] ciphertext = cipher.doFinal(plaintextBytes);

            // 组合 IV 和密文（GCM模式下，密文包含认证标签）
            byte[] combined = new byte[ivBytes.length + ciphertext.length];
            System.arraycopy(ivBytes, 0, combined, 0, ivBytes.length);
            System.arraycopy(ciphertext, 0, combined, ivBytes.length, ciphertext.length);

            String encryptedData = Base64.getEncoder().encodeToString(combined);

            return new EncryptionResult(encryptedData, iv, salt);

        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt data", e);
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密数据
     *
     * @param encryptedData 加密的数据（Base64编码）
     * @param password       密码
     * @param salt           盐值（Base64编码）
     * @param iv             IV（Base64编码）
     * @return 解密后的明文
     */
    public static String decrypt(String encryptedData, String password, String salt, String iv) {
        try {
            // 派生密钥
            SecretKey key = deriveKey(password, salt);

            // 解码数据
            byte[] combinedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] ivBytes = Base64.getDecoder().decode(iv);

            // 提取密文（去掉IV前缀）
            byte[] ciphertext = new byte[combinedBytes.length - ivBytes.length];
            System.arraycopy(combinedBytes, ivBytes.length, ciphertext, 0, ciphertext.length);

            // 解密
            Cipher cipher = Cipher.getInstance(TRANSFORMATION_GCM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            byte[] plaintextBytes = cipher.doFinal(ciphertext);
            return new String(plaintextBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt data", e);
            throw new RuntimeException("解密失败，请检查密码是否正确", e);
        }
    }

    /**
     * 加密结果封装类
     */
    public static class EncryptionResult {
        private final String encryptedData;
        private final String iv;
        private final String salt;

        public EncryptionResult(String encryptedData, String iv, String salt) {
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
