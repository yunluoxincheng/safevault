package org.ttt.safevaultbackend.util;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 密钥生成工具类
 */
public class KeyGenerator {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 生成 RSA-4096 密钥对
     */
    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(4096);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 生成 ECDH-P-521 密钥对
     */
    public static KeyPair generateECDHKeyPair() throws Exception {
        java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec("P-521");
        keyPairGenerator.initialize(ecSpec, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * 生成 AES-256 密钥
     */
    public static SecretKey generateAESKey() throws NoSuchAlgorithmException {
        javax.crypto.KeyGenerator keyGenerator = javax.crypto.KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    /**
     * 生成随机 IV（用于 AES-GCM）
     */
    public static byte[] generateRandomIV() {
        byte[] iv = new byte[12]; // GCM 推荐 12 字节
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    /**
     * 将密钥转换为 Base64 字符串
     */
    public static String keyToBase64(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 从 Base64 字符串还原公钥
     */
    public static PublicKey publicKeyFromBase64(String base64Key, String algorithm) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return java.security.KeyFactory.getInstance(algorithm)
                .generatePublic(new java.security.spec.X509EncodedKeySpec(keyBytes));
    }

    /**
     * 从 Base64 字符串还原私钥
     */
    public static PrivateKey privateKeyFromBase64(String base64Key, String algorithm) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        return java.security.KeyFactory.getInstance(algorithm)
                .generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(keyBytes));
    }

    /**
     * 从字节数组创建 AES 密钥
     */
    public static SecretKey createAESKeyFromBytes(byte[] keyBytes) {
        return new SecretKeySpec(keyBytes, "AES");
    }
}
