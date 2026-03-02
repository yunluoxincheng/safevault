package com.ttt.safevault.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.security.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密码算法性能对比测试
 *
 * 测试 RSA-2048 与 X25519/Ed25519 的性能差异
 * 包括密钥生成、加密/解密、签名/验证的速度对比
 *
 * @since SafeVault 3.6.0
 */
@DisplayName("密码算法性能对比测试")
class CryptoAlgorithmPerformanceTest {

    private X25519KeyManager x25519KeyManager;
    private Ed25519Signer ed25519Signer;

    // 测试数据
    private static final byte[] TEST_DATA = "This is a test data for performance measurement. ".getBytes();
    private static final int WARMUP_ITERATIONS = 10;
    private static final int TEST_ITERATIONS = 100;

    @BeforeEach
    void setUp() throws Exception {
        x25519KeyManager = X25519KeyManagerFactory.create();
        ed25519Signer = Ed25519SignerFactory.create();

        // 预热 JVM
        warmup();
    }

    /**
     * 预热 JVM
     */
    private void warmup() throws Exception {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            KeyPair x25519KeyPair = x25519KeyManager.generateKeyPair();
            KeyPair ed25519KeyPair = ed25519Signer.generateKeyPair();

            byte[] sharedSecret = x25519KeyManager.performECDH(
                x25519KeyPair.getPrivate(),
                x25519KeyPair.getPublic()
            );

            byte[] signature = ed25519Signer.sign(TEST_DATA, ed25519KeyPair.getPrivate());
            boolean verified = ed25519Signer.verify(TEST_DATA, signature, ed25519KeyPair.getPublic());

            // RSA 预热
            KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(2048);
            KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

            Signature rsaSignature = Signature.getInstance("SHA256withRSA");
            rsaSignature.initSign(rsaKeyPair.getPrivate());
            rsaSignature.update(TEST_DATA);
            byte[] rsaSig = rsaSignature.sign();

            rsaSignature.initVerify(rsaKeyPair.getPublic());
            rsaSignature.update(TEST_DATA);
            rsaSignature.verify(rsaSig);
        }
    }

    /**
     * 性能测试结果
     */
    static class PerformanceResult {
        String algorithm;
        String operation;
        long totalTimeMs;
        long avgTimeMs;
        long minTimeMs;
        long maxTimeMs;
        int iterations;

        PerformanceResult(String algorithm, String operation, long totalTimeMs, List<Long> timings) {
            this.algorithm = algorithm;
            this.operation = operation;
            this.totalTimeMs = totalTimeMs;
            this.avgTimeMs = totalTimeMs / timings.size();
            this.minTimeMs = timings.stream().min(Long::compare).orElse(0L);
            this.maxTimeMs = timings.stream().max(Long::compare).orElse(0L);
            this.iterations = timings.size();
        }

        @Override
        public String toString() {
            return String.format("%-12s %-20s | 总计: %5dms | 平均: %4.1fms | 最小: %4dms | 最大: %5dms | 迭代: %d",
                algorithm, operation, totalTimeMs, avgTimeMs / 1000.0, minTimeMs / 1000, maxTimeMs / 1000, iterations);
        }

        /**
         * 获取性能提升倍数
         */
        double getSpeedupFactor(PerformanceResult baseline) {
            return (double) baseline.avgTimeMs / avgTimeMs;
        }
    }

    /**
     * 测试：X25519 密钥生成性能
     */
    @Test
    @DisplayName("X25519 密钥生成性能")
    void testX25519KeyGenerationPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            KeyPair keyPair = x25519KeyManager.generateKeyPair();
            long end = System.nanoTime();
            timings.add(end - start);
            assertNotNull(keyPair);
        }

        PerformanceResult result = new PerformanceResult("X25519", "密钥生成", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 5_000_000, "X25519 密钥生成应该在 5ms 内完成"); // 5ms
    }

    /**
     * 测试：RSA-2048 密钥生成性能
     */
    @Test
    @DisplayName("RSA-2048 密钥生成性能")
    void testRsaKeyGenerationPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            long end = System.nanoTime();
            timings.add(end - start);
            assertNotNull(keyPair);
        }

        PerformanceResult result = new PerformanceResult("RSA-2048", "密钥生成", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 100_000_000, "RSA-2048 密钥生成应该在 100ms 内完成"); // 100ms
    }

    /**
     * 测试：Ed25519 密钥生成性能
     */
    @Test
    @DisplayName("Ed25519 密钥生成性能")
    void testEd25519KeyGenerationPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            KeyPair keyPair = ed25519Signer.generateKeyPair();
            long end = System.nanoTime();
            timings.add(end - start);
            assertNotNull(keyPair);
        }

        PerformanceResult result = new PerformanceResult("Ed25519", "密钥生成", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 5_000_000, "Ed25519 密钥生成应该在 5ms 内完成"); // 5ms
    }

    /**
     * 测试：X25519 ECDH 性能
     */
    @Test
    @DisplayName("X25519 ECDH 性能")
    void testX25519ECDHPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        KeyPair aliceKeyPair = x25519KeyManager.generateKeyPair();
        KeyPair bobKeyPair = x25519KeyManager.generateKeyPair();

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            byte[] sharedSecret = x25519KeyManager.performECDH(
                aliceKeyPair.getPrivate(),
                bobKeyPair.getPublic()
            );
            long end = System.nanoTime();
            timings.add(end - start);
            assertNotNull(sharedSecret);
            assertEquals(32, sharedSecret.length);
        }

        PerformanceResult result = new PerformanceResult("X25519", "ECDH 密钥交换", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 2_000_000, "X25519 ECDH 应该在 2ms 内完成"); // 2ms
    }

    /**
     * 测试：Ed25519 签名性能
     */
    @Test
    @DisplayName("Ed25519 签名性能")
    void testEd25519SigningPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        KeyPair keyPair = ed25519Signer.generateKeyPair();

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            byte[] signature = ed25519Signer.sign(TEST_DATA, keyPair.getPrivate());
            long end = System.nanoTime();
            timings.add(end - start);
            assertNotNull(signature);
            assertEquals(64, signature.length);
        }

        PerformanceResult result = new PerformanceResult("Ed25519", "签名", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 500_000, "Ed25519 签名应该在 0.5ms 内完成"); // 0.5ms
    }

    /**
     * 测试：Ed25519 验证性能
     */
    @Test
    @DisplayName("Ed25519 验证性能")
    void testEd25519VerificationPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        KeyPair keyPair = ed25519Signer.generateKeyPair();
        byte[] signature = ed25519Signer.sign(TEST_DATA, keyPair.getPrivate());

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            boolean verified = ed25519Signer.verify(TEST_DATA, signature, keyPair.getPublic());
            long end = System.nanoTime();
            timings.add(end - start);
            assertTrue(verified);
        }

        PerformanceResult result = new PerformanceResult("Ed25519", "验证", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 500_000, "Ed25519 验证应该在 0.5ms 内完成"); // 0.5ms
    }

    /**
     * 测试：RSA-2048 SHA256withRSA 签名性能
     */
    @Test
    @DisplayName("RSA-2048 SHA256withRSA 签名性能")
    void testRsaSigningPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            signature.update(TEST_DATA);
            byte[] sig = signature.sign();
            long end = System.nanoTime();
            timings.add(end - start);
            assertNotNull(sig);
        }

        PerformanceResult result = new PerformanceResult("RSA-2048", "SHA256withRSA 签名", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 10_000_000, "RSA 签名应该在 10ms 内完成"); // 10ms
    }

    /**
     * 测试：RSA-2048 SHA256withRSA 验证性能
     */
    @Test
    @DisplayName("RSA-2048 SHA256withRSA 验证性能")
    void testRsaVerificationPerformance() throws Exception {
        List<Long> timings = new ArrayList<>();

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(TEST_DATA);
        byte[] sig = signature.sign();

        signature.initVerify(keyPair.getPublic());

        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            signature.update(TEST_DATA);
            boolean verified = signature.verify(sig);
            long end = System.nanoTime();
            timings.add(end - start);
            assertTrue(verified);
        }

        PerformanceResult result = new PerformanceResult("RSA-2048", "SHA256withRSA 验证", sumTimings(timings), timings);
        System.out.println(result);
        assertTrue(result.avgTimeMs < 1_000_000, "RSA 验证应该在 1ms 内完成"); // 1ms
    }

    /**
     * 综合性能对比测试
     */
    @Test
    @DisplayName("综合性能对比：RSA vs X25519/Ed25519")
    void testOverallPerformanceComparison() throws Exception {
        System.out.println("\n========== 密码算法性能对比测试 ==========");
        System.out.printf("%-12s %-20s | %-15s | %-15s | %-15s | %-15s | %-10s%n",
            "算法", "操作", "总计", "平均", "最小", "最大", "迭代");
        System.out.println("----------------------------------------------------------------------------");

        // 密钥生成
        PerformanceResult rsaKeyGen = testRsaKeyGenerationPerformanceImpl();
        PerformanceResult x25519KeyGen = testX25519KeyGenerationPerformanceImpl();
        PerformanceResult ed25519KeyGen = testEd25519KeyGenerationPerformanceImpl();

        // ECDH
        PerformanceResult x25519Ecdh = testX25519ECDHPerformanceImpl();

        // 签名
        PerformanceResult rsaSign = testRsaSigningPerformanceImpl();
        PerformanceResult ed25519Sign = testEd25519SigningPerformanceImpl();

        // 验证
        PerformanceResult rsaVerify = testRsaVerificationPerformanceImpl();
        PerformanceResult ed25519Verify = testEd25519VerificationPerformanceImpl();

        System.out.println("\n========== 性能提升倍数对比 ==========");
        System.out.println("X25519 vs RSA-2048 密钥生成: " + String.format("%.1fx", x25519KeyGen.getSpeedupFactor(rsaKeyGen)));
        System.out.println("Ed25519 vs RSA-2048 密钥生成: " + String.format("%.1fx", ed25519KeyGen.getSpeedupFactor(rsaKeyGen)));
        System.out.println("Ed25519 vs RSA-2048 签名: " + String.format("%.1fx", ed25519Sign.getSpeedupFactor(rsaSign)));
        System.out.println("Ed25519 vs RSA-2048 验证: " + String.format("%.1fx", ed25519Verify.getSpeedupFactor(rsaVerify)));

        System.out.println("\n========== 结论 ==========");
        System.out.println("1. X25519 密钥生成比 RSA-2048 快约 " + String.format("%.0fx", x25519KeyGen.getSpeedupFactor(rsaKeyGen)) + " 倍");
        System.out.println("2. Ed25519 密钥生成比 RSA-2048 快约 " + String.format("%.0fx", ed25519KeyGen.getSpeedupFactor(rsaKeyGen)) + " 倍");
        System.out.println("3. Ed25519 签名比 RSA-2048 快约 " + String.format("%.0fx", ed25519Sign.getSpeedupFactor(rsaSign)) + " 倍");
        System.out.println("4. Ed25519 验证比 RSA-2048 快约 " + String.format("%.0fx", ed25519Verify.getSpeedupFactor(rsaVerify)) + " 倍");
        System.out.println("5. X25519 ECDH 密钥交换平均耗时: " + String.format("%.2f ms", x25519Ecdh.avgTimeMs / 1_000_000.0));

        // 验证性能提升达到预期
        assertTrue(x25519KeyGen.getSpeedupFactor(rsaKeyGen) > 10,
            "X25519 密钥生成应该比 RSA 快 10 倍以上");
        assertTrue(ed25519KeyGen.getSpeedupFactor(rsaKeyGen) > 10,
            "Ed25519 密钥生成应该比 RSA 快 10 倍以上");
        assertTrue(ed25519Sign.getSpeedupFactor(rsaSign) > 5,
            "Ed25519 签名应该比 RSA 快 5 倍以上");
    }

    // 测试实现方法（用于综合测试）

    private PerformanceResult testRsaKeyGenerationPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            keyGen.generateKeyPair();
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("RSA-2048", "密钥生成", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    private PerformanceResult testX25519KeyGenerationPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            x25519KeyManager.generateKeyPair();
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("X25519", "密钥生成", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    private PerformanceResult testEd25519KeyGenerationPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            ed25519Signer.generateKeyPair();
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("Ed25519", "密钥生成", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    private PerformanceResult testX25519ECDHPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        KeyPair aliceKeyPair = x25519KeyManager.generateKeyPair();
        KeyPair bobKeyPair = x25519KeyManager.generateKeyPair();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            x25519KeyManager.performECDH(aliceKeyPair.getPrivate(), bobKeyPair.getPublic());
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("X25519", "ECDH 密钥交换", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    private PerformanceResult testRsaSigningPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            signature.update(TEST_DATA);
            signature.sign();
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("RSA-2048", "SHA256withRSA 签名", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    private PerformanceResult testEd25519SigningPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        KeyPair keyPair = ed25519Signer.generateKeyPair();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            ed25519Signer.sign(TEST_DATA, keyPair.getPrivate());
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("Ed25519", "签名", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    private PerformanceResult testRsaVerificationPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(TEST_DATA);
        byte[] sig = signature.sign();
        signature.initVerify(keyPair.getPublic());
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            signature.update(TEST_DATA);
            signature.verify(sig);
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("RSA-2048", "SHA256withRSA 验证", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    private PerformanceResult testEd25519VerificationPerformanceImpl() throws Exception {
        List<Long> timings = new ArrayList<>();
        KeyPair keyPair = ed25519Signer.generateKeyPair();
        byte[] sig = ed25519Signer.sign(TEST_DATA, keyPair.getPrivate());
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            long start = System.nanoTime();
            ed25519Signer.verify(TEST_DATA, sig, keyPair.getPublic());
            long end = System.nanoTime();
            timings.add(end - start);
        }
        PerformanceResult result = new PerformanceResult("Ed25519", "验证", sumTimings(timings), timings);
        System.out.println(result);
        return result;
    }

    /**
     * 求和时间序列
     */
    private long sumTimings(List<Long> timings) {
        return timings.stream().mapToLong(Long::longValue).sum();
    }

    /**
     * 测试：公钥/私钥尺寸对比
     */
    @Test
    @DisplayName("密钥尺寸对比")
    void testKeySizeComparison() throws Exception {
        System.out.println("\n========== 密钥尺寸对比 ==========");

        // RSA-2048
        KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
        rsaKeyGen.initialize(2048);
        KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();
        byte[] rsaPublicKey = rsaKeyPair.getPublic().getEncoded();
        byte[] rsaPrivateKey = rsaKeyPair.getPrivate().getEncoded();

        System.out.printf("RSA-2048 公钥尺寸: %d 字节 (%.2f KB)%n",
            rsaPublicKey.length, rsaPublicKey.length / 1024.0);
        System.out.printf("RSA-2048 私钥尺寸: %d 字节 (%.2f KB)%n",
            rsaPrivateKey.length, rsaPrivateKey.length / 1024.0);

        // X25519
        KeyPair x25519KeyPair = x25519KeyManager.generateKeyPair();
        byte[] x25519PublicKey = x25519KeyPair.getPublic().getEncoded();
        byte[] x25519PrivateKey = x25519KeyPair.getPrivate().getEncoded();

        System.out.printf("X25519 公钥尺寸: %d 字节%n", x25519PublicKey.length);
        System.out.printf("X25519 私钥尺寸: %d 字节%n", x25519PrivateKey.length);

        // Ed25519
        KeyPair ed25519KeyPair = ed25519Signer.generateKeyPair();
        byte[] ed25519PublicKey = ed25519KeyPair.getPublic().getEncoded();
        byte[] ed25519PrivateKey = ed25519KeyPair.getPrivate().getEncoded();

        System.out.printf("Ed25519 公钥尺寸: %d 字节%n", ed25519PublicKey.length);
        System.out.printf("Ed25519 私钥尺寸: %d 字节%n", ed25519PrivateKey.length);
        System.out.printf("Ed25519 签名尺寸: %d 字节%n", 64);

        System.out.println("\n========== 尺寸节省 ==========");
        System.out.printf("X25519 公钥比 RSA-2048 小 %.1fx%n", (double) rsaPublicKey.length / x25519PublicKey.length);
        System.out.printf("X25519 私钥比 RSA-2048 小 %.1fx%n", (double) rsaPrivateKey.length / x25519PrivateKey.length);

        // 验证尺寸
        assertEquals(32, x25519PublicKey.length, "X25519 公钥应该是 32 字节");
        assertEquals(32, x25519PrivateKey.length, "X25519 私钥应该是 32 字节");
        assertEquals(32, ed25519PublicKey.length, "Ed25519 公钥应该是 32 字节");
        assertEquals(32, ed25519PrivateKey.length, "Ed25519 私钥应该是 32 字节");
    }

    /**
     * 测试：签名尺寸对比
     */
    @Test
    @DisplayName("签名尺寸对比")
    void testSignatureSizeComparison() throws Exception {
        System.out.println("\n========== 签名尺寸对比 ==========");

        // RSA-2048 SHA256withRSA
        KeyPairGenerator rsaKeyGen = KeyPairGenerator.getInstance("RSA");
        rsaKeyGen.initialize(2048);
        KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();
        Signature rsaSignature = Signature.getInstance("SHA256withRSA");
        rsaSignature.initSign(rsaKeyPair.getPrivate());
        rsaSignature.update(TEST_DATA);
        byte[] rsaSig = rsaSignature.sign();

        System.out.printf("RSA-2048 SHA256withRSA 签名尺寸: %d 字节%n", rsaSig.length);

        // Ed25519
        KeyPair ed25519KeyPair = ed25519Signer.generateKeyPair();
        byte[] ed25519Sig = ed25519Signer.sign(TEST_DATA, ed25519KeyPair.getPrivate());

        System.out.printf("Ed25519 签名尺寸: %d 字节%n", ed25519Sig.length);

        System.out.println("\n========== 尺寸节省 ==========");
        System.out.printf("Ed25519 签名比 RSA-2048 小 %.1fx%n", (double) rsaSig.length / ed25519Sig.length);

        // 验证尺寸
        assertEquals(256, rsaSig.length, "RSA-2048 签名应该是 256 字节");
        assertEquals(64, ed25519Sig.length, "Ed25519 签名应该是 64 字节");
    }
}