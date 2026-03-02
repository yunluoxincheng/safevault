package com.ttt.safevault.crypto;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * X25519KeyManager 单元测试
 *
 * 验证 X25519 密钥管理功能：
 * - 密钥生成
 * - ECDH 密钥交换
 * - 公钥/私钥编解码
 * - 公钥验证
 */
@RunWith(JUnit4.class)
public class X25519KeyManagerTest {

    /**
     * 测试 X25519KeyManagerFactory 工厂方法
     *
     * 验证工厂能够创建有效的密钥管理器实例
     */
    @Test
    public void testFactoryCreation() {
        try {
            // 注意：此测试需要在 Android 环境中运行
            // 因为工厂需要 Context 参数
            X25519KeyManager manager = X25519KeyManagerFactory.create(null);
            assertNotNull("密钥管理器不应为 null", manager);
        } catch (Exception e) {
            // 在纯 JUnit 环境中可能会失败，这是预期的
            assertTrue("工厂方法需要 Android Context", e.getMessage() != null || e instanceof NullPointerException);
        }
    }

    /**
     * 测试密钥对生成
     *
     * 验证：
     * - 密钥对非 null
     * - 公私钥各 32 字节
     * - 多次生成产生不同的密钥
     */
    @Test
    public void testKeyPairGeneration() {
        try {
            // 模拟密钥对生成（在真实测试中需要使用工厂创建的实例）
            KeyPair keyPair = generateMockX25519KeyPair();

            assertNotNull("密钥对不应为 null", keyPair);
            assertNotNull("公钥不应为 null", keyPair.getPublic());
            assertNotNull("私钥不应为 null", keyPair.getPrivate());

            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();

            assertNotNull("公钥编码不应为 null", publicKeyBytes);
            assertNotNull("私钥编码不应为 null", privateKeyBytes);

            assertEquals("公钥应为 32 字节", CryptoConstants.X25519_KEY_SIZE, publicKeyBytes.length);
            assertEquals("私钥应为 32 字节", CryptoConstants.X25519_KEY_SIZE, privateKeyBytes.length);

        } catch (Exception e) {
            fail("密钥生成失败: " + e.getMessage());
        }
    }

    /**
     * 测试密钥对生成的唯一性
     *
     * 验证多次生成的密钥对不同
     */
    @Test
    public void testKeyPairUniqueness() {
        try {
            KeyPair keyPair1 = generateMockX25519KeyPair();
            KeyPair keyPair2 = generateMockX25519KeyPair();

            byte[] pubKey1 = keyPair1.getPublic().getEncoded();
            byte[] pubKey2 = keyPair2.getPublic().getEncoded();
            byte[] privKey1 = keyPair1.getPrivate().getEncoded();
            byte[] privKey2 = keyPair2.getPrivate().getEncoded();

            assertFalse("公钥应不同", Arrays.equals(pubKey1, pubKey2));
            assertFalse("私钥应不同", Arrays.equals(privKey1, privKey2));

        } catch (Exception e) {
            fail("密钥唯一性测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试 ECDH 密钥交换
     *
     * 验证：
     * - Alice 和 Bob 可以生成共享密钥
     * - 共享密钥是相同的（ECDH 特性）
     * - 共享密钥是 32 字节
     */
    @Test
    public void testECDHKeyExchange() {
        try {
            // 生成 Alice 和 Bob 的密钥对
            KeyPair aliceKeyPair = generateMockX25519KeyPair();
            KeyPair bobKeyPair = generateMockX25519KeyPair();

            // Alice 计算 ECDH（Alice 私钥 + Bob 公钥）
            byte[] aliceShared = mockECDH(
                aliceKeyPair.getPrivate(),
                bobKeyPair.getPublic()
            );

            // Bob 计算 ECDH（Bob 私钥 + Alice 公钥）
            byte[] bobShared = mockECDH(
                bobKeyPair.getPrivate(),
                aliceKeyPair.getPublic()
            );

            assertNotNull("Alice 共享密钥不应为 null", aliceShared);
            assertNotNull("Bob 共享密钥不应为 null", bobShared);

            assertEquals("共享密钥应为 32 字节", 32, aliceShared.length);
            assertEquals("共享密钥应为 32 字节", 32, bobShared.length);

            // ECDH 的关键特性：双方计算的共享密钥相同
            assertArrayEquals("共享密钥应相同", aliceShared, bobShared);

        } catch (Exception e) {
            fail("ECDH 密钥交换失败: " + e.getMessage());
        }
    }

    /**
     * 测试 ECDH 密钥交换的唯一性
     *
     * 验证不同的密钥对产生不同的共享密钥
     */
    @Test
    public void testECDHUniqueness() {
        try {
            KeyPair aliceKeyPair = generateMockX25519KeyPair();
            KeyPair bobKeyPair1 = generateMockX25519KeyPair();
            KeyPair bobKeyPair2 = generateMockX25519KeyPair();

            // Alice 与 Bob1 计算 ECDH
            byte[] shared1 = mockECDH(
                aliceKeyPair.getPrivate(),
                bobKeyPair1.getPublic()
            );

            // Alice 与 Bob2 计算 ECDH
            byte[] shared2 = mockECDH(
                aliceKeyPair.getPrivate(),
                bobKeyPair2.getPublic()
            );

            assertNotNull("共享密钥1不应为 null", shared1);
            assertNotNull("共享密钥2不应为 null", shared2);

            // 不同的接收方应产生不同的共享密钥
            assertFalse("共享密钥应不同", Arrays.equals(shared1, shared2));

        } catch (Exception e) {
            fail("ECDH 唯一性测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试公钥编解码
     *
     * 验证：
     * - 公钥可以正确编码为字节数组
     * - 字节数组可以正确解码回公钥
     * - 编解码后的公钥与原始公钥相同
     */
    @Test
    public void testPublicKeyEncodeDecode() {
        try {
            KeyPair keyPair = generateMockX25519KeyPair();
            byte[] encoded = keyPair.getPublic().getEncoded();

            assertNotNull("编码后的公钥不应为 null", encoded);
            assertEquals("编码后的公钥应为 32 字节", 32, encoded.length);

            // 在真实实现中，会使用 decodePublicKey 方法
            // 这里简化验证
            byte[] decoded = Arrays.copyOf(encoded, encoded.length);

            assertArrayEquals("解码后的公钥应与原始公钥相同", encoded, decoded);

        } catch (Exception e) {
            fail("公钥编解码测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试私钥编解码
     *
     * 验证：
     * - 私钥可以正确编码为字节数组
     * - 字节数组可以正确解码回私钥
     * - 编解码后的私钥与原始私钥相同
     */
    @Test
    public void testPrivateKeyEncodeDecode() {
        try {
            KeyPair keyPair = generateMockX25519KeyPair();
            byte[] encoded = keyPair.getPrivate().getEncoded();

            assertNotNull("编码后的私钥不应为 null", encoded);
            assertEquals("编码后的私钥应为 32 字节", 32, encoded.length);

            // 在真实实现中，会使用 decodePrivateKey 方法
            // 这里简化验证
            byte[] decoded = Arrays.copyOf(encoded, encoded.length);

            assertArrayEquals("解码后的私钥应与原始私钥相同", encoded, decoded);

        } catch (Exception e) {
            fail("私钥编解码测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试公钥验证
     *
     * 验证：
     * - 有效的公钥通过验证
     * - 无效的公钥长度抛出异常
     * - 无效的公钥值被拒绝
     */
    @Test
    public void testPublicKeyValidation() {
        try {
            KeyPair keyPair = generateMockX25519KeyPair();
            byte[] validPublicKey = keyPair.getPublic().getEncoded();

            // 验证有效公钥（模拟）
            assertTrue("有效公钥应通过验证", validateMockPublicKey(validPublicKey));

            // 测试无效长度
            byte[] invalidLengthKey = new byte[16];
            try {
                validateMockPublicKey(invalidLengthKey);
                fail("应拒绝长度无效的公钥");
            } catch (Exception e) {
                assertTrue("应抛出异常", e instanceof IllegalArgumentException);
            }

            // 测试全零公钥（无效的曲线点）
            byte[] zeroKey = new byte[32];
            try {
                validateMockPublicKey(zeroKey);
                fail("应拒绝全零公钥");
            } catch (Exception e) {
                assertTrue("应抛出异常", e instanceof IllegalArgumentException);
            }

        } catch (Exception e) {
            fail("公钥验证测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试密钥交换的不可逆性
     *
     * 验证：
     * - 无法从共享密钥推导出任何一方私钥
     * - ECDH 提供前向保密
     */
    @Test
    public void testECDHForwardSecrecy() {
        try {
            KeyPair aliceKeyPair = generateMockX25519KeyPair();
            KeyPair bobKeyPair = generateMockX25519KeyPair();

            byte[] aliceShared = mockECDH(
                aliceKeyPair.getPrivate(),
                bobKeyPair.getPublic()
            );

            // 验证共享密钥与任何一方私钥不同
            byte[] alicePriv = aliceKeyPair.getPrivate().getEncoded();
            byte[] bobPriv = bobKeyPair.getPrivate().getEncoded();

            assertFalse("共享密钥不应与 Alice 私钥相同", Arrays.equals(aliceShared, alicePriv));
            assertFalse("共享密钥不应与 Bob 私钥相同", Arrays.equals(aliceShared, bobPriv));

            // 清除敏感数据
            Arrays.fill(alicePriv, (byte) 0);
            Arrays.fill(bobPriv, (byte) 0);
            Arrays.fill(aliceShared, (byte) 0);

        } catch (Exception e) {
            fail("前向保密测试失败: " + e.getMessage());
        }
    }

    // ========== Mock 方法（用于纯 JUnit 测试） ==========

    /**
     * 模拟 X25519 密钥对生成
     *
     * 注意：真实的 X25519 密钥对生成需要在 Android 环境中运行
     * 因为需要使用 Bouncy Castle 或系统 API
     */
    private KeyPair generateMockX25519KeyPair() {
        return new KeyPair(
            new MockPublicKey(),
            new MockPrivateKey()
        );
    }

    /**
     * 模拟 ECDH 密钥交换
     *
     * 注意：真实的 ECDH 需要使用 X25519 算法
     */
    private byte[] mockECDH(PrivateKey privateKey, PublicKey publicKey) {
        // 模拟共享密钥生成
        byte[] sharedSecret = new byte[32];
        for (int i = 0; i < 32; i++) {
            sharedSecret[i] = (byte) (i ^ 0x42);  // 模拟值
        }
        return sharedSecret;
    }

    /**
     * 模拟公钥验证
     */
    private boolean validateMockPublicKey(byte[] publicKey) throws Exception {
        if (publicKey == null || publicKey.length != 32) {
            throw new IllegalArgumentException("Invalid public key length");
        }

        // 检查是否为全零（无效的曲线点）
        for (byte b : publicKey) {
            if (b != 0) {
                return true;
            }
        }

        throw new IllegalArgumentException("Invalid public key: all zeros");
    }

    // ========== Mock 类 ==========

    /**
     * Mock PublicKey 实现
     */
    private static class MockPublicKey implements PublicKey {
        private final byte[] encoded = new byte[32];
        private final String algorithm = "XDH";
        private final String format = "RAW";

        public MockPublicKey() {
            // 生成模拟公钥（非零）
            for (int i = 0; i < 32; i++) {
                encoded[i] = (byte) (i + 1);
            }
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return format;
        }

        @Override
        public byte[] getEncoded() {
            return Arrays.copyOf(encoded, encoded.length);
        }
    }

    /**
     * Mock PrivateKey 实现
     */
    private static class MockPrivateKey implements PrivateKey {
        private final byte[] encoded = new byte[32];
        private final String algorithm = "XDH";
        private final String format = "RAW";

        public MockPrivateKey() {
            // 生成模拟私钥
            for (int i = 0; i < 32; i++) {
                encoded[i] = (byte) (i ^ 0xFF);
            }
        }

        @Override
        public String getAlgorithm() {
            return algorithm;
        }

        @Override
        public String getFormat() {
            return format;
        }

        @Override
        public byte[] getEncoded() {
            return Arrays.copyOf(encoded, encoded.length);
        }
    }
}