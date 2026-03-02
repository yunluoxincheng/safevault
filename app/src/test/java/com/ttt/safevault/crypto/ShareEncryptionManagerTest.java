package com.ttt.safevault.crypto;

import com.ttt.safevault.model.EncryptedSharePacketV3;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.UserKeyInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * ShareEncryptionManager v3.0 单元测试
 *
 * 验证 X25519/Ed25519 分享加密功能：
 * - v3.0 加密流程
 * - v3.0 解密流程
 * - 版本协商
 * - 时间戳验证
 */
@RunWith(JUnit4.class)
public class ShareEncryptionManagerTest {

    private final ShareEncryptionManager manager = new ShareEncryptionManager();

    /**
     * 测试协议版本协商
     *
     * 验证版本协商矩阵：
     * - 双方都支持 v3.0 → 使用 v3.0
     * - 任一方不支持 v3.0 → 回退到 v2.0
     */
    @Test
    public void testProtocolVersionNegotiation() {
        // 双方都支持 v3.0
        UserKeyInfo senderV3 = createV3UserKeyInfo("sender1");
        UserKeyInfo receiverV3 = createV3UserKeyInfo("receiver1");
        String version1 = manager.detectProtocolVersion(senderV3, receiverV3);
        assertEquals("双方 v3.0 应选择 v3.0", CryptoConstants.PROTOCOL_VERSION_V3, version1);

        // 发送方 v3.0，接收方 v2.0
        UserKeyInfo receiverV2 = createV2UserKeyInfo("receiver2");
        String version2 = manager.detectProtocolVersion(senderV3, receiverV2);
        assertEquals("接收方 v2.0 应回退到 v2.0", CryptoConstants.PROTOCOL_VERSION_V2, version2);

        // 发送方 v2.0，接收方 v3.0
        UserKeyInfo senderV2 = createV2UserKeyInfo("sender2");
        String version3 = manager.detectProtocolVersion(senderV2, receiverV3);
        assertEquals("发送方 v2.0 应回退到 v2.0", CryptoConstants.PROTOCOL_VERSION_V2, version3);

        // 双方 v2.0
        UserKeyInfo receiverV2b = createV2UserKeyInfo("receiver3");
        String version4 = manager.detectProtocolVersion(senderV2, receiverV2b);
        assertEquals("双方 v2.0 应选择 v2.0", CryptoConstants.PROTOCOL_VERSION_V2, version4);
    }

    /**
     * 测试 UserKeyInfo.supportsV3()
     *
     * 验证 v3.0 支持检测逻辑
     */
    @Test
    public void testV3SupportDetection() {
        // 完整 v3.0 支持
        UserKeyInfo v3 = createV3UserKeyInfo("user1");
        assertTrue("完整 v3.0 密钥应返回 true", v3.supportsV3());

        // 缺少 X25519 公钥
        UserKeyInfo noX25519 = new UserKeyInfo();
        noX25519.setUserId("user2");
        noX25519.setEd25519PublicKey("ed25519_key");
        assertFalse("缺少 X25519 公钥应返回 false", noX25519.supportsV3());

        // 缺少 Ed25519 公钥
        UserKeyInfo noEd25519 = new UserKeyInfo();
        noEd25519.setUserId("user3");
        noEd25519.setX25519PublicKey("x25519_key");
        assertFalse("缺少 Ed25519 公钥应返回 false", noEd25519.supportsV3());

        // 空 X25519 公钥
        UserKeyInfo emptyX25519 = new UserKeyInfo();
        emptyX25519.setUserId("user4");
        emptyX25519.setX25519PublicKey("");
        emptyX25519.setEd25519PublicKey("ed25519_key");
        assertFalse("空 X25519 公钥应返回 false", emptyX25519.supportsV3());

        // 空 Ed25519 公钥
        UserKeyInfo emptyEd25519 = new UserKeyInfo();
        emptyEd25519.setUserId("user5");
        emptyEd25519.setX25519PublicKey("x25519_key");
        emptyEd25519.setEd25519PublicKey("");
        assertFalse("空 Ed25519 公钥应返回 false", emptyEd25519.supportsV3());
    }

    /**
     * 测试 UserKeyInfo.supportsV2()
     *
     * 验证 v2.0 支持检测逻辑
     */
    @Test
    public void testV2SupportDetection() {
        // 有 RSA 公钥
        UserKeyInfo v2 = createV2UserKeyInfo("user1");
        assertTrue("有 RSA 公钥应返回 true", v2.supportsV2());

        // 无 RSA 公钥
        UserKeyInfo noRsa = createV3UserKeyInfo("user2");
        assertFalse("无 RSA 公钥应返回 false", noRsa.supportsV2());

        // 空 RSA 公钥
        UserKeyInfo emptyRsa = new UserKeyInfo();
        emptyRsa.setUserId("user3");
        emptyRsa.setRsaPublicKey("");
        assertFalse("空 RSA 公钥应返回 false", emptyRsa.supportsV2());
    }

    /**
     * 测试 EncryptedSharePacketV3 创建
     *
     * 验证：
     * - 数据包创建成功
     * - 版本为 "3.0"
     * - 必需字段设置正确
     */
    @Test
    public void testEncryptedPacketV3Creation() {
        long now = System.currentTimeMillis();
        long expireAt = now + 3600000; // 1 小时后过期

        EncryptedSharePacketV3 packet = new EncryptedSharePacketV3(
                "3.0",
                "ephemeral_pub_key_base64",
                "encrypted_data_base64",
                "iv_base64",
                "signature_base64",
                now,
                expireAt,
                "sender123"
        );

        assertNotNull("数据包不应为 null", packet);
        assertEquals("版本应为 3.0", "3.0", packet.getVersion());
        assertEquals("发送方 ID 应正确", "sender123", packet.getSenderId());
        assertEquals("创建时间应正确", now, packet.getCreatedAt());
        assertEquals("过期时间应正确", expireAt, packet.getExpireAt());
    }

    /**
     * 测试 EncryptedSharePacketV3 过期检查
     *
     * 验证：
     * - 已过期的包返回 true
     * - 未过期的包返回 false
     * - 永不过期（expireAt=0）返回 false
     */
    @Test
    public void testPacketExpiration() {
        long now = System.currentTimeMillis();

        // 已过期的包
        EncryptedSharePacketV3 expiredPacket = new EncryptedSharePacketV3();
        expiredPacket.setExpireAt(now - 1000); // 1 秒前过期
        assertTrue("已过期的包应返回 true", expiredPacket.isExpired());

        // 未过期的包
        EncryptedSharePacketV3 validPacket = new EncryptedSharePacketV3();
        validPacket.setExpireAt(now + 3600000); // 1 小时后过期
        assertFalse("未过期的包应返回 false", validPacket.isExpired());

        // 永不过期的包
        EncryptedSharePacketV3 neverExpirePacket = new EncryptedSharePacketV3();
        neverExpirePacket.setExpireAt(0);
        assertFalse("永不过期的包应返回 false", neverExpirePacket.isExpired());
    }

    /**
     * 测试 EncryptedSharePacketV3 剩余时间
     *
     * 验证：
     * - 剩余时间计算正确
     * - 已过期包返回 0
     * - 永不过期包返回 Long.MAX_VALUE
     */
    @Test
    public void testPacketRemainingTime() {
        long now = System.currentTimeMillis();

        // 1 小时后过期
        EncryptedSharePacketV3 packet1 = new EncryptedSharePacketV3();
        packet1.setExpireAt(now + 3600000);
        long remaining1 = packet1.getRemainingTime();
        assertTrue("剩余时间应约等于 1 小时", remaining1 > 3500000 && remaining1 <= 3600000);

        // 已过期
        EncryptedSharePacketV3 packet2 = new EncryptedSharePacketV3();
        packet2.setExpireAt(now - 1000);
        assertEquals("已过期包应返回 0", 0, packet2.getRemainingTime());

        // 永不过期
        EncryptedSharePacketV3 packet3 = new EncryptedSharePacketV3();
        packet3.setExpireAt(0);
        assertEquals("永不过期包应返回 Long.MAX_VALUE", Long.MAX_VALUE, packet3.getRemainingTime());
    }

    /**
     * 测试 EncryptedSharePacketV3 时间戳验证
     *
     * 验证：
     * - 有效时间戳通过验证
     * - 过期时间戳失败
     * - 未来太远的时间戳失败
     * - 过去太远的时间戳失败
     */
    @Test
    public void testTimestampValidation() {
        long now = System.currentTimeMillis();
        long maxDrift = CryptoConstants.MAX_TIMESTAMP_DRIFT; // 10 分钟

        // 有效时间戳
        EncryptedSharePacketV3 validPacket = new EncryptedSharePacketV3();
        validPacket.setCreatedAt(now);
        validPacket.setExpireAt(now + 3600000);
        assertTrue("有效时间戳应通过验证", validPacket.isTimestampValid(maxDrift));

        // 刚创建的时间戳（在允许偏差内）
        EncryptedSharePacketV3 recentPacket = new EncryptedSharePacketV3();
        recentPacket.setCreatedAt(now - 300000); // 5 分钟前
        recentPacket.setExpireAt(now + 3600000);
        assertTrue("最近创建的时间戳应通过验证", recentPacket.isTimestampValid(maxDrift));

        // 过期时间戳
        EncryptedSharePacketV3 expiredPacket = new EncryptedSharePacketV3();
        expiredPacket.setCreatedAt(now - 7200000); // 2 小时前
        expiredPacket.setExpireAt(now - 1000); // 已过期
        assertFalse("过期时间戳应失败", expiredPacket.isTimestampValid(maxDrift));

        // 未来太远的时间戳
        EncryptedSharePacketV3 futurePacket = new EncryptedSharePacketV3();
        futurePacket.setCreatedAt(now + 7200000); // 2 小时后
        futurePacket.setExpireAt(now + 10800000);
        assertFalse("未来太远的时间戳应失败", futurePacket.isTimestampValid(maxDrift));

        // 过去太远的时间戳
        EncryptedSharePacketV3 oldPacket = new EncryptedSharePacketV3();
        oldPacket.setCreatedAt(now - 7200000); // 2 小时前
        oldPacket.setExpireAt(now + 3600000);
        assertFalse("过去太远的时间戳应失败", oldPacket.isTimestampValid(maxDrift));
    }

    /**
     * 测试 EncryptedSharePacketV3 完整性验证
     *
     * 验证：
     * - 完整的数据包通过验证
     * - 缺少版本失败
     * - 缺少 ephemeralPublicKey 失败
     * - 缺少 encryptedData 失败
     * - 缺少 iv 失败
     * - 缺少 signature 失败
     * - 缺少 senderId 失败
     * - 无效时间戳失败
     * - 已过期失败
     */
    @Test
    public void testPacketValidity() {
        long now = System.currentTimeMillis();

        // 完整的数据包
        EncryptedSharePacketV3 validPacket = new EncryptedSharePacketV3(
                "3.0",
                "ephemeral_pub_key",
                "encrypted_data",
                "iv",
                "signature",
                now,
                now + 3600000,
                "sender"
        );
        assertTrue("完整的数据包应通过验证", validPacket.isValid());

        // 缺少版本
        EncryptedSharePacketV3 noVersion = new EncryptedSharePacketV3();
        noVersion.setVersion(null);
        noVersion.setEphemeralPublicKey("key");
        noVersion.setEncryptedData("data");
        noVersion.setIv("iv");
        noVersion.setSignature("sig");
        noVersion.setSenderId("sender");
        noVersion.setCreatedAt(now);
        noVersion.setExpireAt(now + 3600000);
        assertFalse("缺少版本应失败", noVersion.isValid());

        // 缺少 ephemeralPublicKey
        EncryptedSharePacketV3 noEphemeral = new EncryptedSharePacketV3();
        noEphemeral.setVersion("3.0");
        noEphemeral.setEncryptedData("data");
        noEphemeral.setIv("iv");
        noEphemeral.setSignature("sig");
        noEphemeral.setSenderId("sender");
        noEphemeral.setCreatedAt(now);
        noEphemeral.setExpireAt(now + 3600000);
        assertFalse("缺少 ephemeralPublicKey 应失败", noEphemeral.isValid());

        // 缺少 encryptedData
        EncryptedSharePacketV3 noData = new EncryptedSharePacketV3();
        noData.setVersion("3.0");
        noData.setEphemeralPublicKey("key");
        noData.setIv("iv");
        noData.setSignature("sig");
        noData.setSenderId("sender");
        noData.setCreatedAt(now);
        noData.setExpireAt(now + 3600000);
        assertFalse("缺少 encryptedData 应失败", noData.isValid());

        // 缺少 iv
        EncryptedSharePacketV3 noIv = new EncryptedSharePacketV3();
        noIv.setVersion("3.0");
        noIv.setEphemeralPublicKey("key");
        noIv.setEncryptedData("data");
        noIv.setSignature("sig");
        noIv.setSenderId("sender");
        noIv.setCreatedAt(now);
        noIv.setExpireAt(now + 3600000);
        assertFalse("缺少 iv 应失败", noIv.isValid());

        // 缺少 signature
        EncryptedSharePacketV3 noSignature = new EncryptedSharePacketV3();
        noSignature.setVersion("3.0");
        noSignature.setEphemeralPublicKey("key");
        noSignature.setEncryptedData("data");
        noSignature.setIv("iv");
        noSignature.setSenderId("sender");
        noSignature.setCreatedAt(now);
        noSignature.setExpireAt(now + 3600000);
        assertFalse("缺少 signature 应失败", noSignature.isValid());

        // 缺少 senderId
        EncryptedSharePacketV3 noSender = new EncryptedSharePacketV3();
        noSender.setVersion("3.0");
        noSender.setEphemeralPublicKey("key");
        noSender.setEncryptedData("data");
        noSender.setIv("iv");
        noSender.setSignature("sig");
        noSender.setCreatedAt(now);
        noSender.setExpireAt(now + 3600000);
        assertFalse("缺少 senderId 应失败", noSender.isValid());

        // 无效时间戳
        EncryptedSharePacketV3 invalidTime = new EncryptedSharePacketV3();
        invalidTime.setVersion("3.0");
        invalidTime.setEphemeralPublicKey("key");
        invalidTime.setEncryptedData("data");
        invalidTime.setIv("iv");
        invalidTime.setSignature("sig");
        invalidTime.setSenderId("sender");
        invalidTime.setCreatedAt(0);
        invalidTime.setExpireAt(now + 3600000);
        assertFalse("无效时间戳应失败", invalidTime.isValid());

        // 已过期
        EncryptedSharePacketV3 expired = new EncryptedSharePacketV3();
        expired.setVersion("3.0");
        expired.setEphemeralPublicKey("key");
        expired.setEncryptedData("data");
        expired.setIv("iv");
        expired.setSignature("sig");
        expired.setSenderId("sender");
        expired.setCreatedAt(now - 7200000);
        expired.setExpireAt(now - 1000);
        assertFalse("已过期应失败", expired.isValid());
    }

    /**
     * 测试版本协商的一致性
     *
     * 验证：
     * - 版本协商是对称的
     * - 顺序不影响结果
     */
    @Test
    public void testProtocolNegotiationSymmetry() {
        UserKeyInfo senderV3 = createV3UserKeyInfo("sender");
        UserKeyInfo receiverV3 = createV3UserKeyInfo("receiver");

        String version1 = manager.detectProtocolVersion(senderV3, receiverV3);
        String version2 = manager.detectProtocolVersion(receiverV3, senderV3);

        assertEquals("版本协商应对称", version1, version2);
    }

    /**
     * 测试 UserKeyInfo 最佳版本获取
     *
     * 验证：
     * - 有 v3.0 返回 "3.0"
     * - 只有 v2.0 返回 "2.0"
     * - 无可用密钥抛出异常
     */
    @Test
    public void testGetBestProtocolVersion() {
        // 有 v3.0
        UserKeyInfo v3 = createV3UserKeyInfo("user1");
        assertEquals("有 v3.0 应返回 3.0", "3.0", v3.getBestProtocolVersion());

        // 只有 v2.0
        UserKeyInfo v2 = createV2UserKeyInfo("user2");
        assertEquals("只有 v2.0 应返回 2.0", "2.0", v2.getBestProtocolVersion());

        // 无可用密钥
        UserKeyInfo noKeys = new UserKeyInfo();
        noKeys.setUserId("user3");
        try {
            noKeys.getBestProtocolVersion();
            fail("无可用密钥应抛出异常");
        } catch (SecurityException e) {
            assertTrue("异常消息应包含 'No supported'",
                e.getMessage().contains("No supported"));
        }
    }

    /**
     * 测试 CryptoConstants 常量
     *
     * 验证常量定义正确
     */
    @Test
    public void testCryptoConstants() {
        assertEquals("协议版本 v2.0 应为 '2.0'", "2.0", CryptoConstants.PROTOCOL_VERSION_V2);
        assertEquals("协议版本 v3.0 应为 '3.0'", "3.0", CryptoConstants.PROTOCOL_VERSION_V3);

        assertEquals("最大时间偏差应为 10 分钟", 600000, CryptoConstants.MAX_TIMESTAMP_DRIFT);

        assertEquals("AES 算法应为 AES/GCM/NoPadding", "AES/GCM/NoPadding", CryptoConstants.AES_ALGORITHM);
        assertEquals("AES 密钥大小应为 256", 256, CryptoConstants.AES_KEY_SIZE);
        assertEquals("GCM IV 大小应为 12", 12, CryptoConstants.GCM_IV_SIZE);

        assertEquals("HKDF 哈希算法应为 HmacSHA256", "HmacSHA256", CryptoConstants.HKDF_HASH_ALGORITHM);
        assertEquals("HKDF 输出大小应为 32", 32, CryptoConstants.HKDF_OUTPUT_SIZE);

        assertEquals("X25519 密钥大小应为 32", 32, CryptoConstants.X25519_KEY_SIZE);

        assertEquals("Ed25519 签名大小应为 64", 64, CryptoConstants.ED25519_SIGNATURE_SIZE);
        assertEquals("Ed25519 公钥大小应为 32", 32, CryptoConstants.ED25519_PUBLIC_KEY_SIZE);
        assertEquals("Ed25519 私钥大小应为 32", 32, CryptoConstants.ED25519_PRIVATE_KEY_SIZE);
    }

    /**
     * 测试 ShareDataPacket 创建
     *
     * 验证分享数据包的基本功能
     */
    @Test
    public void testShareDataPacketCreation() {
        ShareDataPacket packet = new ShareDataPacket();
        packet.version = "3.0";
        packet.senderId = "sender123";
        packet.senderPublicKey = "sender_pub_key";
        packet.createdAt = System.currentTimeMillis();
        packet.expireAt = System.currentTimeMillis() + 3600000;
        packet.permission = new SharePermission(true, true, true);

        com.ttt.safevault.model.PasswordItem password =
            new com.ttt.safevault.model.PasswordItem();
        password.setTitle("Test Password");
        password.setUsername("testuser");
        password.setPassword("testpass123");
        password.setUrl("https://example.com");
        password.setNotes("Test notes");
        packet.password = password;

        assertNotNull("分享数据包不应为 null", packet);
        assertEquals("版本应为 3.0", "3.0", packet.version);
        assertEquals("发送方 ID 应正确", "sender123", packet.senderId);
        assertEquals("密码标题应正确", "Test Password", packet.password.getTitle());
    }

    // ========== 辅助方法 ==========

    /**
     * 创建 v3.0 用户密钥信息
     */
    private UserKeyInfo createV3UserKeyInfo(String userId) {
        UserKeyInfo keyInfo = new UserKeyInfo();
        keyInfo.setUserId(userId);
        keyInfo.setX25519PublicKey("x25519_" + userId);
        keyInfo.setEd25519PublicKey("ed25519_" + userId);
        keyInfo.setKeyVersion("v3");
        return keyInfo;
    }

    /**
     * 创建 v2.0 用户密钥信息
     */
    private UserKeyInfo createV2UserKeyInfo(String userId) {
        UserKeyInfo keyInfo = new UserKeyInfo();
        keyInfo.setUserId(userId);
        keyInfo.setRsaPublicKey("rsa_" + userId);
        keyInfo.setKeyVersion("v2");
        return keyInfo;
    }
}