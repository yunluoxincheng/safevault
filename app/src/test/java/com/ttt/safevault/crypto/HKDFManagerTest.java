package com.ttt.safevault.crypto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;

import javax.crypto.SecretKey;

import static org.junit.Assert.*;

/**
 * HKDFManager 单元测试
 *
 * 验证 HKDF 密钥派生功能：
 * - Extract 阶段
 * - Expand 阶段
 * - 完整的 HKDF 流程
 * - 身份绑定
 * - 确定性输出
 */
@RunWith(JUnit4.class)
public class HKDFManagerTest {

    private static final String TAG = "HKDFManagerTest";
    private final HKDFManager hkdfManager = HKDFManager.getInstance();

    /**
     * 测试 HKDFManager 单例模式
     *
     * 验证：
     * - getInstance() 返回非 null
     * - 多次调用返回同一实例
     */
    @Test
    public void testSingleton() {
        HKDFManager instance1 = HKDFManager.getInstance();
        HKDFManager instance2 = HKDFManager.getInstance();

        assertNotNull("实例不应为 null", instance1);
        assertNotNull("实例不应为 null", instance2);
        assertSame("应返回同一实例", instance1, instance2);
    }

    /**
     * 测试基本密钥派生
     *
     * 验证：
     * - 可以从共享密钥派生 AES 密钥
     * - 派生密钥长度为 32 字节
     * - 派生密钥算法为 AES
     */
    @Test
    public void testBasicKeyDerivation() {
        try {
            byte[] sharedSecret = generateMockSharedSecret();
            String senderId = "user1";
            String receiverId = "user2";

            SecretKey aesKey = hkdfManager.deriveAESKey(sharedSecret, senderId, receiverId);

            assertNotNull("派生的 AES 密钥不应为 null", aesKey);
            assertEquals("密钥算法应为 AES", "AES", aesKey.getAlgorithm());
            assertEquals("密钥长度应为 32 字节", 32, aesKey.getEncoded().length);

        } catch (Exception e) {
            fail("基本密钥派生测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试密钥派生的确定性
     *
     * 验证：
     * - 相同的输入产生相同的输出
     * - HKDF 是确定性算法
     */
    @Test
    public void testDeterministicDerivation() {
        try {
            byte[] sharedSecret = generateMockSharedSecret();
            String senderId = "alice";
            String receiverId = "bob";

            // 第一次派生
            byte[] sharedSecret1 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            SecretKey key1 = hkdfManager.deriveAESKey(sharedSecret1, senderId, receiverId);

            // 第二次派生（相同输入）
            byte[] sharedSecret2 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            SecretKey key2 = hkdfManager.deriveAESKey(sharedSecret2, senderId, receiverId);

            assertArrayEquals("相同输入应产生相同密钥", key1.getEncoded(), key2.getEncoded());

        } catch (Exception e) {
            fail("确定性派生测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试不同输入产生不同输出
     *
     * 验证：
     * - 不同的共享密钥产生不同的派生密钥
     * - 不同的身份 ID 产生不同的派生密钥
     */
    @Test
    public void testDifferentInputs() {
        try {
            String senderId = "sender";
            String receiverId = "receiver";

            // 不同的共享密钥
            byte[] sharedSecret1 = generateMockSharedSecret();
            byte[] sharedSecret2 = generateMockSharedSecret();
            sharedSecret2[0] ^= 0x01;  // 修改一个字节

            byte[] sharedSecretCopy1 = Arrays.copyOf(sharedSecret1, sharedSecret1.length);
            byte[] sharedSecretCopy2 = Arrays.copyOf(sharedSecret2, sharedSecret2.length);

            SecretKey key1 = hkdfManager.deriveAESKey(sharedSecretCopy1, senderId, receiverId);
            SecretKey key2 = hkdfManager.deriveAESKey(sharedSecretCopy2, senderId, receiverId);

            assertFalse("不同共享密钥应产生不同派生密钥",
                Arrays.equals(key1.getEncoded(), key2.getEncoded()));

            // 不同的身份 ID
            byte[] sharedSecret3 = generateMockSharedSecret();
            byte[] sharedSecretCopy3 = Arrays.copyOf(sharedSecret3, sharedSecret3.length);

            SecretKey key3 = hkdfManager.deriveAESKey(sharedSecretCopy3, "other_sender", receiverId);
            SecretKey key4 = hkdfManager.deriveAESKey(Arrays.copyOf(sharedSecret3, sharedSecret3.length), senderId, "other_receiver");

            assertFalse("不同发送方 ID 应产生不同派生密钥",
                Arrays.equals(key1.getEncoded(), key3.getEncoded()));
            assertFalse("不同接收方 ID 应产生不同派生密钥",
                Arrays.equals(key1.getEncoded(), key4.getEncoded()));

        } catch (Exception e) {
            fail("不同输入测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试身份绑定防止密钥混淆
     *
     * 验证：
     * - 身份 ID 的顺序不影响派生结果
     * - senderId=A, receiverId=B 与 senderId=B, receiverId=A 产生相同结果
     * - 这防止了密钥混淆攻击
     */
    @Test
    public void testIdentityBinding() {
        try {
            byte[] sharedSecret = generateMockSharedSecret();

            byte[] sharedSecret1 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            SecretKey key1 = hkdfManager.deriveAESKey(sharedSecret1, "alice", "bob");

            byte[] sharedSecret2 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            SecretKey key2 = hkdfManager.deriveAESKey(sharedSecret2, "bob", "alice");

            assertArrayEquals("身份顺序应不影响派生结果（使用字典序）", key1.getEncoded(), key2.getEncoded());

        } catch (Exception e) {
            fail("身份绑定测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试共享密钥的清除
     *
     * 验证：
     * - 派生后原始共享密钥被清除
     * - 这是为了安全考虑，防止内存中的敏感数据泄露
     */
    @Test
    public void testSharedSecretWiping() {
        try {
            byte[] sharedSecret = generateMockSharedSecret();
            byte[] originalCopy = Arrays.copyOf(sharedSecret, sharedSecret.length);

            hkdfManager.deriveAESKey(sharedSecret, "user1", "user2");

            // 验证原始共享密钥被清除（全为零）
            boolean allZero = true;
            for (byte b : sharedSecret) {
                if (b != 0) {
                    allZero = false;
                    break;
                }
            }
            assertTrue("原始共享密钥应被清除", allZero);

            // 验证派生密钥与原始共享密钥不同
            byte[] sharedSecret3 = generateMockSharedSecret();
            SecretKey key = hkdfManager.deriveAESKey(sharedSecret3, "user1", "user2");
            assertFalse("派生密钥应与原始共享密钥不同",
                Arrays.equals(key.getEncoded(), originalCopy));

        } catch (Exception e) {
            fail("共享密钥清除测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试空值输入处理
     *
     * 验证：
     * - null 共享密钥抛出异常
     * - 空 sharedSecret 抛出异常
     * - null senderId 抛出异常
     * - 空 senderId 抛出异常
     * - null receiverId 抛出异常
     * - 空 receiverId 抛出异常
     */
    @Test
    public void testNullInputHandling() {
        try {
            byte[] sharedSecret = generateMockSharedSecret();

            // 测试 null 共享密钥
            try {
                hkdfManager.deriveAESKey(null, "sender", "receiver");
                fail("应拒绝 null 共享密钥");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'null or empty'",
                    e.getMessage().contains("null or empty"));
            }

            // 测试空 sharedSecret
            try {
                hkdfManager.deriveAESKey(new byte[0], "sender", "receiver");
                fail("应拒绝空 sharedSecret");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'null or empty'",
                    e.getMessage().contains("null or empty"));
            }

            // 测试 null senderId
            try {
                byte[] sharedSecretCopy = Arrays.copyOf(sharedSecret, sharedSecret.length);
                hkdfManager.deriveAESKey(sharedSecretCopy, null, "receiver");
                fail("应拒绝 null senderId");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'Sender ID'",
                    e.getMessage().contains("Sender ID"));
            }

            // 测试空 senderId
            try {
                byte[] sharedSecretCopy = Arrays.copyOf(sharedSecret, sharedSecret.length);
                hkdfManager.deriveAESKey(sharedSecretCopy, "", "receiver");
                fail("应拒绝空 senderId");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'Sender ID'",
                    e.getMessage().contains("Sender ID"));
            }

            // 测试 null receiverId
            try {
                byte[] sharedSecretCopy = Arrays.copyOf(sharedSecret, sharedSecret.length);
                hkdfManager.deriveAESKey(sharedSecretCopy, "sender", null);
                fail("应拒绝 null receiverId");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'Receiver ID'",
                    e.getMessage().contains("Receiver ID"));
            }

            // 测试空 receiverId
            try {
                byte[] sharedSecretCopy = Arrays.copyOf(sharedSecret, sharedSecret.length);
                hkdfManager.deriveAESKey(sharedSecretCopy, "sender", "");
                fail("应拒绝空 receiverId");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'Receiver ID'",
                    e.getMessage().contains("Receiver ID"));
            }

        } catch (Exception e) {
            fail("空值输入处理测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 deriveKey 方法（不带身份绑定）
     *
     * 验证：
     * - 可以派生指定长度的密钥
     * - 不同长度产生不同输出
     */
    @Test
    public void testDeriveKeyWithoutIdentity() {
        try {
            byte[] sharedSecret = generateMockSharedSecret();

            // 派生 16 字节密钥
            byte[] sharedSecret1 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            byte[] key16 = hkdfManager.deriveKey(sharedSecret1, 16);

            assertNotNull("派生的密钥不应为 null", key16);
            assertEquals("密钥长度应为 16 字节", 16, key16.length);

            // 派生 32 字节密钥
            byte[] sharedSecret2 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            byte[] key32 = hkdfManager.deriveKey(sharedSecret2, 32);

            assertNotNull("派生的密钥不应为 null", key32);
            assertEquals("密钥长度应为 32 字节", 32, key32.length);

            // 派生 64 字节密钥
            byte[] sharedSecret3 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            byte[] key64 = hkdfManager.deriveKey(sharedSecret3, 64);

            assertNotNull("派生的密钥不应为 null", key64);
            assertEquals("密钥长度应为 64 字节", 64, key64.length);

            // 验证不同长度产生不同输出（至少前 16 字节不同）
            boolean prefixDifferent = false;
            for (int i = 0; i < 16; i++) {
                if (key16[i] != key32[i]) {
                    prefixDifferent = true;
                    break;
                }
            }
            assertTrue("不同长度应产生不同输出", prefixDifferent);

        } catch (Exception e) {
            fail("不带身份绑定的密钥派生测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 deriveKey 方法的空值处理
     *
     * 验证：
     * - null 共享密钥抛出异常
     * - 空 sharedSecret 抛出异常
     * - 无效长度抛出异常
     */
    @Test
    public void testDeriveKeyNullInputHandling() {
        try {
            // 测试 null 共享密钥
            try {
                hkdfManager.deriveKey(null, 32);
                fail("应拒绝 null 共享密钥");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'null or empty'",
                    e.getMessage().contains("null or empty"));
            }

            // 测试空 sharedSecret
            try {
                hkdfManager.deriveKey(new byte[0], 32);
                fail("应拒绝空 sharedSecret");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'null or empty'",
                    e.getMessage().contains("null or empty"));
            }

            // 测试无效长度（0）
            try {
                byte[] sharedSecret = generateMockSharedSecret();
                hkdfManager.deriveKey(sharedSecret, 0);
                fail("应拒绝 0 长度");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'positive'",
                    e.getMessage().contains("positive"));
            }

            // 测试无效长度（负数）
            try {
                byte[] sharedSecret = generateMockSharedSecret();
                hkdfManager.deriveKey(sharedSecret, -1);
                fail("应拒绝负长度");
            } catch (IllegalArgumentException e) {
                assertTrue("异常消息应包含 'positive'",
                    e.getMessage().contains("positive"));
            }

        } catch (Exception e) {
            fail("deriveKey 空值输入处理测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 HKDF 参数配置
     *
     * 验证：
     * - 哈希算法为 HMAC-SHA256
     * - 输出长度为 32 字节
     * - info 参数前缀正确
     */
    @Test
    public void testHKDFConfiguration() {
        assertEquals("哈希算法应为 HMAC-SHA256", "HmacSHA256", CryptoConstants.HKDF_HASH_ALGORITHM);
        assertEquals("HKDF 输出长度应为 32 字节", 32, CryptoConstants.HKDF_OUTPUT_SIZE);
        assertTrue("Info 参数前缀应包含 'safevault-sharing'",
            CryptoConstants.HKDF_INFO_PREFIX.contains("safevault-sharing"));
    }

    /**
     * 测试特殊字符身份 ID
     *
     * 验证：
     * - 特殊字符不影响派生
     * - Unicode 字符正常工作
     */
    @Test
    public void testSpecialCharactersInIdentity() {
        try {
            byte[] sharedSecret = generateMockSharedSecret();

            byte[] sharedSecret1 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            SecretKey key1 = hkdfManager.deriveAESKey(sharedSecret1,
                "user@domain.com", "user2");

            byte[] sharedSecret2 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            SecretKey key2 = hkdfManager.deriveAESKey(sharedSecret2,
                "用户1", "用户2");

            byte[] sharedSecret3 = Arrays.copyOf(sharedSecret, sharedSecret.length);
            SecretKey key3 = hkdfManager.deriveAESKey(sharedSecret3,
                "user\nwith\twhitespace", "receiver");

            assertNotNull("特殊字符应正常处理", key1);
            assertNotNull("Unicode 字符应正常处理", key2);
            assertNotNull("空白字符应正常处理", key3);

            // 验证输出是不同的
            assertFalse("特殊字符应产生不同输出",
                Arrays.equals(key1.getEncoded(), key2.getEncoded()));

        } catch (Exception e) {
            fail("特殊字符身份 ID 测试失败: " + e.getMessage());
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 生成模拟的共享密钥
     *
     * 模拟 X25519 ECDH 产生的 32 字节共享密钥
     */
    private byte[] generateMockSharedSecret() {
        byte[] sharedSecret = new byte[32];
        for (int i = 0; i < 32; i++) {
            sharedSecret[i] = (byte) (i * 7 + 13);  // 模拟值
        }
        return sharedSecret;
    }
}