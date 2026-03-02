package com.ttt.safevault.crypto;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import javax.crypto.KeyAgreement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * X25519 密钥管理器系统 API 实现（API 33+）
 *
 * 使用 Android 系统 API 实现 X25519 密钥操作
 * 仅在 Android 13 (API 33) 及以上版本可用
 *
 * 优势：
 * - 原生支持，无需额外库
 * - 性能最优
 * - APK 体积最小
 */
@RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
public class SystemX25519KeyManager implements X25519KeyManager {
    private static final String TAG = "SystemX25519KeyManager";

    @Override
    public KeyPair generateKeyPair() throws Exception {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(CryptoConstants.X25519_ALGORITHM);

            // 尝试使用 NamedParameterSpec
            try {
                NamedParameterSpec spec = new NamedParameterSpec(CryptoConstants.X25519_CURVE);
                kpg.initialize(spec);
            } catch (Exception e) {
                // 如果 NamedParameterSpec 不支持，尝试使用反射
                Log.w(TAG, "NamedParameterSpec not supported, trying reflection", e);
                tryInitializeWithReflection(kpg);
            }

            KeyPair keyPair = kpg.generateKeyPair();

            Log.d(TAG, "Generated X25519 key pair using system API");

            // 验证密钥大小
            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();

            if (publicKeyBytes.length != CryptoConstants.X25519_KEY_SIZE) {
                throw new SecurityException("Invalid public key size: " + publicKeyBytes.length);
            }

            Log.d(TAG, "X25519 key pair generated successfully: public=" + publicKeyBytes.length + " bytes, private=" + privateKeyBytes.length + " bytes");

            return keyPair;

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate X25519 key pair", e);
            throw new Exception("Failed to generate X25519 key pair: " + e.getMessage(), e);
        }
    }

    /**
     * 使用反射初始化 KeyPairGenerator
     * 用于某些不支持 NamedParameterSpec 的 Android 设备
     */
    private void tryInitializeWithReflection(KeyPairGenerator kpg) throws Exception {
        try {
            // 尝试获取 NamedParameterSpec 类
            Class<?> namedParameterSpecClass = Class.forName("java.security.spec.NamedParameterSpec");
            Constructor<?> constructor = namedParameterSpecClass.getConstructor(String.class);
            Object spec = constructor.newInstance(CryptoConstants.X25519_CURVE);

            // 调用 initialize 方法
            Method initializeMethod = kpg.getClass().getMethod("initialize", Class.forName("java.security.spec.AlgorithmParameterSpec"));
            initializeMethod.invoke(kpg, spec);

            Log.d(TAG, "KeyPairGenerator initialized via reflection");
        } catch (Exception e) {
            Log.e(TAG, "Reflection initialization also failed", e);
            throw new Exception("Unable to initialize XDH KeyPairGenerator", e);
        }
    }

    @Override
    public byte[] performECDH(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        try {
            KeyAgreement ka = KeyAgreement.getInstance(CryptoConstants.X25519_ALGORITHM);
            ka.init(privateKey);
            ka.doPhase(publicKey, true);
            byte[] sharedSecret = ka.generateSecret();

            // 验证共享密钥大小
            if (sharedSecret.length != CryptoConstants.ECDH_SHARED_SECRET_SIZE) {
                throw new SecurityException("Invalid shared secret size: " + sharedSecret.length);
            }

            Log.d(TAG, "ECDH key exchange successful, shared secret: " + sharedSecret.length + " bytes");

            return sharedSecret;

        } catch (Exception e) {
            Log.e(TAG, "ECDH key exchange failed", e);
            throw new Exception("ECDH key exchange failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void validatePublicKey(byte[] encodedKey) throws Exception {
        if (encodedKey == null) {
            throw new SecurityException("Public key cannot be null");
        }

        if (encodedKey.length != CryptoConstants.X25519_KEY_SIZE) {
            throw new SecurityException("Invalid public key size: " + encodedKey.length + " (expected " + CryptoConstants.X25519_KEY_SIZE + ")");
        }

        // 尝试解码公钥以验证其有效性
        try {
            KeyFactory kf = KeyFactory.getInstance(CryptoConstants.X25519_ALGORITHM);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encodedKey);
            PublicKey publicKey = kf.generatePublic(spec);

            if (publicKey == null) {
                throw new SecurityException("Failed to decode public key");
            }

            Log.d(TAG, "Public key validation successful");

        } catch (Exception e) {
            Log.e(TAG, "Public key validation failed", e);
            throw new SecurityException("Invalid X25519 public key: " + e.getMessage(), e);
        }
    }

    @Override
    public PublicKey decodePublicKey(byte[] encodedKey) throws Exception {
        if (encodedKey == null || encodedKey.length == 0) {
            throw new IllegalArgumentException("Encoded key cannot be null or empty");
        }

        try {
            KeyFactory kf = KeyFactory.getInstance(CryptoConstants.X25519_ALGORITHM);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encodedKey);
            PublicKey publicKey = kf.generatePublic(spec);

            Log.d(TAG, "Successfully decoded X25519 public key: " + encodedKey.length + " bytes");

            return publicKey;

        } catch (Exception e) {
            Log.e(TAG, "Failed to decode X25519 public key", e);
            throw new Exception("Failed to decode public key: " + e.getMessage(), e);
        }
    }

    @Override
    public PrivateKey decodePrivateKey(byte[] encodedKey) throws Exception {
        if (encodedKey == null || encodedKey.length == 0) {
            throw new IllegalArgumentException("Encoded key cannot be null or empty");
        }

        try {
            KeyFactory kf = KeyFactory.getInstance(CryptoConstants.X25519_ALGORITHM);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encodedKey);
            PrivateKey privateKey = kf.generatePrivate(spec);

            Log.d(TAG, "Successfully decoded X25519 private key: " + encodedKey.length + " bytes");

            return privateKey;

        } catch (Exception e) {
            Log.e(TAG, "Failed to decode X25519 private key", e);
            throw new Exception("Failed to decode private key: " + e.getMessage(), e);
        }
    }
}