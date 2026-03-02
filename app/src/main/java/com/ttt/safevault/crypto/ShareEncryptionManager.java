package com.ttt.safevault.crypto;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.EncryptedSharePacketV3;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.UserKeyInfo;
import com.ttt.safevault.analytics.AnalyticsManager;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 分享加密管理器
 * 处理端到端加密分享的加密、解密和签名验证
 *
 * 版本 2.0 使用混合加密方案（RSA + AES）：
 * 1. 用接收方 RSA 公钥加密随机生成的 AES-256 密钥
 * 2. 用 AES-256-GCM 加密实际的分享数据
 * 3. 保留数字签名验证发送方身份
 *
 * 版本 3.0 使用现代椭圆曲线算法（X25519/Ed25519）：
 * 1. 使用 Ephemeral Diffie-Hellman 密钥交换
 * 2. HKDF 派生 AES 密钥（身份绑定）
 * 3. Ed25519 签名验证发送方身份
 */
public class ShareEncryptionManager {
    private static final String TAG = "ShareEncryptionManager";

    private Context context;
    private AnalyticsManager analytics;

    /**
     * 设置 Android 上下文（用于初始化依赖）
     *
     * @param context Android 上下文
     */
    public void setContext(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.analytics = AnalyticsManager.getInstance(context);
    }

    /**
     * 用接收方公钥加密分享数据
     *
     * @param data 分享数据包
     * @param receiverPublicKey 接收方的RSA公钥
     * @return Base64编码的加密数据
     */
    @Nullable
    public String encryptShare(
            @NonNull ShareDataPacket data,
            @NonNull PublicKey receiverPublicKey
    ) {
        try {
            // 1. 将数据包序列化为JSON
            String json = serializeShareDataPacket(data);
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            Log.d(TAG, "Encrypting share data, size: " + dataBytes.length + " bytes");

            // 2. 检查数据大小（RSA 2048 最多加密 245 字节）
            int maxBlockSize = getMaxBlockSize(receiverPublicKey);
            if (dataBytes.length > maxBlockSize) {
                Log.e(TAG, "Data too large for RSA encryption: " + dataBytes.length +
                       " > " + maxBlockSize);
                return null;
            }

            // 3. 使用 RSA-OAEP 加密
            Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey);

            byte[] encrypted = cipher.doFinal(dataBytes);

            // 4. Base64 编码
            String result = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            Log.d(TAG, "Successfully encrypted share data");

            return result;

        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt share data", e);
            return null;
        }
    }

    /**
     * 用自己的私钥解密分享数据
     *
     * @param encryptedData Base64编码的加密数据
     * @param privateKey 自己的RSA私钥
     * @return 解密后的分享数据包
     */
    @Nullable
    public ShareDataPacket decryptShare(
            @NonNull String encryptedData,
            @NonNull PrivateKey privateKey
    ) {
        try {
            // 1. Base64 解码
            byte[] encryptedBytes = Base64.decode(encryptedData, Base64.NO_WRAP);

            Log.d(TAG, "Decrypting share data, size: " + encryptedBytes.length + " bytes");

            // 2. 使用 RSA-OAEP 解密
            Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decrypted = cipher.doFinal(encryptedBytes);

            // 3. 反序列化
            String json = new String(decrypted, StandardCharsets.UTF_8);
            ShareDataPacket result = deserializeShareDataPacket(json);

            Log.d(TAG, "Successfully decrypted share data");
            return result;

        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt share data", e);
            return null;
        }
    }

    /**
     * 用自己的私钥签名分享数据
     *
     * @param data 分享数据包
     * @param privateKey 自己的RSA私钥
     * @return Base64编码的签名数据
     */
    @Nullable
    public String signShare(
            @NonNull ShareDataPacket data,
            @NonNull PrivateKey privateKey
    ) {
        try {
            // 1. 序列化数据包
            String json = serializeShareDataPacket(data);
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            // 2. 签名
            Signature signature = Signature.getInstance(CryptoConstants.RSA_SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(dataBytes);

            byte[] sign = signature.sign();

            // 3. Base64 编码
            String result = Base64.encodeToString(sign, Base64.NO_WRAP);
            Log.d(TAG, "Successfully signed share data");

            return result;

        } catch (Exception e) {
            Log.e(TAG, "Failed to sign share data", e);
            return null;
        }
    }

    /**
     * 验证签名（用发送方公钥）
     *
     * @param data 分享数据包
     * @param signature Base64编码的签名数据
     * @param senderPublicKey 发送方的RSA公钥
     * @return true表示签名有效
     */
    public boolean verifySignature(
            @NonNull ShareDataPacket data,
            @NonNull String signature,
            @NonNull PublicKey senderPublicKey
    ) {
        try {
            // 1. 解码签名
            byte[] signatureBytes = Base64.decode(signature, Base64.NO_WRAP);

            // 2. 序列化数据包
            String json = serializeShareDataPacket(data);
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);

            // 3. 验证签名
            Signature sig = Signature.getInstance(CryptoConstants.RSA_SIGNATURE_ALGORITHM);
            sig.initVerify(senderPublicKey);
            sig.update(dataBytes);

            boolean valid = sig.verify(signatureBytes);
            Log.d(TAG, "Signature verification: " + (valid ? "valid" : "invalid"));

            return valid;

        } catch (Exception e) {
            Log.e(TAG, "Failed to verify signature", e);
            return false;
        }
    }

    /**
     * 创建加密分享包（混合加密：RSA + AES）
     * 版本 2.0 加密流程：
     * 1. 序列化数据包为 JSON
     * 2. 生成随机 AES-256 密钥和 IV
     * 3. 用 AES-GCM 加密数据
     * 4. 用接收方 RSA 公钥加密 AES 密钥
     * 5. 用发送方 RSA 私钥签名原始数据
     * 6. 组装 EncryptedSharePacket (v2.0)
     *
     * @param data 分享数据包
     * @param receiverPublicKey 接收方公钥
     * @param senderPrivateKey 发送方私钥
     * @return 加密分享包
     */
    @Nullable
    public EncryptedSharePacket createEncryptedPacket(
            @NonNull ShareDataPacket data,
            @NonNull PublicKey receiverPublicKey,
            @NonNull PrivateKey senderPrivateKey
    ) {
        try {
            // 1. 序列化数据包为 JSON
            String json = serializeShareDataPacket(data);
            byte[] dataBytes = json.getBytes(StandardCharsets.UTF_8);
            Log.d(TAG, "Creating encrypted packet, data size: " + dataBytes.length + " bytes");

            // 2. 生成随机 AES-256 密钥和 IV
            SecretKey aesKey = generateAESKey();
            if (aesKey == null) {
                Log.e(TAG, "Failed to generate AES key");
                return null;
            }
            byte[] iv = generateIV();
            if (iv == null) {
                Log.e(TAG, "Failed to generate IV");
                return null;
            }

            // 3. 用 AES-GCM 加密数据
            String encryptedData = encryptWithAES(dataBytes, aesKey, iv);
            if (encryptedData == null) {
                Log.e(TAG, "Failed to encrypt data with AES");
                return null;
            }

            // 4. 用接收方 RSA 公钥加密 AES 密钥
            String encryptedAESKey = encryptAESKeyWithRSA(aesKey, receiverPublicKey);
            if (encryptedAESKey == null) {
                Log.e(TAG, "Failed to encrypt AES key with RSA");
                return null;
            }

            // 5. 用发送方 RSA 私钥签名原始数据
            String signature = signShare(data, senderPrivateKey);
            if (signature == null) {
                Log.e(TAG, "Failed to sign share data");
                return null;
            }

            // 6. 组装 EncryptedSharePacket (v2.0)
            EncryptedSharePacket packet = new EncryptedSharePacket();
            packet.setVersion("2.0");
            packet.setEncryptedData(encryptedData);
            packet.setEncryptedAESKey(encryptedAESKey);
            packet.setIv(Base64.encodeToString(iv, Base64.NO_WRAP));
            packet.setSignature(signature);
            packet.setSenderId(data.senderId);
            packet.setCreatedAt(data.createdAt);
            packet.setExpireAt(data.expireAt);

            Log.d(TAG, "Successfully created encrypted packet v2.0");
            return packet;

        } catch (Exception e) {
            Log.e(TAG, "Failed to create encrypted packet", e);
            return null;
        }
    }

    /**
     * 解开加密分享包（混合加密：RSA + AES）
     * 版本 2.0 解密流程：
     * 1. 验证版本必须是 "2.0"
     * 2. 用接收方 RSA 私钥解密 AES 密钥
     * 3. 用 AES 密钥和 IV 解密数据
     * 4. 反序列化 JSON 得到 ShareDataPacket
     * 5. 用发送方 RSA 公钥验证签名
     *
     * @param packet 加密分享包
     * @param receiverPrivateKey 接收方私钥
     * @param senderPublicKey 发送方公钥
     * @return 分享数据包，如果解密或签名验证失败则返回null
     */
    @Nullable
    public ShareDataPacket openEncryptedPacket(
            @NonNull EncryptedSharePacket packet,
            @NonNull PrivateKey receiverPrivateKey,
            @NonNull PublicKey senderPublicKey
    ) {
        try {
            // 1. 验证版本
            String version = packet.getVersion();
            if (!"2.0".equals(version)) {
                Log.e(TAG, "Unsupported packet version: " + version + " (expected 2.0)");
                return null;
            }

            Log.d(TAG, "Opening encrypted packet v2.0");

            // 2. 用接收方 RSA 私钥解密 AES 密钥
            String encryptedAESKey = packet.getEncryptedAESKey();
            if (encryptedAESKey == null || encryptedAESKey.isEmpty()) {
                Log.e(TAG, "Missing encrypted AES key");
                return null;
            }
            SecretKey aesKey = decryptAESKeyWithRSA(encryptedAESKey, receiverPrivateKey);
            if (aesKey == null) {
                Log.e(TAG, "Failed to decrypt AES key");
                return null;
            }

            // 3. 用 AES 密钥和 IV 解密数据
            String ivBase64 = packet.getIv();
            if (ivBase64 == null || ivBase64.isEmpty()) {
                Log.e(TAG, "Missing IV");
                return null;
            }
            byte[] iv = Base64.decode(ivBase64, Base64.NO_WRAP);

            String encryptedData = packet.getEncryptedData();
            byte[] decryptedBytes = decryptWithAES(encryptedData, aesKey, iv);
            if (decryptedBytes == null) {
                Log.e(TAG, "Failed to decrypt data with AES");
                return null;
            }

            // 4. 反序列化 JSON 得到 ShareDataPacket
            String json = new String(decryptedBytes, StandardCharsets.UTF_8);
            ShareDataPacket data = deserializeShareDataPacket(json);

            // 5. 用发送方 RSA 公钥验证签名
            if (!verifySignature(data, packet.getSignature(), senderPublicKey)) {
                Log.e(TAG, "Signature verification failed");
                return null;
            }

            Log.d(TAG, "Successfully opened encrypted packet v2.0");
            return data;

        } catch (Exception e) {
            Log.e(TAG, "Failed to open encrypted packet", e);
            return null;
        }
    }

    /**
     * 获取RSA密钥的最大加密块大小
     * RSA 2048位密钥最多可加密 245 字节（使用OAEPWithSHA-256AndMGF1Padding）
     */
    private int getMaxBlockSize(PublicKey publicKey) {
        // 使用 RSAKey 接口获取密钥的模数长度（位数）
        int keySizeBits;
        if (publicKey instanceof RSAKey) {
            keySizeBits = ((RSAKey) publicKey).getModulus().bitLength();
        } else {
            // 兼容非RSAKey的情况，使用保守估计
            keySizeBits = 2048; // 默认使用2048位
            Log.w(TAG, "PublicKey is not RSAKey, using default key size: " + keySizeBits);
        }

        // OAEP 开销：42 字节（使用SHA-256）
        // keySizeBits / 8 得到字节数，再减去OAEP开销
        int maxBlockSize = (keySizeBits / 8) - 42;
        Log.d(TAG, "RSA key size: " + keySizeBits + " bits, max block size: " + maxBlockSize + " bytes");
        return maxBlockSize;
    }

    /**
     * 序列化分享数据包为JSON
     */
    @NonNull
    private String serializeShareDataPacket(@NonNull ShareDataPacket data) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"version\":\"").append(escapeJson(data.version)).append("\",");
        json.append("\"senderId\":\"").append(escapeJson(data.senderId)).append("\",");
        json.append("\"senderPublicKey\":\"").append(escapeJson(data.senderPublicKey)).append("\",");
        json.append("\"createdAt\":").append(data.createdAt).append(",");
        json.append("\"expireAt\":").append(data.expireAt).append(",");

        // 权限
        json.append("\"permission\":{");
        json.append("\"canView\":").append(data.permission.isCanView());
        json.append(",\"canSave\":").append(data.permission.isCanSave());
        json.append(",\"isRevocable\":").append(data.permission.isRevocable());
        json.append("},");

        // 密码数据
        json.append("\"password\":{");
        json.append("\"title\":\"").append(escapeJson(data.password.getTitle())).append("\",");
        json.append("\"username\":\"").append(escapeJson(data.password.getUsername())).append("\",");
        json.append("\"password\":\"").append(escapeJson(data.password.getPassword())).append("\",");
        json.append("\"url\":\"").append(escapeJson(data.password.getUrl() != null ? data.password.getUrl() : "")).append("\",");
        json.append("\"notes\":\"").append(escapeJson(data.password.getNotes() != null ? data.password.getNotes() : "")).append("\"");
        json.append("}");

        json.append("}");
        return json.toString();
    }

    /**
     * 从JSON反序列化分享数据包
     */
    @NonNull
    private ShareDataPacket deserializeShareDataPacket(@NonNull String json) {
        // 简化的JSON解析
        ShareDataPacket packet = new ShareDataPacket();

        // 解析基本字段
        packet.version = extractJsonString(json, "version");
        packet.senderId = extractJsonString(json, "senderId");
        packet.senderPublicKey = extractJsonString(json, "senderPublicKey");
        packet.createdAt = extractJsonLong(json, "createdAt");
        packet.expireAt = extractJsonLong(json, "expireAt");

        // 解析权限
        packet.permission = new SharePermission(
            extractJsonBoolean(json, "canView"),
            extractJsonBoolean(json, "canSave"),
            extractJsonBoolean(json, "isRevocable")
        );

        // 解析密码数据（简化）
        com.ttt.safevault.model.PasswordItem password =
            new com.ttt.safevault.model.PasswordItem();
        password.setTitle(extractJsonString(json, "title"));
        password.setUsername(extractJsonString(json, "username"));
        password.setPassword(extractJsonString(json, "password"));
        password.setUrl(extractJsonString(json, "url"));
        password.setNotes(extractJsonString(json, "notes"));
        packet.password = password;

        return packet;
    }

    // JSON 辅助方法
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String extractJsonString(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return "";
        startIndex += searchKey.length();

        int endIndex = startIndex;
        boolean escaped = false;
        while (endIndex < json.length()) {
            char c = json.charAt(endIndex);
            if (!escaped && c == '"') break;
            escaped = (c == '\\' && !escaped);
            endIndex++;
        }

        return json.substring(startIndex, endIndex)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private long extractJsonLong(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return 0;
        startIndex += searchKey.length();

        int endIndex = startIndex;
        while (endIndex < json.length()) {
            char c = json.charAt(endIndex);
            if (c == ',' || c == '}') break;
            endIndex++;
        }

        try {
            return Long.parseLong(json.substring(startIndex, endIndex).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean extractJsonBoolean(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return false;
        startIndex += searchKey.length();

        if (json.substring(startIndex).startsWith("true")) return true;
        return false;
    }

    // ==================== AES 加密方法（版本 2.0） ====================

    /**
     * 生成随机 AES-256 密钥
     * 每次调用生成新的密钥，确保前向安全
     *
     * @return 新生成的 AES 密钥
     */
    @Nullable
    private SecretKey generateAESKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(CryptoConstants.AES_KEY_SIZE, new SecureRandom());
            SecretKey key = keyGenerator.generateKey();
            Log.d(TAG, "Generated AES-256 key");
            return key;
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate AES key", e);
            return null;
        }
    }

    /**
     * 生成随机 GCM 初始化向量（IV）
     * 每次加密必须使用新的 IV，确保安全性
     *
     * @return 12 字节的随机 IV
     */
    @Nullable
    private byte[] generateIV() {
        return CryptoUtils.generateGcmIV();
    }

    /**
     * 使用 AES-256-GCM 加密数据
     * 版本 2.0：使用安全随机填充防止元数据泄露
     *
     * @param data 要加密的数据
     * @param key AES 密钥
     * @param iv 初始化向量（12 字节）
     * @return Base64 编码的加密数据
     */
    @Nullable
    private String encryptWithAES(@NonNull byte[] data, @NonNull SecretKey key, @NonNull byte[] iv) {
        try {
            // 先进行安全随机填充
            byte[] paddedData = SecurePaddingUtil.pad(data);
            Log.d(TAG, "AES encryption: " + data.length + " -> " + paddedData.length + " bytes (padded)");

            Cipher cipher = Cipher.getInstance(CryptoConstants.AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] encrypted = cipher.doFinal(paddedData);
            String result = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            Log.d(TAG, "AES encryption successful, output size: " + result.length() + " chars");
            return result;
        } catch (javax.crypto.AEADBadTagException e) {
            Log.e(TAG, "GCM authentication failed", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "AES encryption failed", e);
            return null;
        }
    }

    /**
     * 使用 AES-256-GCM 解密数据
     * 版本 2.0：解密后去除安全随机填充
     *
     * @param encryptedBase64 Base64 编码的加密数据
     * @param key AES 密钥
     * @param iv 初始化向量（12 字节）
     * @return 解密后的原始数据
     */
    @Nullable
    private byte[] decryptWithAES(@NonNull String encryptedBase64, @NonNull SecretKey key, @NonNull byte[] iv) {
        try {
            byte[] encrypted = Base64.decode(encryptedBase64, Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance(CryptoConstants.AES_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(CryptoConstants.GCM_TAG_SIZE, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            byte[] decrypted = cipher.doFinal(encrypted);

            // 去除安全随机填充
            byte[] unpadded = SecurePaddingUtil.unpad(decrypted);
            Log.d(TAG, "AES decryption successful: " + encrypted.length + " -> "
                    + decrypted.length + " -> " + unpadded.length + " bytes (unpadded)");
            return unpadded;
        } catch (javax.crypto.AEADBadTagException e) {
            Log.e(TAG, "GCM authentication tag verification failed - data may be tampered", e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "AES decryption failed", e);
            return null;
        }
    }

    /**
     * 使用接收方 RSA 公钥加密 AES 密钥
     *
     * @param aesKey AES 密钥（32 字节）
     * @param receiverPublicKey 接收方的 RSA 公钥
     * @return Base64 编码的加密密钥
     */
    @Nullable
    private String encryptAESKeyWithRSA(@NonNull SecretKey aesKey, @NonNull PublicKey receiverPublicKey) {
        try {
            byte[] keyBytes = aesKey.getEncoded();
            Log.d(TAG, "Encrypting AES key with RSA, size: " + keyBytes.length + " bytes");

            // 验证密钥大小（32 字节 = 256 位，RSA-2048 可以轻松处理）
            if (keyBytes.length != CryptoConstants.X25519_KEY_SIZE) {
                Log.e(TAG, "Invalid AES key size: " + keyBytes.length + " (expected " + CryptoConstants.X25519_KEY_SIZE + ")");
                return null;
            }

            Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey);

            byte[] encrypted = cipher.doFinal(keyBytes);
            String result = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            Log.d(TAG, "Successfully encrypted AES key with RSA");
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt AES key with RSA", e);
            return null;
        }
    }

    /**
     * 使用接收方 RSA 私钥解密 AES 密钥
     *
     * @param encryptedKeyBase64 Base64 编码的加密密钥
     * @param receiverPrivateKey 接收方的 RSA 私钥
     * @return 解密后的 AES 密钥
     */
    @Nullable
    private SecretKey decryptAESKeyWithRSA(@NonNull String encryptedKeyBase64, @NonNull PrivateKey receiverPrivateKey) {
        try {
            byte[] encrypted = Base64.decode(encryptedKeyBase64, Base64.NO_WRAP);
            Log.d(TAG, "Decrypting AES key with RSA, size: " + encrypted.length + " bytes");

            Cipher cipher = Cipher.getInstance(CryptoConstants.RSA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, receiverPrivateKey);

            byte[] keyBytes = cipher.doFinal(encrypted);

            // 验证解密后的密钥大小
            if (keyBytes.length != CryptoConstants.X25519_KEY_SIZE) {
                Log.e(TAG, "Invalid decrypted AES key size: " + keyBytes.length + " (expected " + CryptoConstants.X25519_KEY_SIZE + ")");
                return null;
            }

            SecretKey key = new SecretKeySpec(keyBytes, "AES");
            Log.d(TAG, "Successfully decrypted AES key with RSA");
            return key;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt AES key with RSA", e);
            return null;
        }
    }

    // ==================== 版本 3.0 方法（X25519/Ed25519）====================

    /**
     * 检测双方支持的协议版本
     *
     * 版本协商矩阵：
     * - 双方都支持 v3.0 → 使用 v3.0
     * - 任一方不支持 v3.0 → 回退到 v2.0
     *
     * @param senderKeyInfo 发送方密钥信息
     * @param receiverKeyInfo 接收方密钥信息
     * @return 协议版本字符串（"3.0" 或 "2.0"）
     */
    @NonNull
    public String detectProtocolVersion(@NonNull UserKeyInfo senderKeyInfo, @NonNull UserKeyInfo receiverKeyInfo) {
        // 检查双方是否都支持 v3.0
        boolean senderSupportsV3 = senderKeyInfo.supportsV3();
        boolean receiverSupportsV3 = receiverKeyInfo.supportsV3();

        if (senderSupportsV3 && receiverSupportsV3) {
            Log.d(TAG, "Both parties support v3.0, using X25519/Ed25519");
            return CryptoConstants.PROTOCOL_VERSION_V3;
        }

        // 回退到 v2.0
        Log.d(TAG, "Falling back to v2.0 (RSA): senderV3=" + senderSupportsV3 + ", receiverV3=" + receiverSupportsV3);
        return CryptoConstants.PROTOCOL_VERSION_V2;
    }

    /**
     * 创建加密分享包 - 版本 3.0（X25519/Ed25519）
     *
     * v3.0 加密流程：
     * 1. 生成 ephemeral X25519 密钥对（每次分享都生成新的）
     * 2. 用 ephemeral 私钥 + 接收方长期 X25519 公钥 → ECDH 共享密钥
     * 3. 用 HKDF 从共享密钥派生 AES 密钥（混合双方身份）
     * 4. 序列化 ShareDataPacket 为 JSON
     * 5. 对原始 JSON 签名（Ed25519）
     * 6. 用 AES-256-GCM 加密 JSON 字节数组
     * 7. 组装 EncryptedSharePacketV3
     *
     * @param data 分享数据包
     * @param receiverX25519PublicKey 接收方长期 X25519 公钥
     * @param senderEd25519PrivateKey 发送方 Ed25519 私钥（用于签名）
     * @param senderId 发送方用户 ID
     * @param receiverId 接收方用户 ID
     * @param expireAt 过期时间（毫秒，0 表示永不过期）
     * @param context Android 上下文
     * @return 加密分享包 v3.0，如果失败则返回 null
     */
    @Nullable
    public EncryptedSharePacketV3 createEncryptedPacketV3(
            @NonNull ShareDataPacket data,
            @NonNull java.security.PublicKey receiverX25519PublicKey,
            @NonNull java.security.PrivateKey senderEd25519PrivateKey,
            @NonNull String senderId,
            @NonNull String receiverId,
            long expireAt,
            @NonNull Context context
    ) {
        long startTime = System.currentTimeMillis();
        try {
            // 确保上下文已设置
            if (this.context == null) {
                setContext(context);
            }

            // 获取加密管理器实例
            X25519KeyManager x25519KeyManager = X25519KeyManagerFactory.create(context);
            Ed25519Signer ed25519Signer = Ed25519SignerFactory.create(context);
            HKDFManager hkdfManager = HKDFManager.getInstance();

            // 1. 生成 ephemeral X25519 密钥对（每次分享都生成新的）
            java.security.KeyPair ephemeralKeyPair = x25519KeyManager.generateKeyPair();
            Log.d(TAG, "Generated ephemeral X25519 key pair");

            // 2. ECDH 密钥交换
            byte[] sharedSecret = x25519KeyManager.performECDH(
                    ephemeralKeyPair.getPrivate(),
                    receiverX25519PublicKey
            );
            Log.d(TAG, "ECDH key exchange successful, shared secret: " + sharedSecret.length + " bytes");

            // 3. HKDF 派生 AES 密钥（混合双方身份，防密钥混淆攻击）
            javax.crypto.SecretKey aesKey = hkdfManager.deriveAESKey(sharedSecret, senderId, receiverId);
            Log.d(TAG, "HKDF key derivation successful");

            // 4. 序列化数据包为 JSON
            String json = serializeShareDataPacket(data);
            byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
            Log.d(TAG, "Serialized data packet: " + jsonBytes.length + " bytes");

            // 5. 对原始 JSON 签名（不是对密文签名）
            byte[] signature = ed25519Signer.sign(jsonBytes, senderEd25519PrivateKey);
            if (signature.length != CryptoConstants.ED25519_SIGNATURE_SIZE) {
                throw new SecurityException("Invalid signature size: " + signature.length);
            }
            Log.d(TAG, "Ed25519 signature generated: " + signature.length + " bytes");

            // 6. AES-256-GCM 加密
            byte[] iv = CryptoUtils.generateGcmIV();  // 使用优化的 CryptoUtils
            String encryptedData = encryptWithAES(jsonBytes, aesKey, iv);
            if (encryptedData == null) {
                Log.e(TAG, "Failed to encrypt data with AES");
                return null;
            }

            // 7. 组装数据包
            long now = System.currentTimeMillis();
            long actualExpireAt = (expireAt > 0) ? expireAt : (data.expireAt > 0 ? data.expireAt : 0);

            EncryptedSharePacketV3 packet = new EncryptedSharePacketV3(
                    CryptoConstants.PROTOCOL_VERSION_V3,
                    CryptoUtils.base64Encode(ephemeralKeyPair.getPublic().getEncoded()),  // 使用优化的 CryptoUtils
                    encryptedData,
                    CryptoUtils.base64Encode(iv),  // 使用优化的 CryptoUtils
                    CryptoUtils.base64Encode(signature),  // 使用优化的 CryptoUtils
                    now,
                    actualExpireAt,
                    senderId
            );

            long duration = System.currentTimeMillis() - startTime;

            // 记录分享创建事件
            if (analytics != null) {
                analytics.recordShareCreated(CryptoConstants.PROTOCOL_VERSION_V3);
                analytics.recordCryptoOperation(AnalyticsManager.CRYPTO_OP_V3_ENCRYPT, duration);
            }

            Log.d(TAG, "Successfully created encrypted packet v3.0 in " + duration + "ms");
            return packet;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Failed to create encrypted packet v3.0 after " + duration + "ms", e);

            // 记录失败操作
            if (analytics != null) {
                analytics.recordCryptoOperation(AnalyticsManager.CRYPTO_OP_V3_ENCRYPT, duration);
            }

            return null;
        }
    }

    /**
     * 解开加密分享包 - 版本 3.0（X25519/Ed25519）
     *
     * v3.0 解密流程：
     * 1. 验证时间戳（防重放攻击）
     * 2. 验证 ephemeral public key 有效性
     * 3. 用接收方 X25519 私钥 + 发送方 ephemeral 公钥 → ECDH 共享密钥
     * 4. HKDF 派生 AES 密钥（与加密时相同的 info）
     * 5. AES-256-GCM 解密
     * 6. Ed25519 验证签名（对原始 JSON 验证）
     * 7. 反序列化 JSON
     *
     * @param packet 加密分享包 v3.0
     * @param receiverX25519PrivateKey 接收方长期 X25519 私钥
     * @param senderEd25519PublicKey 发送方 Ed25519 公钥（验证签名）
     * @param senderId 发送方用户 ID
     * @param receiverId 接收方用户 ID
     * @param context Android 上下文
     * @return 分享数据包，如果解密或验证失败则返回 null
     */
    @Nullable
    public ShareDataPacket openEncryptedPacketV3(
            @NonNull EncryptedSharePacketV3 packet,
            @NonNull java.security.PrivateKey receiverX25519PrivateKey,
            @NonNull java.security.PublicKey senderEd25519PublicKey,
            @NonNull String senderId,
            @NonNull String receiverId,
            @NonNull Context context
    ) {
        long startTime = System.currentTimeMillis();
        try {
            // 确保上下文已设置
            if (this.context == null) {
                setContext(context);
            }

            // 获取加密管理器实例
            X25519KeyManager x25519KeyManager = X25519KeyManagerFactory.create(context);
            Ed25519Signer ed25519Signer = Ed25519SignerFactory.create(context);
            HKDFManager hkdfManager = HKDFManager.getInstance();

            // 1. 验证版本
            if (!CryptoConstants.PROTOCOL_VERSION_V3.equals(packet.getVersion())) {
                Log.e(TAG, "Unsupported packet version: " + packet.getVersion() + " (expected " + CryptoConstants.PROTOCOL_VERSION_V3 + ")");
                return null;
            }

            Log.d(TAG, "Opening encrypted packet v3.0");

            // 2. 验证时间戳（防重放攻击）
            if (!packet.isTimestampValid(CryptoConstants.MAX_TIMESTAMP_DRIFT)) {
                Log.e(TAG, "Timestamp validation failed");
                return null;
            }

            // 3. 验证 ephemeral public key 有效性
            byte[] ephemeralPublicKeyBytes = CryptoUtils.base64Decode(packet.getEphemeralPublicKey());  // 使用优化的 CryptoUtils
            x25519KeyManager.validatePublicKey(ephemeralPublicKeyBytes);

            // 4. ECDH 密钥交换（用接收方私钥 + 发送方 ephemeral 公钥）
            java.security.PublicKey ephemeralPublicKey = x25519KeyManager.decodePublicKey(ephemeralPublicKeyBytes);
            byte[] sharedSecret = x25519KeyManager.performECDH(
                    receiverX25519PrivateKey,
                    ephemeralPublicKey
            );
            Log.d(TAG, "ECDH key exchange successful, shared secret: " + sharedSecret.length + " bytes");

            // 5. HKDF 派生 AES 密钥（与加密时相同的 info）
            javax.crypto.SecretKey aesKey = hkdfManager.deriveAESKey(sharedSecret, senderId, receiverId);
            Log.d(TAG, "HKDF key derivation successful");

            // 6. AES-256-GCM 解密
            byte[] iv = CryptoUtils.base64Decode(packet.getIv());  // 使用优化的 CryptoUtils
            byte[] decryptedBytes = decryptWithAES(packet.getEncryptedData(), aesKey, iv);
            if (decryptedBytes == null) {
                Log.e(TAG, "Failed to decrypt data with AES");
                return null;
            }

            // 7. Ed25519 验证签名（对原始 JSON 验证）
            byte[] signature = CryptoUtils.base64Decode(packet.getSignature());  // 使用优化的 CryptoUtils
            boolean isValid = ed25519Signer.verify(decryptedBytes, signature, senderEd25519PublicKey);
            if (!isValid) {
                Log.e(TAG, "Ed25519 signature verification failed");
                return null;
            }

            // 8. 反序列化 JSON
            String json = new String(decryptedBytes, StandardCharsets.UTF_8);
            ShareDataPacket data = deserializeShareDataPacket(json);

            long duration = System.currentTimeMillis() - startTime;

            // 记录分享接收事件
            if (analytics != null) {
                analytics.recordShareReceived(CryptoConstants.PROTOCOL_VERSION_V3);
                analytics.recordCryptoOperation(AnalyticsManager.CRYPTO_OP_V3_DECRYPT, duration);
            }

            Log.d(TAG, "Successfully opened encrypted packet v3.0 in " + duration + "ms");
            return data;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            Log.e(TAG, "Failed to open encrypted packet v3.0 after " + duration + "ms", e);

            // 记录失败操作
            if (analytics != null) {
                analytics.recordCryptoOperation(AnalyticsManager.CRYPTO_OP_V3_DECRYPT, duration);
            }

            return null;
        }
    }

    /**
     * 自动选择协议版本并创建加密包
     *
     * 根据双方密钥版本自动选择最佳协议版本（v3.0 优先）
     *
     * @param data 分享数据包
     * @param senderKeyInfo 发送方密钥信息
     * @param receiverKeyInfo 接收方密钥信息
     * @param senderEd25519PrivateKey 发送方 Ed25519 私钥（v3.0 使用）
     * @param senderRsaPrivateKey 发送方 RSA 私钥（v2.0 使用）
     * @param context Android 上下文
     * @return 加密分享包（v2.0 或 v3.0），如果失败则返回 null
     */
    @Nullable
    public Object createEncryptedPacketAuto(
            @NonNull ShareDataPacket data,
            @NonNull UserKeyInfo senderKeyInfo,
            @NonNull UserKeyInfo receiverKeyInfo,
            @Nullable java.security.PrivateKey senderEd25519PrivateKey,
            @Nullable java.security.PrivateKey senderRsaPrivateKey,
            @NonNull Context context
    ) {
        String version = detectProtocolVersion(senderKeyInfo, receiverKeyInfo);

        if (CryptoConstants.PROTOCOL_VERSION_V3.equals(version)) {
            // 使用 v3.0
            if (senderEd25519PrivateKey == null || receiverKeyInfo.getX25519PublicKey() == null) {
                Log.e(TAG, "Missing required keys for v3.0, falling back to v2.0");
                version = CryptoConstants.PROTOCOL_VERSION_V2;
            } else {
                try {
                    // 解密接收方 X25519 公钥
                    X25519KeyManager x25519Manager = X25519KeyManagerFactory.create(context);
                    byte[] pubKeyBytes = Base64.decode(receiverKeyInfo.getX25519PublicKey(), Base64.NO_WRAP);
                    java.security.PublicKey receiverX25519PubKey = x25519Manager.decodePublicKey(pubKeyBytes);

                    return createEncryptedPacketV3(
                            data,
                            receiverX25519PubKey,
                            senderEd25519PrivateKey,
                            senderKeyInfo.getUserId(),
                            receiverKeyInfo.getUserId(),
                            data.expireAt,
                            context
                    );
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create v3.0 packet, falling back to v2.0", e);
                    version = CryptoConstants.PROTOCOL_VERSION_V2;
                }
            }
        }

        // 使用 v2.0
        if (CryptoConstants.PROTOCOL_VERSION_V2.equals(version)) {
            if (senderRsaPrivateKey == null || receiverKeyInfo.getRsaPublicKey() == null) {
                Log.e(TAG, "Missing required keys for v2.0");
                return null;
            }

            try {
                // 解密接收方 RSA 公钥
                byte[] rsaPubKeyBytes = Base64.decode(receiverKeyInfo.getRsaPublicKey(), Base64.NO_WRAP);
                java.security.KeyFactory keyFactory = java.security.KeyFactory.getInstance("RSA");
                java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(rsaPubKeyBytes);
                java.security.PublicKey receiverRsaPubKey = keyFactory.generatePublic(spec);

                return createEncryptedPacket(data, receiverRsaPubKey, senderRsaPrivateKey);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create v2.0 packet", e);
                return null;
            }
        }

        return null;
    }
}
