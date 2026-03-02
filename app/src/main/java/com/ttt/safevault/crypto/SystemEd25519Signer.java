package com.ttt.safevault.crypto;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Ed25519 签名器系统 API 实现（API 34+）
 *
 * 使用 Android 系统 API 实现 Ed25519 签名和验证
 * 仅在 Android 14 (API 34) 及以上版本可用
 *
 * 优势：
 * - 原生支持，无需额外库
 * - 性能最优
 * - APK 体积最小
 */
@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class SystemEd25519Signer implements Ed25519Signer {
    private static final String TAG = "SystemEd25519Signer";
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    @Override
    public KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(CryptoConstants.ED25519_ALGORITHM);
        kpg.initialize(new NamedParameterSpec(CryptoConstants.ED25519_CURVE));
        KeyPair keyPair = kpg.generateKeyPair();

        Log.d(TAG, "Generated Ed25519 key pair using system API");

        // 验证密钥大小
        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();

        Log.d(TAG, "Ed25519 key pair generated successfully: public=" + publicKeyBytes.length + " bytes, private=" + privateKeyBytes.length + " bytes");

        return keyPair;
    }

    @Override
    public byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        if (data == null || privateKey == null) {
            throw new IllegalArgumentException("Data and private key cannot be null");
        }

        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);

            byte[] sign = signature.sign();

            // 验证签名大小
            if (sign.length != CryptoConstants.ED25519_SIGNATURE_SIZE) {
                throw new SecurityException("Invalid signature size: " + sign.length + " (expected " + CryptoConstants.ED25519_SIGNATURE_SIZE + ")");
            }

            Log.d(TAG, "Successfully signed data: " + data.length + " bytes -> " + sign.length + " bytes signature");

            return sign;

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
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
            sig.initVerify(publicKey);
            sig.update(data);

            boolean valid = sig.verify(signature);

            Log.d(TAG, "Signature verification: " + (valid ? "valid" : "invalid"));

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
            KeyFactory kf = KeyFactory.getInstance(CryptoConstants.ED25519_ALGORITHM);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(encodedKey);
            PublicKey publicKey = kf.generatePublic(spec);

            Log.d(TAG, "Successfully decoded Ed25519 public key: " + encodedKey.length + " bytes");

            return publicKey;

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
            KeyFactory kf = KeyFactory.getInstance(CryptoConstants.ED25519_ALGORITHM);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(encodedKey);
            PrivateKey privateKey = kf.generatePrivate(spec);

            Log.d(TAG, "Successfully decoded Ed25519 private key: " + encodedKey.length + " bytes");

            return privateKey;

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
        // X509 格式：前缀 + 32 字节密钥
        int minLength = CryptoConstants.ED25519_PUBLIC_KEY_SIZE;
        if (encodedKey.length < minLength && encodedKey.length != 44) {
            throw new SecurityException("Invalid public key size: " + encodedKey.length);
        }

        // 尝试解码以验证有效性
        try {
            decodePublicKey(encodedKey);
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
}