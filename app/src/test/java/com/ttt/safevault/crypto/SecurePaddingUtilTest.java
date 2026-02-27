package com.ttt.safevault.crypto;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SecurePaddingUtil 单元测试
 *
 * 测试覆盖：
 * - pad/unpad 对称性
 * - 边界条件（空数据、正好块大小）
 * - 填充长度验证
 * - 随机填充不可预测性
 * - 密文长度固定性
 */
@RunWith(RobolectricTestRunner.class)
public class SecurePaddingUtilTest {

    /**
     * 测试 pad/unpad 对称性 - 基本用例
     */
    @Test
    public void testPadUnpadSymmetry_Basic() {
        String original = "Hello, World!";
        byte[] originalBytes = original.getBytes(StandardCharsets.UTF_8);

        byte[] padded = SecurePaddingUtil.pad(originalBytes);
        byte[] unpadded = SecurePaddingUtil.unpad(padded);

        assertArrayEquals("unpad 应该恢复原始数据", originalBytes, unpadded);
    }

    /**
     * 测试 pad/unpad 对称性 - 不同长度的输入
     */
    @Test
    public void testPadUnpadSymmetry_VariousLengths() {
        // 测试不同长度的输入
        String[] testCases = {
            "", // 空字符串
            "a", // 1 字节
            "ab", // 2 字节
            "password", // 8 字节
            "This is a longer test string with more content", // 47 字节
            generateString(100), // 100 字节
            generateString(200), // 200 字节
            generateString(255), // 255 字节（接近块大小）
            generateString(256), // 256 字节（正好块大小）
            generateString(257), // 257 字节（超过块大小）
            generateString(500), // 500 字节（两个块）
        };

        for (String testCase : testCases) {
            byte[] originalBytes = testCase.getBytes(StandardCharsets.UTF_8);
            byte[] padded = SecurePaddingUtil.pad(originalBytes);
            byte[] unpadded = SecurePaddingUtil.unpad(padded);

            assertArrayEquals("unpad 应该恢复原始数据 (length=" + originalBytes.length + ")",
                    originalBytes, unpadded);
        }
    }

    /**
     * 测试 padString/unpadToString 对称性
     */
    @Test
    public void testPadStringUnpadToStringSymmetry() {
        String original = "Test string for padString/unpadToString";

        byte[] padded = SecurePaddingUtil.padString(original);
        String recovered = SecurePaddingUtil.unpadToString(padded);

        assertEquals("unpadToString 应该恢复原始字符串", original, recovered);
    }

    /**
     * 测试填充长度验证
     */
    @Test
    public void testPaddingLength() {
        // 测试 1 字节数据（应该填充到 256 字节）
        byte[] data1 = new byte[1];
        byte[] padded1 = SecurePaddingUtil.pad(data1);
        assertEquals("1 字节应填充到 256 字节", 256, padded1.length);

        // 测试 255 字节数据（应该填充到 256 字节）
        byte[] data255 = new byte[255];
        byte[] padded255 = SecurePaddingUtil.pad(data255);
        assertEquals("255 字节应填充到 256 字节", 256, padded255.length);

        // 测试 256 字节数据（应该填充到 512 字节）
        byte[] data256 = new byte[256];
        byte[] padded256 = SecurePaddingUtil.pad(data256);
        assertEquals("256 字节应填充到 512 字节", 512, padded256.length);

        // 测试 257 字节数据（应该填充到 512 字节）
        byte[] data257 = new byte[257];
        byte[] padded257 = SecurePaddingUtil.pad(data257);
        assertEquals("257 字节应填充到 512 字节", 512, padded257.length);
    }

    /**
     * 测试边界条件 - 空数组
     */
    @Test
    public void testBoundary_EmptyArray() {
        byte[] empty = new byte[0];
        byte[] padded = SecurePaddingUtil.pad(empty);
        byte[] unpadded = SecurePaddingUtil.unpad(padded);

        assertArrayEquals("空数组应正确处理", empty, unpadded);
        assertEquals("空数组应填充到 256 字节", 256, padded.length);
    }

    /**
     * 测试边界条件 - 正好块大小
     */
    @Test
    public void testBoundary_ExactlyBlockSize() {
        byte[] data256 = new byte[256];
        Arrays.fill(data256, (byte) 0x42);

        byte[] padded = SecurePaddingUtil.pad(data256);
        byte[] unpadded = SecurePaddingUtil.unpad(padded);

        assertArrayEquals("正好块大小的数据应正确处理", data256, unpadded);
        assertEquals("256 字节应填充到 512 字节", 512, padded.length);
    }

    /**
     * 测试边界条件 - 接近块大小
     */
    @Test
    public void testBoundary_NearBlockSize() {
        byte[] data255 = new byte[255];
        Arrays.fill(data255, (byte) 0x42);

        byte[] padded = SecurePaddingUtil.pad(data255);
        byte[] unpadded = SecurePaddingUtil.unpad(padded);

        assertArrayEquals("接近块大小的数据应正确处理", data255, unpadded);
        assertEquals("255 字节应填充到 256 字节", 256, padded.length);
    }

    /**
     * 测试异常情况 - null 输入
     */
    @Test(expected = IllegalArgumentException.class)
    public void testException_NullInput_Pad() {
        SecurePaddingUtil.pad(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testException_NullInput_Unpad() {
        SecurePaddingUtil.unpad(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testException_NullInput_PadString() {
        SecurePaddingUtil.padString(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testException_NullInput_UnpadToString() {
        SecurePaddingUtil.unpadToString(null);
    }

    /**
     * 测试异常情况 - 空数组 unpad
     */
    @Test(expected = IllegalArgumentException.class)
    public void testException_EmptyArray_Unpad() {
        SecurePaddingUtil.unpad(new byte[0]);
    }

    /**
     * 测试安全特性 - 随机填充不可预测性
     *
     * 验证同一个数据多次填充会产生不同的结果
     */
    @Test
    public void testSecurity_RandomPaddingUnpredictability() {
        byte[] data = "Test data".getBytes(StandardCharsets.UTF_8);

        // 多次填充同一数据
        byte[] padded1 = SecurePaddingUtil.pad(data);
        byte[] padded2 = SecurePaddingUtil.pad(data);
        byte[] padded3 = SecurePaddingUtil.pad(data);

        // 验证填充结果不同（因为填充是随机的）
        assertFalse("多次填充应产生不同结果", Arrays.equals(padded1, padded2));
        assertFalse("多次填充应产生不同结果", Arrays.equals(padded1, padded3));
        assertFalse("多次填充应产生不同结果", Arrays.equals(padded2, padded3));

        // 但 unpad 后应得到相同结果
        assertArrayEquals("unpad 后应恢复原始数据", data, SecurePaddingUtil.unpad(padded1));
        assertArrayEquals("unpad 后应恢复原始数据", data, SecurePaddingUtil.unpad(padded2));
        assertArrayEquals("unpad 后应恢复原始数据", data, SecurePaddingUtil.unpad(padded3));
    }

    /**
     * 测试安全特性 - 密文长度固定性
     *
     * 验证不同长度的数据（在同一块范围内）填充后长度相同
     */
    @Test
    public void testSecurity_FixedCiphertextLength() {
        // 测试 1-255 字节的数据，填充后都应该是 256 字节
        int[] testLengths = {1, 10, 50, 100, 200, 255};

        for (int length : testLengths) {
            byte[] data = new byte[length];
            byte[] padded = SecurePaddingUtil.pad(data);
            assertEquals("1-255 字节的数据应填充到 256 字节 (actual=" + length + ")",
                    256, padded.length);
        }

        // 测试 256-511 字节的数据，填充后都应该是 512 字节
        int[] testLengths2 = {256, 300, 400, 500, 511};

        for (int length : testLengths2) {
            byte[] data = new byte[length];
            byte[] padded = SecurePaddingUtil.pad(data);
            assertEquals("256-511 字节的数据应填充到 512 字节 (actual=" + length + ")",
                    512, padded.length);
        }
    }

    /**
     * 测试安全特性 - 填充字节不是 \0
     *
     * 验证填充字节是随机的，不是全零
     */
    @Test
    public void testSecurity_PaddingIsNotZero() {
        byte[] data = "Test".getBytes(StandardCharsets.UTF_8);
        byte[] padded = SecurePaddingUtil.pad(data);

        // 检查填充部分（除了最后一个字节）不全为零
        int dataLength = data.length;
        boolean hasNonZero = false;

        for (int i = dataLength; i < padded.length - 1; i++) {
            if (padded[i] != 0) {
                hasNonZero = true;
                break;
            }
        }

        assertTrue("填充字节不应全为零（应该是随机的）", hasNonZero);
    }

    /**
     * 测试安全特性 - 填充熵
     *
     * 验证多次填充的填充部分具有足够的熵（不重复）
     */
    @Test
    public void testSecurity_PaddingEntropy() {
        byte[] data = "Test".getBytes(StandardCharsets.UTF_8);
        int iterations = 100;

        // 收集多次填充的结果
        Set<String> uniquePaddings = new HashSet<>();

        for (int i = 0; i < iterations; i++) {
            byte[] padded = SecurePaddingUtil.pad(data);
            // 只比较填充部分（不包括原始数据和填充长度字节）
            int paddingStart = data.length;
            int paddingEnd = padded.length - 1; // 不包括最后一个字节（填充长度）

            if (paddingEnd > paddingStart) {
                byte[] paddingOnly = Arrays.copyOfRange(padded, paddingStart, paddingEnd);
                uniquePaddings.add(Arrays.toString(paddingOnly));
            }
        }

        // 验证大部分填充都是唯一的（至少 95% 不重复）
        double uniquenessRatio = (double) uniquePaddings.size() / iterations;
        assertTrue("填充应具有足够的熵（实际唯一率: " + uniquenessRatio + ")",
                uniquenessRatio > 0.95);
    }

    /**
     * 测试 getPaddedLength 方法
     */
    @Test
    public void testGetPaddedLength() {
        assertEquals("0 字节应填充到 256", 256, SecurePaddingUtil.getPaddedLength(0));
        assertEquals("1 字节应填充到 256", 256, SecurePaddingUtil.getPaddedLength(1));
        assertEquals("255 字节应填充到 256", 256, SecurePaddingUtil.getPaddedLength(255));
        assertEquals("256 字节应填充到 512", 512, SecurePaddingUtil.getPaddedLength(256));
        assertEquals("257 字节应填充到 512", 512, SecurePaddingUtil.getPaddedLength(257));
        assertEquals("512 字节应填充到 768", 768, SecurePaddingUtil.getPaddedLength(512));
    }

    /**
     * 测试 getPaddedLength 异常情况
     */
    @Test(expected = IllegalArgumentException.class)
    public void testGetPaddedLength_Negative() {
        SecurePaddingUtil.getPaddedLength(-1);
    }

    /**
     * 测试 isPadded 方法
     */
    @Test
    public void testIsPadded() {
        // 未填充的数据
        byte[] unpadded = "Test data".getBytes(StandardCharsets.UTF_8);
        assertFalse("未填充的数据应返回 false", SecurePaddingUtil.isPadded(unpadded));

        // 已填充的数据
        byte[] padded = SecurePaddingUtil.pad(unpadded);
        assertTrue("已填充的数据应返回 true", SecurePaddingUtil.isPadded(padded));

        // 空数组
        assertFalse("空数组应返回 false", SecurePaddingUtil.isPadded(new byte[0]));
    }

    /**
     * 测试 getBlockSize 方法
     */
    @Test
    public void testGetBlockSize() {
        assertEquals("块大小应为 256 字节", 256, SecurePaddingUtil.getBlockSize());
    }

    /**
     * 生成指定长度的测试字符串
     */
    private String generateString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        return sb.toString();
    }
}
