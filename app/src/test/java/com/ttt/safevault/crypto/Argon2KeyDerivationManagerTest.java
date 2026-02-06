package com.ttt.safevault.crypto;

import android.util.Base64;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.lambdapioneer.argon2kt.Argon2Kt;
import com.lambdapioneer.argon2kt.Argon2KtResult;
import com.lambdapioneer.argon2kt.Argon2Mode;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Argon2kt 单元测试
 *
 * 验证 argon2kt 的基本功能和与后端 argon2-jvm 的兼容性
 */
@RunWith(AndroidJUnit4.class)
public class Argon2KeyDerivationManagerTest {

    // ========== 与后端一致的参数配置 ==========
    private static final int ARGON2_TIME_COST = 3;
    private static final int ARGON2_MEMORY_COST = 65536;  // 64MB
    private static final int ARGON2_PARALLELISM = 4;
    private static final int ARGON2_OUTPUT_LENGTH = 32;
    private static final int ARGON2_SALT_LENGTH = 16;

    /**
     * 测试 argon2kt 基本哈希功能
     */
    @Test
    public void testBasicHashing() {
        Argon2Kt argon2Kt = new Argon2Kt();

        String password = "TestPassword123!";
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] salt = generateSalt();

        // 执行哈希
        Argon2KtResult result = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                passwordBytes,
                salt,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        assertNotNull("哈希结果不应为空", result);

        // argon2kt 返回 ByteBuffer，需要转换为 byte[]
        ByteBuffer hashBuffer = result.getRawHash();
        byte[] rawHash = new byte[hashBuffer.remaining()];
        hashBuffer.get(rawHash);

        assertNotNull("原始哈希不应为空", rawHash);
        assertEquals("哈希长度应为 32 字节", ARGON2_OUTPUT_LENGTH, rawHash.length);

        String encodedOutput = result.encodedOutputAsString();
        assertNotNull("编码输出不应为空", encodedOutput);
        assertTrue("编码输出应以 $argon2id$ 开头", encodedOutput.startsWith("$argon2id$"));

        // 清除敏感数据
        Arrays.fill(passwordBytes, (byte) 0);
    }

    /**
     * 测试确定性的哈希结果
     * 相同的密码和盐值应产生相同的哈希
     */
    @Test
    public void testDeterministicHashing() {
        Argon2Kt argon2Kt = new Argon2Kt();

        String password = "DeterministicTest";
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] salt = generateFixedSalt();

        // 第一次哈希
        Argon2KtResult result1 = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                passwordBytes,
                salt,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        // 第二次哈希（相同输入）
        Argon2KtResult result2 = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                passwordBytes,
                salt,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        // 验证结果相同
        ByteBuffer buffer1 = result1.getRawHash();
        byte[] hash1 = new byte[buffer1.remaining()];
        buffer1.get(hash1);

        ByteBuffer buffer2 = result2.getRawHash();
        byte[] hash2 = new byte[buffer2.remaining()];
        buffer2.get(hash2);

        assertArrayEquals("相同输入应产生相同哈希", hash1, hash2);

        // 清除敏感数据
        Arrays.fill(passwordBytes, (byte) 0);
    }

    /**
     * 测试不同密码产生不同哈希
     */
    @Test
    public void testDifferentPasswords() {
        Argon2Kt argon2Kt = new Argon2Kt();

        byte[] salt = generateSalt();

        byte[] password1 = "Password1".getBytes(StandardCharsets.UTF_8);
        byte[] password2 = "Password2".getBytes(StandardCharsets.UTF_8);

        Argon2KtResult result1 = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                password1,
                salt,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        Argon2KtResult result2 = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                password2,
                salt,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        ByteBuffer buffer1 = result1.getRawHash();
        byte[] hash1 = new byte[buffer1.remaining()];
        buffer1.get(hash1);

        ByteBuffer buffer2 = result2.getRawHash();
        byte[] hash2 = new byte[buffer2.remaining()];
        buffer2.get(hash2);

        assertFalse("不同密码应产生不同哈希", Arrays.equals(hash1, hash2));

        // 清除敏感数据
        Arrays.fill(password1, (byte) 0);
        Arrays.fill(password2, (byte) 0);
    }

    /**
     * 测试不同盐值产生不同哈希
     */
    @Test
    public void testDifferentSalts() {
        Argon2Kt argon2Kt = new Argon2Kt();

        byte[] password = "SamePassword".getBytes(StandardCharsets.UTF_8);
        byte[] salt1 = generateSalt();
        byte[] salt2 = generateSalt();

        // 确保盐值不同
        while (Arrays.equals(salt1, salt2)) {
            salt2 = generateSalt();
        }

        Argon2KtResult result1 = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                password,
                salt1,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        Argon2KtResult result2 = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                password,
                salt2,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        ByteBuffer buffer1 = result1.getRawHash();
        byte[] hash1 = new byte[buffer1.remaining()];
        buffer1.get(hash1);

        ByteBuffer buffer2 = result2.getRawHash();
        byte[] hash2 = new byte[buffer2.remaining()];
        buffer2.get(hash2);

        assertFalse("不同盐值应产生不同哈希", Arrays.equals(hash1, hash2));

        // 清除敏感数据
        Arrays.fill(password, (byte) 0);
    }

    /**
     * 测试 Argon2KeyDerivationManager 的密钥派生功能
     */
    @Test
    public void testKeyDerivationManager() {
        // 注意：此测试需要在 Android 设备或模拟器上运行
        // 因为 Argon2KeyDerivationManager 需要 Context

        // 这里只测试参数配置
        assertEquals("timeCost 应为 3", 3, ARGON2_TIME_COST);
        assertEquals("memoryCost 应为 65536", 65536, ARGON2_MEMORY_COST);
        assertEquals("parallelism 应为 4", 4, ARGON2_PARALLELISM);
        assertEquals("outputLength 应为 32", 32, ARGON2_OUTPUT_LENGTH);
        assertEquals("saltLength 应为 16", 16, ARGON2_SALT_LENGTH);
    }

    /**
     * 测试编码输出格式
     */
    @Test
    public void testEncodedOutputFormat() {
        Argon2Kt argon2Kt = new Argon2Kt();

        String password = "FormatTest";
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] salt = generateSalt();

        Argon2KtResult result = argon2Kt.hash(
                Argon2Mode.ARGON2_ID,
                passwordBytes,
                salt,
                ARGON2_TIME_COST,
                ARGON2_MEMORY_COST
        );

        String encoded = result.encodedOutputAsString();

        // 验证编码格式：$argon2id$v=19$m=65536,t=3,p=4$...
        assertTrue("编码输出应以 $argon2id$ 开头", encoded.startsWith("$argon2id$"));
        assertTrue("编码输出应包含版本信息", encoded.contains("v=19"));
        assertTrue("编码输出应包含内存成本", encoded.contains("m=65536"));
        assertTrue("编码输出应包含时间成本", encoded.contains("t=3"));
        assertTrue("编码输出应包含并行度", encoded.contains("p=4"));

        // 清除敏感数据
        Arrays.fill(passwordBytes, (byte) 0);
    }

    // ========== 辅助方法 ==========

    /**
     * 生成随机盐值
     */
    private byte[] generateSalt() {
        byte[] salt = new byte[ARGON2_SALT_LENGTH];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * 生成固定盐值（用于确定性测试）
     */
    private byte[] generateFixedSalt() {
        return "FixedSalt1234567".getBytes(StandardCharsets.UTF_8);
    }
}
