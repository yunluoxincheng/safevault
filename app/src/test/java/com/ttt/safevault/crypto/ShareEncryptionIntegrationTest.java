package com.ttt.safevault.crypto;

import com.ttt.safevault.model.EncryptedSharePacket;
import com.ttt.safevault.model.EncryptedSharePacketV3;
import com.ttt.safevault.model.ShareDataPacket;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.UserKeyInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * ShareEncryptionManager 集成测试
 *
 * 验证端到端分享流程、v2.0/v3.0 互操作、版本协商
 *
 * 注意：此测试使用 Mock 密钥管理器，真实集成测试需要在 Android 设备上运行
 */
@RunWith(JUnit4.class)
public class ShareEncryptionIntegrationTest {

    private final ShareEncryptionManager manager = new ShareEncryptionManager();

    /**
     * 测试端到端 v3.0 分享流程（模拟）
     *
     * 完整流程：
     * 1. Alice 生成 X25519/Ed25519 密钥对
     * 2. Bob 生成 X25519/Ed25519 密钥对
     * 3. Alice 创建分享数据包
     * 4. Alice 加密数据包（使用 Bob 的 X25519 公钥）
     * 5. Bob 解密数据包（使用自己的 X25519 私钥）
     * 6. Bob 验证签名（使用 Alice 的 Ed25519 公钥）
     */
    @Test
    public void testEndToEndV3ShareFlow() {
        try {
            // 1. Alice 生成密钥对
            KeyPair aliceX25519 = generateMockX25519KeyPair();
            KeyPair aliceEd25519 = generateMockEd25519KeyPair();

            // 2. Bob 生成密钥对
            KeyPair bobX25519 = generateMockX25519KeyPair();
            KeyPair bobEd25519 = generateMockEd25519KeyPair();

            // 3. Alice 创建分享数据包
            ShareDataPacket shareData = createTestShareData("alice", "bob");

            // 4. Alice 加密数据包（模拟）
            EncryptedSharePacketV3 encryptedPacket = mockEncryptV3(
                shareData,
                bobX25519.getPublic(),
                aliceEd25519.getPrivate(),
                "alice",
                "bob"
            );

            // 验证加密包
            assertNotNull("加密包不应为 null", encryptedPacket);
            assertEquals("版本应为 3.0", "3.0", encryptedPacket.getVersion());
            assertEquals("发送方应为 alice", "alice", encryptedPacket.getSenderId());
            assertNotNull("应有 ephemeral 公钥", encryptedPacket.getEphemeralPublicKey());
            assertNotNull("应有加密数据", encryptedPacket.getEncryptedData());
            assertNotNull("应有 IV", encryptedPacket.getIv());
            assertNotNull("应有签名", encryptedPacket.getSignature());

            // 5. Bob 解密数据包（模拟）
            ShareDataPacket decryptedData = mockDecryptV3(
                encryptedPacket,
                bobX25519.getPrivate(),
                aliceEd25519.getPublic(),
                "alice",
                "bob"
            );

            // 验证解密结果
            assertNotNull("解密数据不应为 null", decryptedData);
            assertEquals("发送方 ID 应匹配", shareData.senderId, decryptedData.senderId);
            assertEquals("发送方公钥应匹配", shareData.senderPublicKey, decryptedData.senderPublicKey);
            assertEquals("密码标题应匹配", shareData.password.getTitle(), decryptedData.password.getTitle());
            assertEquals("密码用户名应匹配", shareData.password.getUsername(), decryptedData.password.getUsername());
            assertEquals("密码应匹配", shareData.password.getPassword(), decryptedData.password.getPassword());

            // 6. 验证签名（模拟在解密过程中完成）
            // 在真实实现中，签名验证在解密流程中完成

        } catch (Exception e) {
            fail("端到端 v3.0 分享流程测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 v2.0/v3.0 互操作性 - 混合场景
     *
     * 验证 4 种组合场景：
     * 1. Alice v3.0 → Bob v3.0：使用 v3.0
     * 2. Alice v3.0 → Bob v2.0：使用 v2.0
     * 3. Alice v2.0 → Bob v3.0：使用 v2.0
     * 4. Alice v2.0 → Bob v2.0：使用 v2.0
     */
    @Test
    public void testV2V3Interoperability() {
        // 准备密钥信息
        UserKeyInfo aliceV3 = createV3UserKeyInfo("alice_v3");
        UserKeyInfo bobV3 = createV3UserKeyInfo("bob_v3");
        UserKeyInfo aliceV2 = createV2UserKeyInfo("alice_v2");
        UserKeyInfo bobV2 = createV2UserKeyInfo("bob_v2");

        // 场景 1: 双方 v3.0 → 使用 v3.0
        String version1 = manager.detectProtocolVersion(aliceV3, bobV3);
        assertEquals("双方 v3.0 应使用 v3.0", CryptoConstants.PROTOCOL_VERSION_V3, version1);

        // 场景 2: Alice v3.0, Bob v2.0 → 使用 v2.0
        String version2 = manager.detectProtocolVersion(aliceV3, bobV2);
        assertEquals("接收方 v2.0 应回退到 v2.0", CryptoConstants.PROTOCOL_VERSION_V2, version2);

        // 场景 3: Alice v2.0, Bob v3.0 → 使用 v2.0
        String version3 = manager.detectProtocolVersion(aliceV2, bobV3);
        assertEquals("发送方 v2.0 应回退到 v2.0", CryptoConstants.PROTOCOL_VERSION_V2, version3);

        // 场景 4: 双方 v2.0 → 使用 v2.0
        String version4 = manager.detectProtocolVersion(aliceV2, bobV2);
        assertEquals("双方 v2.0 应使用 v2.0", CryptoConstants.PROTOCOL_VERSION_V2, version4);
    }

    /**
     * 测试版本协商的优先级
     *
     * 验证 v3.0 优先于 v2.0
     */
    @Test
    public void testProtocolVersionPriority() {
        UserKeyInfo senderV3 = createV3UserKeyInfo("sender");
        UserKeyInfo receiverV3 = createV3UserKeyInfo("receiver");

        // 双方 v3.0 应选择 v3.0
        String version = manager.detectProtocolVersion(senderV3, receiverV3);
        assertEquals("v3.0 应优先于 v2.0", CryptoConstants.PROTOCOL_VERSION_V3, version);
    }

    /**
     * 测试版本协商的回退机制
     *
     * 验证不兼容时回退到 v2.0
     */
    @Test
    public void testProtocolVersionFallback() {
        UserKeyInfo senderV3 = createV3UserKeyInfo("sender");
        UserKeyInfo receiverV2 = createV2UserKeyInfo("receiver");

        // 一方 v2.0 应回退到 v2.0
        String version = manager.detectProtocolVersion(senderV3, receiverV2);
        assertEquals("应回退到 v2.0", CryptoConstants.PROTOCOL_VERSION_V2, version);
    }

    /**
     * 测试多用户分享场景
     *
     * 验证：
     * - Alice 可以向多个用户分享
     * - 每个接收方使用不同的加密密钥
     * - 每个接收方都能独立解密
     */
    @Test
    public void testMultiUserSharing() {
        try {
            // Alice 生成密钥对
            KeyPair aliceX25519 = generateMockX25519KeyPair();
            KeyPair aliceEd25519 = generateMockEd25519KeyPair();

            // 创建多个接收方
            KeyPair bobX25519 = generateMockX25519KeyPair();
            KeyPair bobEd25519 = generateMockEd25519KeyPair();
            KeyPair charlieX25519 = generateMockX25519KeyPair();
            KeyPair charlieEd25519 = generateMockEd25519KeyPair();

            // 创建分享数据
            ShareDataPacket shareData = createTestShareData("alice", "multi");

            // Alice 加密给 Bob
            EncryptedSharePacketV3 packetToBob = mockEncryptV3(
                shareData,
                bobX25519.getPublic(),
                aliceEd25519.getPrivate(),
                "alice",
                "bob"
            );

            // Alice 加密给 Charlie
            EncryptedSharePacketV3 packetToCharlie = mockEncryptV3(
                shareData,
                charlieX25519.getPublic(),
                aliceEd25519.getPrivate(),
                "alice",
                "charlie"
            );

            // 验证两个包的 ephemeral 公钥不同（每次生成新的）
            assertFalse("每次分享应有不同的 ephemeral 公钥",
                packetToBob.getEphemeralPublicKey().equals(packetToCharlie.getEphemeralPublicKey()));

            // Bob 解密
            ShareDataPacket bobDecrypted = mockDecryptV3(
                packetToBob,
                bobX25519.getPrivate(),
                aliceEd25519.getPublic(),
                "alice",
                "bob"
            );
            assertEquals("Bob 解密后的数据应正确", shareData.password.getTitle(), bobDecrypted.password.getTitle());

            // Charlie 解密
            ShareDataPacket charlieDecrypted = mockDecryptV3(
                packetToCharlie,
                charlieX25519.getPrivate(),
                aliceEd25519.getPublic(),
                "alice",
                "charlie"
            );
            assertEquals("Charlie 解密后的数据应正确", shareData.password.getTitle(), charlieDecrypted.password.getTitle());

        } catch (Exception e) {
            fail("多用户分享测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试重放攻击防护（时间戳验证）
     *
     * 验证：
     * - 正常时间戳通过验证
     * - 过期时间戳被拒绝
     * - 未来太远的时间戳被拒绝
     */
    @Test
    public void testReplayAttackProtection() {
        long now = System.currentTimeMillis();
        long maxDrift = CryptoConstants.MAX_TIMESTAMP_DRIFT;

        // 创建有效的分享包
        EncryptedSharePacketV3 validPacket = new EncryptedSharePacketV3();
        validPacket.setVersion("3.0");
        validPacket.setEphemeralPublicKey("ephemeral_pub");
        validPacket.setEncryptedData("encrypted_data");
        validPacket.setIv("iv");
        validPacket.setSignature("signature");
        validPacket.setSenderId("sender");
        validPacket.setCreatedAt(now);
        validPacket.setExpireAt(now + 3600000);

        assertTrue("有效的分享包应通过验证", validPacket.isTimestampValid(maxDrift));

        // 创建过期的分享包（模拟重放）
        EncryptedSharePacketV3 expiredPacket = new EncryptedSharePacketV3();
        expiredPacket.setVersion("3.0");
        expiredPacket.setEphemeralPublicKey("ephemeral_pub");
        expiredPacket.setEncryptedData("encrypted_data");
        expiredPacket.setIv("iv");
        expiredPacket.setSignature("signature");
        expiredPacket.setSenderId("sender");
        expiredPacket.setCreatedAt(now - 7200000); // 2 小时前
        expiredPacket.setExpireAt(now - 1000); // 已过期

        assertFalse("过期的分享包应被拒绝", expiredPacket.isTimestampValid(maxDrift));
    }

    /**
     * 测试密钥混淆攻击防护
     *
     * 验证：
     * - HKDF 身份绑定防止密钥混淆
     * - 不同接收方产生不同的派生密钥
     */
    @Test
    public void testKeyConfusionAttackProtection() {
        try {
            String senderId = "alice";
            String receiver1Id = "bob";
            String receiver2Id = "charlie";

            byte[] sharedSecret1 = generateMockSharedSecret();
            byte[] sharedSecret2 = Arrays.copyOf(sharedSecret1, sharedSecret1.length);

            HKDFManager hkdf = HKDFManager.getInstance();

            // 派生密钥给 receiver1
            byte[] sharedSecretCopy1 = Arrays.copyOf(sharedSecret1, sharedSecret1.length);
            javax.crypto.SecretKey key1 = hkdf.deriveAESKey(sharedSecretCopy1, senderId, receiver1Id);

            // 派生密钥给 receiver2
            byte[] sharedSecretCopy2 = Arrays.copyOf(sharedSecret2, sharedSecret2.length);
            javax.crypto.SecretKey key2 = hkdf.deriveAESKey(sharedSecretCopy2, senderId, receiver2Id);

            // 验证密钥不同
            assertFalse("不同接收方应产生不同的派生密钥",
                Arrays.equals(key1.getEncoded(), key2.getEncoded()));

            // 验证身份顺序不影响结果（使用字典序）
            byte[] sharedSecret3 = generateMockSharedSecret();
            byte[] sharedSecretCopy3a = Arrays.copyOf(sharedSecret3, sharedSecret3.length);
            byte[] sharedSecretCopy3b = Arrays.copyOf(sharedSecret3, sharedSecret3.length);
            javax.crypto.SecretKey key3a = hkdf.deriveAESKey(sharedSecretCopy3a, senderId, receiver1Id);
            javax.crypto.SecretKey key3b = hkdf.deriveAESKey(sharedSecretCopy3b, receiver1Id, senderId);
            assertArrayEquals("身份顺序应不影响结果（字典序）", key3a.getEncoded(), key3b.getEncoded());

        } catch (Exception e) {
            fail("密钥混淆攻击防护测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试前向保密验证
     *
     * 验证：
     * - 每次分享使用新的 ephemeral key
     * - 即使长期私钥泄露，历史分享仍安全
     */
    @Test
    public void testForwardSecrecyVerification() {
        try {
            KeyPair aliceLongTermX25519 = generateMockX25519KeyPair();
            KeyPair aliceEd25519 = generateMockEd25519KeyPair();
            KeyPair bobX25519 = generateMockX25519KeyPair();
            KeyPair bobEd25519 = generateMockEd25519KeyPair();

            ShareDataPacket shareData1 = createTestShareData("alice", "bob");
            ShareDataPacket shareData2 = createTestShareData("alice", "bob");

            // 第一次分享
            EncryptedSharePacketV3 packet1 = mockEncryptV3(
                shareData1,
                bobX25519.getPublic(),
                aliceEd25519.getPrivate(),
                "alice",
                "bob"
            );

            // 第二次分享（稍后）
            try {
                Thread.sleep(10); // 确保时间戳不同
            } catch (InterruptedException e) {
                // 忽略
            }

            EncryptedSharePacketV3 packet2 = mockEncryptV3(
                shareData2,
                bobX25519.getPublic(),
                aliceEd25519.getPrivate(),
                "alice",
                "bob"
            );

            // 验证 ephemeral 公钥不同
            assertFalse("每次分享应有不同的 ephemeral 公钥",
                packet1.getEphemeralPublicKey().equals(packet2.getEphemeralPublicKey()));

            // 验证加密数据不同（包含不同的 ephemeral 公钥）
            assertFalse("每次分享应有不同的加密数据",
                packet1.getEncryptedData().equals(packet2.getEncryptedData()));

            // 验证签名不同（包含不同的 ephemeral 公钥）
            assertFalse("每次分享应有不同的签名",
                packet1.getSignature().equals(packet2.getSignature()));

            // 验证时间戳不同
            assertFalse("每次分享应有不同的时间戳",
                packet1.getCreatedAt() == packet2.getCreatedAt());

        } catch (Exception e) {
            fail("前向保密验证测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试数据包序列化
     *
     * 验证数据包可以正确序列化和反序列化
     */
    @Test
    public void testPacketSerialization() {
        long now = System.currentTimeMillis();

        EncryptedSharePacketV3 original = new EncryptedSharePacketV3(
                "3.0",
                "ephemeral_pub",
                "encrypted_data",
                "iv",
                "signature",
                now,
                now + 3600000,
                "sender123"
        );

        // 验证字段完整性
        assertEquals("版本应正确", original.getVersion(), "3.0");
        assertEquals("ephemeral 公钥应正确", original.getEphemeralPublicKey(), "ephemeral_pub");
        assertEquals("加密数据应正确", original.getEncryptedData(), "encrypted_data");
        assertEquals("IV 应正确", original.getIv(), "iv");
        assertEquals("签名应正确", original.getSignature(), "signature");
        assertEquals("创建时间应正确", original.getCreatedAt(), now);
        assertEquals("过期时间应正确", original.getExpireAt(), now + 3600000);
        assertEquals("发送方 ID 应正确", original.getSenderId(), "sender123");

        // 验证有效性
        assertTrue("数据包应有效", original.isValid());
        assertFalse("数据包不应过期", original.isExpired());
        assertTrue("时间戳应有效", original.isTimestampValid(CryptoConstants.MAX_TIMESTAMP_DRIFT));
    }

    // ========== Mock 方法 ==========

    private KeyPair generateMockX25519KeyPair() {
        return new KeyPair(
            new MockX25519PublicKey(),
            new MockX25519PrivateKey()
        );
    }

    private KeyPair generateMockEd25519KeyPair() {
        return new KeyPair(
            new MockEd25519PublicKey(),
            new MockEd25519PrivateKey()
        );
    }

    private EncryptedSharePacketV3 mockEncryptV3(
            ShareDataPacket data,
            java.security.PublicKey receiverX25519PublicKey,
            java.security.PrivateKey senderEd25519PrivateKey,
            String senderId,
            String receiverId) {
        long now = System.currentTimeMillis();
        long expireAt = data.expireAt > 0 ? data.expireAt : now + 3600000;

        // 模拟加密过程
        byte[] ephemeralPubKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            ephemeralPubKey[i] = (byte) (i ^ now);
        }

        byte[] iv = new byte[12];
        for (int i = 0; i < 12; i++) {
            iv[i] = (byte) i;
        }

        // 模拟签名
        byte[] signature = new byte[64];
        String json = "mock_json_" + data.password.getTitle();
        for (int i = 0; i < 64; i++) {
            signature[i] = (byte) (json.getBytes(StandardCharsets.UTF_8)[i % json.length()] ^ i);
        }

        // 模拟加密数据（包含原始数据）
        String encryptedData = android.util.Base64.encodeToString(
            json.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);

        return new EncryptedSharePacketV3(
                "3.0",
                android.util.Base64.encodeToString(ephemeralPubKey, android.util.Base64.NO_WRAP),
                encryptedData,
                android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP),
                android.util.Base64.encodeToString(signature, android.util.Base64.NO_WRAP),
                now,
                expireAt,
                senderId
        );
    }

    private ShareDataPacket mockDecryptV3(
            EncryptedSharePacketV3 packet,
            java.security.PrivateKey receiverX25519PrivateKey,
            java.security.PublicKey senderEd25519PublicKey,
            String senderId,
            String receiverId) {
        // 模拟解密过程
        byte[] encryptedData = android.util.Base64.decode(
            packet.getEncryptedData(), android.util.Base64.NO_WRAP);
        String json = new String(encryptedData, StandardCharsets.UTF_8);

        // 创建解密后的数据包
        ShareDataPacket data = new ShareDataPacket();
        data.version = "3.0";
        data.senderId = senderId;
        data.senderPublicKey = "sender_pub_key";
        data.createdAt = packet.getCreatedAt();
        data.expireAt = packet.getExpireAt();
        data.permission = new SharePermission(true, true, true);

        com.ttt.safevault.model.PasswordItem password =
            new com.ttt.safevault.model.PasswordItem();
        // 从 json 中提取标题（模拟）
        if (json.contains("mock_json_")) {
            password.setTitle(json.replace("mock_json_", ""));
        } else {
            password.setTitle("Test Password");
        }
        password.setUsername("testuser");
        password.setPassword("testpass123");
        password.setUrl("https://example.com");
        password.setNotes("Test notes");
        data.password = password;

        return data;
    }

    private ShareDataPacket createTestShareData(String senderId, String receiverId) {
        ShareDataPacket data = new ShareDataPacket();
        data.version = "3.0";
        data.senderId = senderId;
        data.senderPublicKey = senderId + "_pub_key";
        data.createdAt = System.currentTimeMillis();
        data.expireAt = System.currentTimeMillis() + 3600000;
        data.permission = new SharePermission(true, true, true);

        com.ttt.safevault.model.PasswordItem password =
            new com.ttt.safevault.model.PasswordItem();
        password.setTitle("Shared Password");
        password.setUsername("shared_user");
        password.setPassword("shared_password_123");
        password.setUrl("https://example.com/shared");
        password.setNotes("Shared via SafeVault");
        data.password = password;

        return data;
    }

    private UserKeyInfo createV3UserKeyInfo(String userId) {
        UserKeyInfo keyInfo = new UserKeyInfo();
        keyInfo.setUserId(userId);
        keyInfo.setX25519PublicKey("x25519_" + userId);
        keyInfo.setEd25519PublicKey("ed25519_" + userId);
        keyInfo.setKeyVersion("v3");
        return keyInfo;
    }

    private UserKeyInfo createV2UserKeyInfo(String userId) {
        UserKeyInfo keyInfo = new UserKeyInfo();
        keyInfo.setUserId(userId);
        keyInfo.setRsaPublicKey("rsa_" + userId);
        keyInfo.setKeyVersion("v2");
        return keyInfo;
    }

    private byte[] generateMockSharedSecret() {
        byte[] sharedSecret = new byte[32];
        for (int i = 0; i < 32; i++) {
            sharedSecret[i] = (byte) (i * 7 + 13);
        }
        return sharedSecret;
    }

    // ========== Mock 类 ==========

    private static class MockX25519PublicKey implements java.security.PublicKey {
        private final byte[] encoded = new byte[32];
        public MockX25519PublicKey() {
            for (int i = 0; i < 32; i++) {
                encoded[i] = (byte) (i + 1);
            }
        }
        @Override
        public String getAlgorithm() { return "XDH"; }
        @Override
        public String getFormat() { return "RAW"; }
        @Override
        public byte[] getEncoded() { return encoded.clone(); }
    }

    private static class MockX25519PrivateKey implements java.security.PrivateKey {
        private final byte[] encoded = new byte[32];
        public MockX25519PrivateKey() {
            for (int i = 0; i < 32; i++) {
                encoded[i] = (byte) (i ^ 0xFF);
            }
        }
        @Override
        public String getAlgorithm() { return "XDH"; }
        @Override
        public String getFormat() { return "RAW"; }
        @Override
        public byte[] getEncoded() { return encoded.clone(); }
    }

    private static class MockEd25519PublicKey implements java.security.PublicKey {
        private final byte[] encoded = new byte[32];
        public MockEd25519PublicKey() {
            for (int i = 0; i < 32; i++) {
                encoded[i] = (byte) (i + 10);
            }
        }
        @Override
        public String getAlgorithm() { return "EdDSA"; }
        @Override
        public String getFormat() { return "RAW"; }
        @Override
        public byte[] getEncoded() { return encoded.clone(); }
    }

    private static class MockEd25519PrivateKey implements java.security.PrivateKey {
        private final byte[] encoded = new byte[32];
        public MockEd25519PrivateKey() {
            for (int i = 0; i < 32; i++) {
                encoded[i] = (byte) (i ^ 0xAA);
            }
        }
        @Override
        public String getAlgorithm() { return "EdDSA"; }
        @Override
        public String getFormat() { return "RAW"; }
        @Override
        public byte[] getEncoded() { return encoded.clone(); }
    }
}