package com.ttt.safevault.crypto;

import android.util.Log;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.agreement.X25519Agreement;
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator;
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

import javax.security.auth.Destroyable;

/**
 * X25519 密钥管理器 Bouncy Castle 实现（API < 33）
 *
 * 使用 Bouncy Castle 库实现 X25519 密钥操作
 * 用于 Android 10-13 (API 29-33) 版本
 *
 * 注意：
 * - 需要初始化 Bouncy Castle Provider
 * - APK 体积会增加约 3-5MB
 * - 性能略低于系统 API，但仍远优于 RSA
 *
 * 重要：此实现使用自定义的 Key 类，不依赖 JCA 的 XDH 支持
 */
public class BouncyCastleX25519KeyManager implements X25519KeyManager {
    private static final String TAG = "BCX25519KeyManager";
    private static boolean providerInitialized = false;

    static {
        // 初始化 Bouncy Castle Provider
        synchronized (BouncyCastleX25519KeyManager.class) {
            if (!providerInitialized) {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.insertProviderAt(new BouncyCastleProvider(), 1);
                }
                providerInitialized = true;
                Log.d(TAG, "BouncyCastle Provider initialized");
            }
        }
    }

    @Override
    public KeyPair generateKeyPair() throws Exception {
        try {
            // 使用 Bouncy Castle 的 X25519 密钥生成器
            X25519KeyPairGenerator generator = new X25519KeyPairGenerator();
            generator.init(new X25519KeyGenerationParameters(new java.security.SecureRandom()));
            AsymmetricCipherKeyPair keyPair = generator.generateKeyPair();

            X25519PublicKeyParameters pubParams = (X25519PublicKeyParameters) keyPair.getPublic();
            X25519PrivateKeyParameters privParams = (X25519PrivateKeyParameters) keyPair.getPrivate();

            // 提取密钥字节
            byte[] pubBytes = pubParams.getEncoded();
            byte[] privBytes = privParams.getEncoded();

            // 验证密钥大小
            if (pubBytes.length != CryptoConstants.X25519_KEY_SIZE) {
                throw new SecurityException("Invalid public key size: " + pubBytes.length);
            }
            if (privBytes.length != CryptoConstants.X25519_KEY_SIZE) {
                throw new SecurityException("Invalid private key size: " + privBytes.length);
            }

            // 使用自定义的 Key 包装类（不依赖 JCA XDH）
            X25519PublicKey publicKey = new X25519PublicKey(pubBytes);
            X25519PrivateKey privateKey = new X25519PrivateKey(privBytes);

            Log.d(TAG, "Generated X25519 key pair using Bouncy Castle (custom Key classes)");

            return new KeyPair(publicKey, privateKey);

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate X25519 key pair", e);
            throw new Exception("Key pair generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] performECDH(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        try {
            // 提取原始密钥字节
            byte[] privBytes;
            byte[] pubBytes;

            if (privateKey instanceof X25519PrivateKey) {
                privBytes = ((X25519PrivateKey) privateKey).getEncoded();
            } else {
                // 尝试从 getEncoded() 提取（假设是 X509/PKCS8 格式，取最后32字节）
                byte[] encoded = privateKey.getEncoded();
                if (encoded.length >= CryptoConstants.X25519_KEY_SIZE) {
                    privBytes = new byte[CryptoConstants.X25519_KEY_SIZE];
                    System.arraycopy(encoded, encoded.length - CryptoConstants.X25519_KEY_SIZE, privBytes, 0, CryptoConstants.X25519_KEY_SIZE);
                } else {
                    throw new SecurityException("Invalid private key encoding");
                }
            }

            if (publicKey instanceof X25519PublicKey) {
                pubBytes = ((X25519PublicKey) publicKey).getEncoded();
            } else {
                byte[] encoded = publicKey.getEncoded();
                if (encoded.length >= CryptoConstants.X25519_KEY_SIZE) {
                    pubBytes = new byte[CryptoConstants.X25519_KEY_SIZE];
                    System.arraycopy(encoded, encoded.length - CryptoConstants.X25519_KEY_SIZE, pubBytes, 0, CryptoConstants.X25519_KEY_SIZE);
                } else {
                    throw new SecurityException("Invalid public key encoding");
                }
            }

            // 使用 Bouncy Castle 的 X25519 协议
            X25519PrivateKeyParameters privParams = new X25519PrivateKeyParameters(privBytes);
            X25519PublicKeyParameters pubParams2 = new X25519PublicKeyParameters(pubBytes);

            X25519Agreement agreement = new X25519Agreement();
            agreement.init(privParams);
            byte[] sharedSecret = new byte[CryptoConstants.ECDH_SHARED_SECRET_SIZE];
            agreement.calculateAgreement(pubParams2, sharedSecret, 0);

            // 清理敏感数据
            Arrays.fill(privBytes, (byte) 0);
            if (privateKey instanceof Destroyable) {
                try {
                    ((Destroyable) privateKey).destroy();
                } catch (Exception ignored) {
                }
            }

            Log.d(TAG, "ECDH key exchange successful using Bouncy Castle");

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

        // 验证公钥是否是有效的曲线点
        try {
            X25519PublicKeyParameters params = new X25519PublicKeyParameters(encodedKey);
            if (params == null) {
                throw new SecurityException("Invalid X25519 public key");
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
            // 如果输入是原始 32 字节，直接使用
            if (encodedKey.length == CryptoConstants.X25519_KEY_SIZE) {
                Log.d(TAG, "Successfully decoded X25519 public key using Bouncy Castle (raw format)");
                return new X25519PublicKey(encodedKey);
            }

            // 如果是 X509 格式，提取原始密钥字节（最后32字节）
            byte[] rawKey = new byte[CryptoConstants.X25519_KEY_SIZE];
            System.arraycopy(encodedKey, encodedKey.length - CryptoConstants.X25519_KEY_SIZE, rawKey, 0, CryptoConstants.X25519_KEY_SIZE);

            Log.d(TAG, "Successfully decoded X25519 public key using Bouncy Castle (X509 format)");

            return new X25519PublicKey(rawKey);

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
            // 如果输入是原始 32 字节，直接使用
            if (encodedKey.length == CryptoConstants.X25519_KEY_SIZE) {
                Log.d(TAG, "Successfully decoded X25519 private key using Bouncy Castle (raw format)");
                return new X25519PrivateKey(encodedKey);
            }

            // 如果是 PKCS8 格式，提取原始密钥字节（最后32字节）
            byte[] rawKey = new byte[CryptoConstants.X25519_KEY_SIZE];
            System.arraycopy(encodedKey, encodedKey.length - CryptoConstants.X25519_KEY_SIZE, rawKey, 0, CryptoConstants.X25519_KEY_SIZE);

            Log.d(TAG, "Successfully decoded X25519 private key using Bouncy Castle (PKCS8 format)");

            return new X25519PrivateKey(rawKey);

        } catch (Exception e) {
            Log.e(TAG, "Failed to decode X25519 private key", e);
            throw new Exception("Failed to decode private key: " + e.getMessage(), e);
        }
    }

    /**
     * 自定义 X25519 公钥实现
     * 不依赖 JCA 的 XDH 支持
     */
    private static class X25519PublicKey implements PublicKey {
        private static final long serialVersionUID = 1L;
        private final byte[] encoded;

        X25519PublicKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        @Override
        public String getAlgorithm() {
            return "X25519";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            return encoded.clone();
        }

        public byte[] getEncodedNoClone() {
            return encoded;
        }
    }

    /**
     * 自定义 X25519 私钥实现
     * 不依赖 JCA 的 XDH 支持
     */
    private static class X25519PrivateKey implements PrivateKey, Destroyable {
        private static final long serialVersionUID = 1L;
        private byte[] encoded;
        private boolean destroyed = false;

        X25519PrivateKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        @Override
        public String getAlgorithm() {
            return "X25519";
        }

        @Override
        public String getFormat() {
            return "RAW";
        }

        @Override
        public byte[] getEncoded() {
            if (destroyed) {
                throw new IllegalStateException("Key has been destroyed");
            }
            return encoded.clone();
        }

        public byte[] getEncodedNoClone() {
            if (destroyed) {
                throw new IllegalStateException("Key has been destroyed");
            }
            return encoded;
        }

        @Override
        public void destroy() {
            if (!destroyed) {
                Arrays.fill(encoded, (byte) 0);
                destroyed = true;
            }
        }

        @Override
        public boolean isDestroyed() {
            return destroyed;
        }
    }
}