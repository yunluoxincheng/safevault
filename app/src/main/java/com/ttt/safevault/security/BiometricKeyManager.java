package com.ttt.safevault.security;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.annotation.RequiresApi;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * 生物识别密钥管理器
 * 用于安全地管理生物识别解锁所需的加密密钥
 */
public class BiometricKeyManager {
    private static final String TAG = "BiometricKeyManager";
    private static final String KEY_ALIAS = "safevault_biometric_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private static BiometricKeyManager instance;
    private KeyStore keyStore;

    private BiometricKeyManager() throws Exception {
        keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
    }

    public static synchronized BiometricKeyManager getInstance() throws Exception {
        if (instance == null) {
            instance = new BiometricKeyManager();
        }
        return instance;
    }

    /**
     * 初始化生物识别密钥
     */
    public void initializeKey() throws Exception {
        // 如果密钥已存在，直接使用，不要重建
        // 重建会导致之前用旧密钥加密的数据无法解密
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return;
        }

        // 创建新密钥
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);

        KeyGenParameterSpec keyGenParameterSpec = new KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(false)  // 不需要每次认证，因为生物识别认证在应用层完成
            .build();

        keyGenerator.init(keyGenParameterSpec);
        keyGenerator.generateKey();
    }

    /**
     * 获取加密Cipher
     */
    public Cipher getEncryptCipher() throws Exception {
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (secretKey == null) {
            throw new Exception("Biometric key not found");
        }
        
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher;
    }

    /**
     * 获取解密Cipher
     */
    public Cipher getDecryptCipher(byte[] iv) throws Exception {
        if (iv == null || iv.length == 0) {
            throw new Exception("Invalid IV");
        }
        
        SecretKey secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (secretKey == null) {
            throw new Exception("Biometric key not found");
        }
        
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
        return cipher;
    }

    /**
     * 检查密钥是否存在
     */
    public boolean hasKey() throws Exception {
        return keyStore.containsAlias(KEY_ALIAS);
    }

    /**
     * 删除密钥
     */
    public void deleteKey() throws Exception {
        keyStore.deleteEntry(KEY_ALIAS);
    }
}