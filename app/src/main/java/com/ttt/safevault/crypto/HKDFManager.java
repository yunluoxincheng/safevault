package com.ttt.safevault.crypto;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * HKDF (HMAC-based Key Derivation Function) 管理器
 *
 * 实现 RFC 5869 HKDF 规范，使用 HMAC-SHA256 从共享密钥派生 AES 密钥
 *
 * HKDF 由两部分组成：
 * 1. Extract: 将共享密钥和可选的 salt 提取为固定长度的伪随机密钥
 * 2. Expand: 从提取的密钥和 info 参数派生任意长度的密钥材料
 *
 * 用于 X25519 ECDH 共享密钥的密钥派生，添加身份绑定以防止密钥混淆攻击
 */
public class HKDFManager {
    private static final String TAG = "HKDFManager";

    // 单例实例
    private static volatile HKDFManager instance;

    /**
     * 获取 HKDF 管理器实例
     */
    public static HKDFManager getInstance() {
        if (instance != null) {
            return instance;
        }

        synchronized (HKDFManager.class) {
            if (instance != null) {
                return instance;
            }
            instance = new HKDFManager();
            return instance;
        }
    }

    /**
     * 从 ECDH 共享密钥派生 AES 密钥（带身份绑定）
     *
     * HKDF 流程：
     * 1. Extract: 使用可选的 salt 从共享密钥提取 PRK
     * 2. Expand: 使用 info 参数（包含双方身份）从 PRK 扩展出 AES 密钥
     *
     * @param sharedSecret ECDH 共享密钥（32 字节）
     * @param senderId 发送方用户 ID
     * @param receiverId 接收方用户 ID
     * @return AES-256 密钥（32 字节）
     * @throws Exception 如果密钥派生失败
     */
    public SecretKey deriveAESKey(byte[] sharedSecret, String senderId, String receiverId) throws Exception {
        if (sharedSecret == null || sharedSecret.length == 0) {
            throw new IllegalArgumentException("Shared secret cannot be null or empty");
        }

        if (senderId == null || senderId.isEmpty()) {
            throw new IllegalArgumentException("Sender ID cannot be null or empty");
        }

        if (receiverId == null || receiverId.isEmpty()) {
            throw new IllegalArgumentException("Receiver ID cannot be null or empty");
        }

        try {
            // 构建 info 参数：混合双方身份（防止密钥混淆攻击）
            String info = CryptoConstants.HKDF_INFO_PREFIX + senderId + "\0" + receiverId + "\0";
            byte[] infoBytes = info.getBytes(StandardCharsets.UTF_8);

            Log.d(TAG, "Deriving AES key from shared secret: " + sharedSecret.length + " bytes");
            Log.d(TAG, "Info parameter: " + info);

            // 1. Extract 阶段：使用 salt 从共享密钥提取 PRK
            byte[] salt = generateSalt(senderId, receiverId);
            byte[] prk = hmacExtract(salt, sharedSecret);

            // 清理中间数据
            Arrays.fill(sharedSecret, (byte) 0);
            Arrays.fill(salt, (byte) 0);

            // 2. Expand 阶段：从 PRK 扩展出 AES 密钥
            byte[] okm = hmacExpand(prk, infoBytes, CryptoConstants.HKDF_OUTPUT_SIZE);

            // 清理中间数据
            Arrays.fill(prk, (byte) 0);

            // 创建 AES 密钥
            SecretKey aesKey = new SecretKeySpec(okm, "AES");

            Log.d(TAG, "Successfully derived AES-256 key");

            return aesKey;

        } catch (Exception e) {
            Log.e(TAG, "Failed to derive AES key", e);
            throw new Exception("Key derivation failed: " + e.getMessage(), e);
        }
    }

    /**
     * HKDF Extract 阶段
     *
     * HMAC-Hash(salt, IKM)
     *
     * @param salt 可选的 salt（可以为 null，将使用 Hash 长度的零）
     * @param ikm 输入密钥材料（Input Key Material）
     * @return 伪随机密钥（PRK）
     * @throws NoSuchAlgorithmException 如果哈希算法不支持
     * @throws InvalidKeyException 如果密钥无效
     */
    private byte[] hmacExtract(byte[] salt, byte[] ikm) throws NoSuchAlgorithmException, InvalidKeyException {
        // 如果 salt 为 null，使用 Hash 长度的零
        if (salt == null || salt.length == 0) {
            salt = new byte[getHashLength()];
        }

        return hmac(salt, ikm);
    }

    /**
     * HKDF Expand 阶段
     *
     * HMAC-Hash(PRK, T(0) | info | 0x01) | HMAC-Hash(PRK, T(1) | info | 0x02) | ...
     *
     * @param prk 伪随机密钥
     * @param info 可选的上下文信息
     * @param length 期望的输出长度（字节）
     * @return 输出密钥材料（OKM）
     * @throws NoSuchAlgorithmException 如果哈希算法不支持
     * @throws InvalidKeyException 如果密钥无效
     */
    private byte[] hmacExpand(byte[] prk, byte[] info, int length) throws NoSuchAlgorithmException, InvalidKeyException {
        if (prk.length < getHashLength()) {
            throw new IllegalArgumentException("PRK too short");
        }

        int hashLen = getHashLength();
        int n = (length + hashLen - 1) / hashLen; // ceil(length / hashLen)

        if (n > 255) {
            throw new IllegalArgumentException("Length too large");
        }

        byte[] okm = new byte[length];
        byte[] t = new byte[0]; // T(0) = 空字符串

        for (int i = 1; i <= n; i++) {
            // 计算当前块：T(i) = HMAC-Hash(PRK, T(i-1) | info | i)
            byte[] input = new byte[t.length + info.length + 1];
            System.arraycopy(t, 0, input, 0, t.length);
            System.arraycopy(info, 0, input, t.length, info.length);
            input[input.length - 1] = (byte) i;

            t = hmac(prk, input);

            // 将当前块复制到输出
            int copyLength = Math.min(hashLen, length - (i - 1) * hashLen);
            System.arraycopy(t, 0, okm, (i - 1) * hashLen, copyLength);
        }

        // 清理敏感数据
        Arrays.fill(t, (byte) 0);

        return okm;
    }

    /**
     * HMAC 计算
     *
     * @param key 密钥
     * @param data 数据
     * @return HMAC 值
     * @throws NoSuchAlgorithmException 如果哈希算法不支持
     * @throws InvalidKeyException 如果密钥无效
     */
    private byte[] hmac(byte[] key, byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(CryptoConstants.HKDF_HASH_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(key, CryptoConstants.HKDF_HASH_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(data);
    }

    /**
     * 获取哈希算法输出长度（字节）
     */
    private int getHashLength() {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.getDigestLength();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是标准算法，不应该抛出异常
            return 32;
        }
    }

    /**
     * 从双方身份生成 salt
     *
     * 使用发送方和接收方 ID 的混合作为 salt，增加密钥派生的随机性
     *
     * @param senderId 发送方用户 ID
     * @param receiverId 接收方用户 ID
     * @return salt 字节数组
     */
    private byte[] generateSalt(String senderId, String receiverId) {
        // 确保身份参数的顺序一致，防止密钥混淆
        // 使用字典序确保不同设备得到相同结果
        String combined;
        if (senderId.compareTo(receiverId) < 0) {
            combined = senderId + ":" + receiverId;
        } else {
            combined = receiverId + ":" + senderId;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            Log.d(TAG, "Generated salt from identities: " + combined);

            return hash;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是标准算法，不应该抛出异常
            Log.e(TAG, "Failed to generate salt", e);
            return new byte[32]; // 回退到零 salt
        }
    }

    /**
     * 从共享密钥派生密钥（不带身份绑定）
     *
     * 用于简单场景，不包含身份绑定
     *
     * @param sharedSecret ECDH 共享密钥
     * @param length 期望的密钥长度（字节）
     * @return 派生的密钥
     * @throws Exception 如果密钥派生失败
     */
    public byte[] deriveKey(byte[] sharedSecret, int length) throws Exception {
        if (sharedSecret == null || sharedSecret.length == 0) {
            throw new IllegalArgumentException("Shared secret cannot be null or empty");
        }

        if (length <= 0) {
            throw new IllegalArgumentException("Length must be positive");
        }

        try {
            // 不使用 salt，提取 PRK
            byte[] prk = hmacExtract(null, sharedSecret);

            // 不使用 info，扩展密钥
            byte[] okm = hmacExpand(prk, new byte[0], length);

            // 清理中间数据
            Arrays.fill(sharedSecret, (byte) 0);
            Arrays.fill(prk, (byte) 0);

            return okm;

        } catch (Exception e) {
            Log.e(TAG, "Failed to derive key", e);
            throw new Exception("Key derivation failed: " + e.getMessage(), e);
        }
    }
}