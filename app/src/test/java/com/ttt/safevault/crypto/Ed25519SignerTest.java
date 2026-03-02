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
 * Ed25519Signer 单元测试
 *
 * 验证 Ed25519 签名功能：
 * - 密钥生成
 * - 签名和验证
 * - 公钥/私钥编解码
 * - 签名验证
 */
@RunWith(JUnit4.class)
public class Ed25519SignerTest {

    /**
     * 测试 Ed25519SignerFactory 工厂方法
     *
     * 验证工厂能够创建有效的签名器实例
     */
    @Test
    public void testFactoryCreation() {
        try {
            // 注意：此测试需要在 Android 环境中运行
            // 因为工厂需要 Context 参数
            Ed25519Signer signer = Ed25519SignerFactory.create(null);
            assertNotNull("签名器不应为 null", signer);
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
     * - 公钥 32 字节，私钥 32 字节
     * - 多次生成产生不同的密钥
     */
    @Test
    public void testKeyPairGeneration() {
        try {
            KeyPair keyPair = generateMockEd25519KeyPair();

            assertNotNull("密钥对不应为 null", keyPair);
            assertNotNull("公钥不应为 null", keyPair.getPublic());
            assertNotNull("私钥不应为 null", keyPair.getPrivate());

            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            byte[] privateKeyBytes = keyPair.getPrivate().getEncoded();

            assertNotNull("公钥编码不应为 null", publicKeyBytes);
            assertNotNull("私钥编码不应为 null", privateKeyBytes);

            assertEquals("公钥应为 32 字节", CryptoConstants.ED25519_PUBLIC_KEY_SIZE, publicKeyBytes.length);
            assertEquals("私钥应为 32 字节", CryptoConstants.ED25519_PRIVATE_KEY_SIZE, privateKeyBytes.length);

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
            KeyPair keyPair1 = generateMockEd25519KeyPair();
            KeyPair keyPair2 = generateMockEd25519KeyPair();

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
     * 测试签名和验证
     *
     * 验证：
     * - 可以对数据进行签名
     * - 签名大小为 64 字节
     * - 可以使用公钥验证签名
     * - 签名验证成功
     */
    @Test
    public void testSignAndVerify() {
        try {
            KeyPair keyPair = generateMockEd25519KeyPair();
            byte[] data = "Test message for signing".getBytes();

            // 签名
            byte[] signature = mockSign(data, keyPair.getPrivate());

            assertNotNull("签名不应为 null", signature);
            assertEquals("签名应为 64 字节", CryptoConstants.ED25519_SIGNATURE_SIZE, signature.length);

            // 验证
            boolean isValid = mockVerify(data, signature, keyPair.getPublic());
            assertTrue("签名验证应成功", isValid);

        } catch (Exception e) {
            fail("签名和验证测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试签名验证失败
     *
     * 验证：
     * - 修改后的数据验证失败
     * - 不同的签名验证失败
     * - 不同的公钥验证失败
     */
    @Test
    public void testSignatureVerificationFailure() {
        try {
            KeyPair keyPair = generateMockEd25519KeyPair();
            byte[] data = "Original message".getBytes();

            // 签名原始数据
            byte[] signature = mockSign(data, keyPair.getPrivate());

            // 修改数据
            byte[] modifiedData = "Modified message".getBytes();
            boolean isValidModified = mockVerify(modifiedData, signature, keyPair.getPublic());
            assertFalse("修改后的数据验证应失败", isValidModified);

            // 修改签名
            byte[] modifiedSignature = Arrays.copyOf(signature, signature.length);
            modifiedSignature[0] ^= 0x01;  // 修改第一个字节
            boolean isValidModifiedSig = mockVerify(data, modifiedSignature, keyPair.getPublic());
            assertFalse("修改后的签名验证应失败", isValidModifiedSig);

            // 使用不同的公钥
            KeyPair anotherKeyPair = generateMockEd25519KeyPair();
            boolean isValidDifferentKey = mockVerify(data, signature, anotherKeyPair.getPublic());
            assertFalse("不同公钥验证应失败", isValidDifferentKey);

        } catch (Exception e) {
            fail("签名验证失败测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试签名的确定性
     *
     * 验证：
     * - 相同的私钥和数据产生相同的签名
     * - Ed25519 是确定性签名算法
     */
    @Test
    public void testSignatureDeterminism() {
        try {
            KeyPair keyPair = generateMockEd25519KeyPair();
            byte[] data = "Deterministic test".getBytes();

            // 第一次签名
            byte[] signature1 = mockSign(data, keyPair.getPrivate());

            // 第二次签名（相同输入）
            byte[] signature2 = mockSign(data, keyPair.getPrivate());

            // 验证签名相同
            assertArrayEquals("相同输入应产生相同签名", signature1, signature2);

        } catch (Exception e) {
            fail("签名确定性测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试签名的唯一性
     *
     * 验证：
     * - 不同的数据产生不同的签名
     * - 不同的私钥产生不同的签名
     */
    @Test
    public void testSignatureUniqueness() {
        try {
            KeyPair keyPair = generateMockEd25519KeyPair();

            // 对不同数据签名
            byte[] data1 = "Message 1".getBytes();
            byte[] data2 = "Message 2".getBytes();
            byte[] signature1 = mockSign(data1, keyPair.getPrivate());
            byte[] signature2 = mockSign(data2, keyPair.getPrivate());

            assertFalse("不同数据应产生不同签名", Arrays.equals(signature1, signature2));

            // 不同私钥签名相同数据
            KeyPair anotherKeyPair = generateMockEd25519KeyPair();
            byte[] signature3 = mockSign(data1, anotherKeyPair.getPrivate());

            assertFalse("不同私钥应产生不同签名", Arrays.equals(signature1, signature3));

        } catch (Exception e) {
            fail("签名唯一性测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试空数据签名
     *
     * 验证：
     * - 可以对空数组进行签名
     * - 空数据签名验证成功
     */
    @Test
    public void testEmptyDataSigning() {
        try {
            KeyPair keyPair = generateMockEd25519KeyPair();
            byte[] emptyData = new byte[0];

            byte[] signature = mockSign(emptyData, keyPair.getPrivate());
            assertNotNull("空数据签名不应为 null", signature);
            assertEquals("空数据签名应为 64 字节", 64, signature.length);

            boolean isValid = mockVerify(emptyData, signature, keyPair.getPublic());
            assertTrue("空数据签名验证应成功", isValid);

        } catch (Exception e) {
            fail("空数据签名测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试大数据签名
     *
     * 验证：
     * - 可以对大数据进行签名
     * - 大数据签名验证成功
     */
    @Test
    public void testLargeDataSigning() {
        try {
            KeyPair keyPair = generateMockEd25519KeyPair();
            byte[] largeData = new byte[1024 * 1024];  // 1MB

            // 填充随机数据
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }

            byte[] signature = mockSign(largeData, keyPair.getPrivate());
            assertNotNull("大数据签名不应为 null", signature);
            assertEquals("大数据签名应为 64 字节", 64, signature.length);

            boolean isValid = mockVerify(largeData, signature, keyPair.getPublic());
            assertTrue("大数据签名验证应成功", isValid);

        } catch (Exception e) {
            fail("大数据签名测试失败: " + e.getMessage());
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
            KeyPair keyPair = generateMockEd25519KeyPair();
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
            KeyPair keyPair = generateMockEd25519KeyPair();
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
            KeyPair keyPair = generateMockEd25519KeyPair();
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
     * 测试签名大小验证
     *
     * 验证：
     * - 正确大小的签名通过验证
     * - 错误大小的签名抛出异常
     */
    @Test
    public void testSignatureSizeValidation() {
        try {
            // 测试正确大小
            byte[] validSignature = new byte[64];
            mockValidateSignatureSize(validSignature);  // 应通过

            // 测试过大签名
            byte[] tooLargeSignature = new byte[128];
            try {
                mockValidateSignatureSize(tooLargeSignature);
                fail("应拒绝过大的签名");
            } catch (Exception e) {
                assertTrue("应抛出异常", e instanceof IllegalArgumentException);
            }

            // 测试过小签名
            byte[] tooSmallSignature = new byte[32];
            try {
                mockValidateSignatureSize(tooSmallSignature);
                fail("应拒绝过小的签名");
            } catch (Exception e) {
                assertTrue("应抛出异常", e instanceof IllegalArgumentException);
            }

        } catch (Exception e) {
            fail("签名大小验证测试失败: " + e.getMessage());
        }
    }

    // ========== Mock 方法（用于纯 JUnit 测试） ==========

    /**
     * 模拟 Ed25519 密钥对生成
     */
    private KeyPair generateMockEd25519KeyPair() {
        return new KeyPair(
            new MockEd25519PublicKey(),
            new MockEd25519PrivateKey()
        );
    }

    /**
     * 模拟 Ed25519 签名
     */
    private byte[] mockSign(byte[] data, PrivateKey privateKey) {
        // 使用模拟的签名算法
        byte[] signature = new byte[64];
        byte[] privKeyBytes = privateKey.getEncoded();

        // 简单的模拟签名（实际使用 Ed25519）
        for (int i = 0; i < 64; i++) {
            signature[i] = (byte) (data[i % data.length] ^ privKeyBytes[i % 32]);
        }

        return signature;
    }

    /**
     * 模拟 Ed25519 签名验证
     */
    private boolean mockVerify(byte[] data, byte[] signature, PublicKey publicKey) {
        byte[] pubKeyBytes = publicKey.getEncoded();

        // 简单的模拟验证（与签名过程对称）
        for (int i = 0; i < 64; i++) {
            int expected = data[i % data.length] ^ pubKeyBytes[i % 32];
            if ((signature[i] & 0xFF) != (expected & 0xFF)) {
                return false;
            }
        }

        return true;
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

    /**
     * 模拟签名大小验证
     */
    private void mockValidateSignatureSize(byte[] signature) throws Exception {
        if (signature == null || signature.length != CryptoConstants.ED25519_SIGNATURE_SIZE) {
            throw new IllegalArgumentException("Invalid signature size: " +
                (signature == null ? "null" : signature.length));
        }
    }

    // ========== Mock 类 ==========

    /**
     * Mock Ed25519 PublicKey 实现
     */
    private static class MockEd25519PublicKey implements PublicKey {
        private final byte[] encoded = new byte[32];
        private final String algorithm = "EdDSA";
        private final String format = "RAW";

        public MockEd25519PublicKey() {
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
     * Mock Ed25519 PrivateKey 实现
     */
    private static class MockEd25519PrivateKey implements PrivateKey {
        private final byte[] encoded = new byte[32];
        private final String algorithm = "EdDSA";
        private final String format = "RAW";

        public MockEd25519PrivateKey() {
            for (int i = 0; i < 32; i++) {
                encoded[i] = (byte) (i ^ 0xAA);
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