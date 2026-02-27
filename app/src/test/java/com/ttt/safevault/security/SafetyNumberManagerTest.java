package com.ttt.safevault.security;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * SafetyNumberManager 单元测试
 *
 * 测试覆盖：
 * - 指纹生成一致性
 * - 短指纹格式正确性
 * - 公钥变化检测
 * - 验证状态管理
 */
@RunWith(RobolectricTestRunner.class)
public class SafetyNumberManagerTest {

    private SafetyNumberManager safetyNumberManager;
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        safetyNumberManager = SafetyNumberManager.getInstance(context);
        prefs = context.getSharedPreferences("safety_numbers", Context.MODE_PRIVATE);

        // 清除之前的测试数据
        prefs.edit().clear().apply();
    }

    /**
     * 测试短指纹格式正确性
     */
    @Test
    public void testShortFingerprintFormat() {
        try {
            KeyPair keyPair = generateKeyPair();
            String shortFingerprint = safetyNumberManager.generateShortFingerprint(keyPair.getPublic());

            // 验证格式：XX-XX-XX-XX-XX（5组2位数字）
            assertNotNull("短指纹不应为 null", shortFingerprint);
            assertTrue("短指纹应为14字符", shortFingerprint.length() == 14);

            // 验证分隔符位置
            assertEquals("第3位应为连字符", '-', shortFingerprint.charAt(2));
            assertEquals("第6位应为连字符", '-', shortFingerprint.charAt(5));
            assertEquals("第9位应为连字符", '-', shortFingerprint.charAt(8));
            assertEquals("第12位应为连字符", '-', shortFingerprint.charAt(11));

            // 验证每组都是2位数字
            String[] parts = shortFingerprint.split("-");
            assertEquals("应有5组数字", 5, parts.length);
            for (String part : parts) {
                assertEquals("每组应为2位", 2, part.length());
                assertTrue("每组应为数字", part.matches("\\d{2}"));
            }

        } catch (Exception e) {
            fail("测试短指纹格式失败: " + e.getMessage());
        }
    }

    /**
     * 测试指纹生成一致性
     * 同一个公钥多次生成的指纹应该相同
     */
    @Test
    public void testFingerprintConsistency() {
        try {
            KeyPair keyPair = generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();

            // 多次生成短指纹
            String shortFingerprint1 = safetyNumberManager.generateShortFingerprint(publicKey);
            String shortFingerprint2 = safetyNumberManager.generateShortFingerprint(publicKey);
            String shortFingerprint3 = safetyNumberManager.generateShortFingerprint(publicKey);

            // 验证一致性
            assertEquals("短指纹应一致", shortFingerprint1, shortFingerprint2);
            assertEquals("短指纹应一致", shortFingerprint1, shortFingerprint3);

            // 多次生成长指纹
            String fullFingerprint1 = safetyNumberManager.generateFullFingerprint(publicKey);
            String fullFingerprint2 = safetyNumberManager.generateFullFingerprint(publicKey);
            String fullFingerprint3 = safetyNumberManager.generateFullFingerprint(publicKey);

            // 验证一致性
            assertEquals("长指纹应一致", fullFingerprint1, fullFingerprint2);
            assertEquals("长指纹应一致", fullFingerprint1, fullFingerprint3);

        } catch (Exception e) {
            fail("测试指纹一致性失败: " + e.getMessage());
        }
    }

    /**
     * 测试不同公钥生成不同指纹
     */
    @Test
    public void testDifferentKeysDifferentFingerprints() {
        try {
            KeyPair keyPair1 = generateKeyPair();
            KeyPair keyPair2 = generateKeyPair();

            String shortFingerprint1 = safetyNumberManager.generateShortFingerprint(keyPair1.getPublic());
            String shortFingerprint2 = safetyNumberManager.generateShortFingerprint(keyPair2.getPublic());

            // 不同公钥应产生不同的短指纹
            assertNotEquals("不同公钥应产生不同短指纹", shortFingerprint1, shortFingerprint2);

            String fullFingerprint1 = safetyNumberManager.generateFullFingerprint(keyPair1.getPublic());
            String fullFingerprint2 = safetyNumberManager.generateFullFingerprint(keyPair2.getPublic());

            // 不同公钥应产生不同的长指纹
            assertNotEquals("不同公钥应产生不同长指纹", fullFingerprint1, fullFingerprint2);

        } catch (Exception e) {
            fail("测试不同公钥指纹失败: " + e.getMessage());
        }
    }

    /**
     * 测试长指纹格式正确性
     */
    @Test
    public void testFullFingerprintFormat() {
        try {
            KeyPair keyPair = generateKeyPair();
            String fullFingerprint = safetyNumberManager.generateFullFingerprint(keyPair.getPublic());

            // 验证格式：64个十六进制字符，每8个一组
            assertNotNull("长指纹不应为 null", fullFingerprint);

            // 去除空格后应为64字符
            String withoutSpaces = fullFingerprint.replace(" ", "");
            assertEquals("长指纹应为64个十六进制字符", 64, withoutSpaces.length());

            // 验证为有效的十六进制字符串
            assertTrue("长指纹应为十六进制", withoutSpaces.matches("[0-9a-fA-F]{64}"));

        } catch (Exception e) {
            fail("测试长指纹格式失败: " + e.getMessage());
        }
    }

    /**
     * 测试验证状态管理
     */
    @Test
    public void testVerificationStatus() {
        try {
            KeyPair keyPair = generateKeyPair();
            String username = "test_verification_status_" + System.currentTimeMillis() + "@example.com";

            // 初始状态：未验证
            assertFalse("初始状态应为未验证",
                safetyNumberManager.isVerified(username, keyPair.getPublic()));

            // 标记为已验证
            safetyNumberManager.markAsVerified(username, keyPair.getPublic());

            // 验证状态：已验证
            assertTrue("标记后应为已验证",
                safetyNumberManager.isVerified(username, keyPair.getPublic()));

            // 清除验证状态
            safetyNumberManager.clearVerification(username);

            // 验证状态：未验证
            assertFalse("清除后应为未验证",
                safetyNumberManager.isVerified(username, keyPair.getPublic()));

        } catch (Exception e) {
            fail("测试验证状态管理失败: " + e.getMessage());
        }
    }

    /**
     * 测试公钥变化检测
     */
    @Test
    public void testPublicKeyChangeDetection() {
        try {
            KeyPair keyPair1 = generateKeyPair();
            KeyPair keyPair2 = generateKeyPair();
            String username = "test_key_change_" + System.currentTimeMillis() + "@example.com";

            // 初始状态：未验证，公钥未变化
            assertFalse("未验证时公钥变化检测应返回 false",
                safetyNumberManager.hasPublicKeyChanged(username, keyPair1.getPublic()));

            // 标记第一个公钥为已验证
            safetyNumberManager.markAsVerified(username, keyPair1.getPublic());

            // 使用相同的公钥：未变化
            assertFalse("相同公钥应未变化",
                safetyNumberManager.hasPublicKeyChanged(username, keyPair1.getPublic()));

            // 使用不同的公钥：检测到变化
            assertTrue("不同公钥应检测到变化",
                safetyNumberManager.hasPublicKeyChanged(username, keyPair2.getPublic()));

        } catch (Exception e) {
            fail("测试公钥变化检测失败: " + e.getMessage());
        }
    }

    /**
     * 测试获取已存储的指纹
     */
    @Test
    public void testGetStoredFingerprints() {
        try {
            KeyPair keyPair = generateKeyPair();
            String username = "test_stored_fingerprints_" + System.currentTimeMillis() + "@example.com";

            // 标记为已验证
            safetyNumberManager.markAsVerified(username, keyPair.getPublic());

            // 获取存储的短指纹
            String storedShort = safetyNumberManager.getStoredShortFingerprint(username);
            assertNotNull("存储的短指纹不应为 null", storedShort);

            // 应该与新生成的短指纹一致
            String generatedShort = safetyNumberManager.generateShortFingerprint(keyPair.getPublic());
            assertEquals("存储的短指纹应与生成的一致", generatedShort, storedShort);

            // 获取存储的长指纹
            String storedFull = safetyNumberManager.getStoredFullFingerprint(username);
            assertNotNull("存储的长指纹不应为 null", storedFull);

            // 应该与新生成的长指纹一致（忽略空格）
            String generatedFull = safetyNumberManager.generateFullFingerprint(keyPair.getPublic());
            assertEquals("存储的长指纹应与生成的一致",
                generatedFull, storedFull);

        } catch (Exception e) {
            fail("测试获取存储指纹失败: " + e.getMessage());
        }
    }

    /**
     * 测试验证时间戳
     */
    @Test
    public void testVerificationTimestamp() {
        try {
            KeyPair keyPair = generateKeyPair();
            String username = "test_timestamp_" + System.currentTimeMillis() + "@example.com";

            // 初始时间戳为0
            assertEquals("初始验证时间应为0", 0,
                safetyNumberManager.getVerificationTime(username));

            // 标记为已验证
            long beforeTime = System.currentTimeMillis();
            safetyNumberManager.markAsVerified(username, keyPair.getPublic());
            long afterTime = System.currentTimeMillis();

            // 验证时间戳在合理范围内
            long verificationTime = safetyNumberManager.getVerificationTime(username);
            assertTrue("验证时间应在标记之前",
                verificationTime >= beforeTime);
            assertTrue("验证时间应在标记之后",
                verificationTime <= afterTime);

        } catch (Exception e) {
            fail("测试验证时间戳失败: " + e.getMessage());
        }
    }

    /**
     * 测试获取所有已验证用户
     */
    @Test
    public void testGetVerifiedUsers() {
        try {
            // 使用唯一的用户名避免与其他测试冲突
            String timestamp = String.valueOf(System.currentTimeMillis());
            String username1 = "test_get_verified_users_user1_" + timestamp + "@example.com";
            String username2 = "test_get_verified_users_user2_" + timestamp + "@example.com";
            String username3 = "test_get_verified_users_user3_" + timestamp + "@example.com";

            KeyPair keyPair1 = generateKeyPair();
            KeyPair keyPair2 = generateKeyPair();
            KeyPair keyPair3 = generateKeyPair();

            // 获取初始已验证用户数（可能包含其他测试的数据）
            int initialCount = safetyNumberManager.getVerifiedUserCount();

            // 标记用户为已验证
            safetyNumberManager.markAsVerified(username1, keyPair1.getPublic());
            safetyNumberManager.markAsVerified(username2, keyPair2.getPublic());
            safetyNumberManager.markAsVerified(username3, keyPair3.getPublic());

            // 验证用户列表（应该包含新添加的用户）
            Set<String> verifiedUsers = safetyNumberManager.getVerifiedUsers();
            assertTrue("应包含用户1", verifiedUsers.contains(username1));
            assertTrue("应包含用户2", verifiedUsers.contains(username2));
            assertTrue("应包含用户3", verifiedUsers.contains(username3));

            // 已验证用户数应该至少增加了3个
            int newCount = safetyNumberManager.getVerifiedUserCount();
            assertTrue("已验证用户数应该至少增加了3个", newCount >= initialCount + 3);

            // 清除一个用户的验证状态
            safetyNumberManager.clearVerification(username2);

            // 验证用户列表更新
            verifiedUsers = safetyNumberManager.getVerifiedUsers();
            assertFalse("不应包含用户2", verifiedUsers.contains(username2));
            assertTrue("应仍包含用户1", verifiedUsers.contains(username1));
            assertTrue("应仍包含用户3", verifiedUsers.contains(username3));

        } catch (Exception e) {
            fail("测试获取已验证用户失败: " + e.getMessage());
        }
    }

    /**
     * 测试重新验证后指纹更新
     */
    @Test
    public void testReverificationUpdatesFingerprint() {
        try {
            KeyPair keyPair1 = generateKeyPair();
            KeyPair keyPair2 = generateKeyPair();
            String username = "test_reverification_" + System.currentTimeMillis() + "@example.com";

            // 标记第一个公钥为已验证
            safetyNumberManager.markAsVerified(username, keyPair1.getPublic());
            String storedFingerprint1 = safetyNumberManager.getStoredShortFingerprint(username);

            // 标记第二个公钥为已验证（模拟重新验证）
            safetyNumberManager.markAsVerified(username, keyPair2.getPublic());
            String storedFingerprint2 = safetyNumberManager.getStoredShortFingerprint(username);

            // 指纹应该已更新
            assertNotEquals("重新验证后指纹应更新", storedFingerprint1, storedFingerprint2);

            // 新指纹应该与第二个公钥匹配
            String expectedFingerprint = safetyNumberManager.generateShortFingerprint(keyPair2.getPublic());
            assertEquals("新指纹应与第二个公钥匹配", expectedFingerprint, storedFingerprint2);

        } catch (Exception e) {
            fail("测试重新验证指纹更新失败: " + e.getMessage());
        }
    }

    /**
     * 测试短指纹的唯一性分布
     * 验证短指纹在多个公钥之间有良好的分布
     */
    @Test
    public void testShortFingerprintDistribution() {
        try {
            int sampleSize = 100;
            Set<String> uniqueFingerprints = new HashSet<>();

            // 生成多个不同的公钥和指纹
            for (int i = 0; i < sampleSize; i++) {
                KeyPair keyPair = generateKeyPair();
                String fingerprint = safetyNumberManager.generateShortFingerprint(keyPair.getPublic());
                uniqueFingerprints.add(fingerprint);
            }

            // 验证指纹有良好的分布
            // 5组2位数字理论上可以有 100^5 = 100亿种组合
            // 对于100个样本，期望至少有90%是唯一的
            double uniquenessRatio = (double) uniqueFingerprints.size() / sampleSize;
            assertTrue("短指纹应有良好的唯一分布（实际唯一率: " + uniquenessRatio + ")",
                uniquenessRatio > 0.90);

        } catch (Exception e) {
            fail("测试短指纹分布失败: " + e.getMessage());
        }
    }

    /**
     * 生成 RSA 密钥对用于测试
     */
    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048, new SecureRandom());
        return keyGen.generateKeyPair();
    }
}
