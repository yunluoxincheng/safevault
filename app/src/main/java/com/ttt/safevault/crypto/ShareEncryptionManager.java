package com.ttt.safevault.crypto;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAKey;
import javax.crypto.Cipher;

/**
 * 分享加密管理器
 * 处理端到端加密分享的加密、解密和签名验证
 */
public class ShareEncryptionManager {
    private static final String TAG = "ShareEncryptionManager";

    // RSA-OAEP with SHA-256
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    // 签名算法
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

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
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
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
            Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
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
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
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
            Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
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
     * 创建加密分享包（加密+签名）
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
        // 1. 加密数据
        String encryptedData = encryptShare(data, receiverPublicKey);
        if (encryptedData == null) {
            return null;
        }

        // 2. 签名原始数据
        String signature = signShare(data, senderPrivateKey);
        if (signature == null) {
            return null;
        }

        // 3. 创建加密包
        EncryptedSharePacket packet = new EncryptedSharePacket();
        packet.setVersion(data.version);
        packet.setEncryptedData(encryptedData);
        packet.setSignature(signature);
        packet.setSenderId(data.senderId);
        packet.setCreatedAt(data.createdAt);
        packet.setExpireAt(data.expireAt);

        return packet;
    }

    /**
     * 解开加密分享包（解密+验证签名）
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
        // 1. 解密数据
        ShareDataPacket data = decryptShare(packet.getEncryptedData(), receiverPrivateKey);
        if (data == null) {
            return null;
        }

        // 2. 验证签名
        if (!verifySignature(data, packet.getSignature(), senderPublicKey)) {
            Log.e(TAG, "Signature verification failed");
            return null;
        }

        return data;
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
}
