package com.ttt.safevault.crypto;

import android.util.Log;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

import javax.security.auth.Destroyable;

/**
 * Ed25519 签名器 Bouncy Castle 实现（API < 34）
 *
 * 使用 Bouncy Castle 库实现 Ed25519 签名和验证
 * 用于 Android 10-13 (API 29-33) 版本
 *
 * 注意：
 * - 需要初始化 Bouncy Castle Provider
 * - APK 体积会增加约 3-5MB
 * - 性能略低于系统 API，但仍远优于 RSA
 *
 * 重要：此实现使用自定义的 Key 类，不依赖 JCA 的 EdDSA 支持
 */
public class BouncyCastleEd25519Signer implements Ed25519Signer {
    private static final String TAG = "BCEd25519Signer";
    private static boolean providerInitialized = false;

    static {
        // 初始化 Bouncy Castle Provider
        synchronized (BouncyCastleEd25519Signer.class) {
            if (!providerInitialized) {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.insertProviderAt(new BouncyCastleProvider(), 1);
                }
                providerInitialized = true;
                Log.d(TAG, "BouncyCastle Provider initialized for Ed25519");
            }
        }
    }

    @Override
    public KeyPair generateKeyPair() throws Exception {
        try {
            // 使用 Bouncy Castle 生成 Ed25519 密钥对
            Ed25519PrivateKeyParameters privParams = new Ed25519PrivateKeyParameters(new java.security.SecureRandom());
            Ed25519PublicKeyParameters pubParams = privParams.generatePublicKey();

            // 提取密钥字节
            byte[] pubBytes = pubParams.getEncoded();
            byte[] privBytes = privParams.getEncoded();

            // 验证密钥大小
            if (pubBytes.length != CryptoConstants.ED25519_PUBLIC_KEY_SIZE) {
                throw new SecurityException("Invalid public key size: " + pubBytes.length);
            }
            if (privBytes.length != CryptoConstants.ED25519_PRIVATE_KEY_SIZE) {
                throw new SecurityException("Invalid private key size: " + privBytes.length);
            }

            // 使用自定义的 Key 包装类（不依赖 JCA EdDSA）
            Ed25519PublicKey publicKey = new Ed25519PublicKey(pubBytes);
            Ed25519PrivateKey privateKey = new Ed25519PrivateKey(privBytes);

            Log.d(TAG, "Generated Ed25519 key pair using Bouncy Castle (custom Key classes)");

            return new KeyPair(publicKey, privateKey);

        } catch (Exception e) {
            Log.e(TAG, "Failed to generate Ed25519 key pair", e);
            throw new Exception("Key pair generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("Data and private key cannot be null");
        }

        try {
            // 提取原始私钥字节
            byte[] privBytes;
            if (privateKey instanceof Ed25519PrivateKey) {
                privBytes = ((Ed25519PrivateKey) privateKey).getEncodedNoClone();
            } else {
                byte[] encoded = privateKey.getEncoded();
                if (encoded.length >= CryptoConstants.ED25519_PRIVATE_KEY_SIZE) {
                    privBytes = new byte[CryptoConstants.ED25519_PRIVATE_KEY_SIZE];
                    System.arraycopy(encoded, encoded.length - CryptoConstants.ED25519_PRIVATE_KEY_SIZE, privBytes, 0, CryptoConstants.ED25519_PRIVATE_KEY_SIZE);
                } else {
                    throw new SecurityException("Invalid private key encoding");
                }
            }

            // 使用 Bouncy Castle 签名
            Ed25519PrivateKeyParameters privParams = new Ed25519PrivateKeyParameters(privBytes);
            org.bouncycastle.crypto.signers.Ed25519Signer bcSigner = new org.bouncycastle.crypto.signers.Ed25519Signer();
            bcSigner.init(true, privParams);
            bcSigner.update(data, 0, data.length);

            byte[] signature = bcSigner.generateSignature();

            // 清理敏感数据
            Arrays.fill(privBytes, (byte) 0);
            if (privateKey instanceof Destroyable) {
                try {
                    ((Destroyable) privateKey).destroy();
                } catch (Exception ignored) {
                }
            }

            Log.d(TAG, "Successfully signed data using Bouncy Castle: " + data.length + " bytes -> " + signature.length + " bytes");

            return signature;

        } catch (Exception e) {
            Log.e(TAG, "Failed to sign data", e);
            throw new Exception("Signing failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        if (data == null || signature == null || publicKey == null) {
            throw new IllegalArgumentException("Data, signature, and public key cannot be null");
        }

        // 验证签名大小
        if (signature.length != CryptoConstants.ED25519_SIGNATURE_SIZE) {
            Log.w(TAG, "Invalid signature size: " + signature.length + " (expected " + CryptoConstants.ED25519_SIGNATURE_SIZE + ")");
            return false;
        }

        try {
            // 提取原始公钥字节
            byte[] pubBytes;
            if (publicKey instanceof Ed25519PublicKey) {
                pubBytes = ((Ed25519PublicKey) publicKey).getEncodedNoClone();
            } else {
                byte[] encoded = publicKey.getEncoded();
                if (encoded.length >= CryptoConstants.ED25519_PUBLIC_KEY_SIZE) {
                    pubBytes = new byte[CryptoConstants.ED25519_PUBLIC_KEY_SIZE];
                    System.arraycopy(encoded, encoded.length - CryptoConstants.ED25519_PUBLIC_KEY_SIZE, pubBytes, 0, CryptoConstants.ED25519_PUBLIC_KEY_SIZE);
                } else {
                    throw new SecurityException("Invalid public key encoding");
                }
            }

            // 使用 Bouncy Castle 验证
            Ed25519PublicKeyParameters pubParams = new Ed25519PublicKeyParameters(pubBytes);
            org.bouncycastle.crypto.signers.Ed25519Signer bcVerifier = new org.bouncycastle.crypto.signers.Ed25519Signer();
            bcVerifier.init(false, pubParams);
            bcVerifier.update(data, 0, data.length);

            boolean valid = bcVerifier.verifySignature(signature);

            Log.d(TAG, "Signature verification using Bouncy Castle: " + (valid ? "valid" : "invalid"));

            return valid;

        } catch (Exception e) {
            Log.e(TAG, "Failed to verify signature", e);
            throw new Exception("Verification failed: " + e.getMessage(), e);
        }
    }

    @Override
    public PublicKey decodePublicKey(byte[] encodedKey) throws Exception {
        if (encodedKey == null || encodedKey.length == 0) {
            throw new IllegalArgumentException("Encoded key cannot be null or empty");
        }

        try {
            // 如果输入是原始 32 字节，直接使用
            if (encodedKey.length == CryptoConstants.ED25519_PUBLIC_KEY_SIZE) {
                Log.d(TAG, "Successfully decoded Ed25519 public key using Bouncy Castle (raw format)");
                return new Ed25519PublicKey(encodedKey);
            }

            // 如果是 X509 格式，提取原始密钥字节（最后32字节）
            byte[] rawKey = new byte[CryptoConstants.ED25519_PUBLIC_KEY_SIZE];
            System.arraycopy(encodedKey, encodedKey.length - CryptoConstants.ED25519_PUBLIC_KEY_SIZE, rawKey, 0, CryptoConstants.ED25519_PUBLIC_KEY_SIZE);

            Log.d(TAG, "Successfully decoded Ed25519 public key using Bouncy Castle (X509 format)");

            return new Ed25519PublicKey(rawKey);

        } catch (Exception e) {
            Log.e(TAG, "Failed to decode Ed25519 public key", e);
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
            if (encodedKey.length == CryptoConstants.ED25519_PRIVATE_KEY_SIZE) {
                Log.d(TAG, "Successfully decoded Ed25519 private key using Bouncy Castle (raw format)");
                return new Ed25519PrivateKey(encodedKey);
            }

            // 如果是 PKCS8 格式，提取原始密钥字节（最后32字节）
            byte[] rawKey = new byte[CryptoConstants.ED25519_PRIVATE_KEY_SIZE];
            System.arraycopy(encodedKey, encodedKey.length - CryptoConstants.ED25519_PRIVATE_KEY_SIZE, rawKey, 0, CryptoConstants.ED25519_PRIVATE_KEY_SIZE);

            Log.d(TAG, "Successfully decoded Ed25519 private key using Bouncy Castle (PKCS8 format)");

            return new Ed25519PrivateKey(rawKey);

        } catch (Exception e) {
            Log.e(TAG, "Failed to decode Ed25519 private key", e);
            throw new Exception("Failed to decode private key: " + e.getMessage(), e);
        }
    }

    @Override
    public void validatePublicKey(byte[] encodedKey) throws Exception {
        if (encodedKey == null) {
            throw new SecurityException("Public key cannot be null");
        }

        // Ed25519 公钥大小可以是 32 字节（原始）或 44 字节（X509 编码）
        int minLength = CryptoConstants.ED25519_PUBLIC_KEY_SIZE;
        if (encodedKey.length < minLength && encodedKey.length != 44) {
            throw new SecurityException("Invalid public key size: " + encodedKey.length);
        }

        // 尝试验证
        try {
            byte[] pubBytes = encodedKey;
            if (encodedKey.length == 44) {
                pubBytes = new byte[CryptoConstants.ED25519_PUBLIC_KEY_SIZE];
                System.arraycopy(encodedKey, 12, pubBytes, 0, CryptoConstants.ED25519_PUBLIC_KEY_SIZE);
            }

            Ed25519PublicKeyParameters params = new Ed25519PublicKeyParameters(pubBytes);
            if (params == null) {
                throw new SecurityException("Invalid Ed25519 public key");
            }

            Log.d(TAG, "Public key validation successful");

        } catch (Exception e) {
            Log.e(TAG, "Public key validation failed", e);
            throw new SecurityException("Invalid Ed25519 public key: " + e.getMessage(), e);
        }
    }

    @Override
    public void validateSignatureSize(byte[] signature) throws Exception {
        if (signature == null) {
            throw new SecurityException("Signature cannot be null");
        }

        if (signature.length != CryptoConstants.ED25519_SIGNATURE_SIZE) {
            throw new SecurityException("Invalid signature size: " + signature.length + " (expected " + CryptoConstants.ED25519_SIGNATURE_SIZE + ")");
        }
    }

    /**
     * 自定义 Ed25519 公钥实现
     * 不依赖 JCA 的 EdDSA 支持
     */
    private static class Ed25519PublicKey implements PublicKey {
        private static final long serialVersionUID = 1L;
        private final byte[] encoded;

        Ed25519PublicKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        @Override
        public String getAlgorithm() {
            return "Ed25519";
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
     * 自定义 Ed25519 私钥实现
     * 不依赖 JCA 的 EdDSA 支持
     */
    private static class Ed25519PrivateKey implements PrivateKey, Destroyable {
        private static final long serialVersionUID = 1L;
        private byte[] encoded;
        private boolean destroyed = false;

        Ed25519PrivateKey(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        @Override
        public String getAlgorithm() {
            return "Ed25519";
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