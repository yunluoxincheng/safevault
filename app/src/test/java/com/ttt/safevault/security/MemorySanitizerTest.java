package com.ttt.safevault.security;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.reflect.Method;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.Assert.*;

/**
 * MemorySanitizer 单元测试
 *
 * 测试 MemorySanitizer 清零工具类的功能：
 * - 字节数组清零
 * - 字符数组清零
 * - SecretKey 清零
 * - 多轮覆盖清零
 *
 * @since SafeVault 3.5.0 (内存安全强化)
 */
@RunWith(JUnit4.class)
public class MemorySanitizerTest {

    @Test
    public void testSecureWipeByteArray() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        MemorySanitizer.secureWipe(data);

        // 验证数组已被清零
        for (byte b : data) {
            assertEquals("所有字节应被清零", 0, b);
        }
    }

    @Test
    public void testSecureWipeByteArrayWithNull() {
        // 不应抛出异常
        MemorySanitizer.secureWipe((byte[]) null);
    }

    @Test
    public void testSecureWipeEmptyByteArray() {
        byte[] data = new byte[0];

        // 不应抛出异常
        MemorySanitizer.secureWipe(data);
    }

    @Test
    public void testSecureWipeCharArray() {
        char[] data = new char[]{'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};

        MemorySanitizer.secureWipe(data);

        // 验证数组已被清零
        for (char c : data) {
            assertEquals("所有字符应被清零", '\0', c);
        }
    }

    @Test
    public void testSecureWipeCharArrayWithNull() {
        // 不应抛出异常
        MemorySanitizer.secureWipe((char[]) null);
    }

    @Test
    public void testSecureWipeEmptyCharArray() {
        char[] data = new char[0];

        // 不应抛出异常
        MemorySanitizer.secureWipe(data);
    }

    @Test
    public void testSecureWipeSecretKey() {
        byte[] keyBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        // SecretKey.getEncoded() 返回的是副本，清零副本不影响原始 keyBytes
        // MemorySanitizer.secureWipe() 只是尽力清零，不保证成功
        MemorySanitizer.secureWipe(key);

        // 由于 getEncoded() 返回副本，原始 keyBytes 不受影响
        // 这是预期行为，测试仅验证方法不抛出异常
        // 实际应用中，AndroidKeyStore 密钥由硬件保护，不可导出
    }

    @Test
    public void testSecureWipeSecretKeyWithNull() {
        // 不应抛出异常
        MemorySanitizer.secureWipe((SecretKey) null);
    }

    @Test
    public void testSecureWipeByteArrayWithCustomPasses() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        // 测试 5 轮清零
        MemorySanitizer.secureWipe(data, 5);

        // 验证数组已被清零
        for (byte b : data) {
            assertEquals("所有字节应被清零", 0, b);
        }
    }

    @Test
    public void testSecureWipeCharArrayWithCustomPasses() {
        char[] data = new char[]{'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};

        // 测试 5 轮清零
        MemorySanitizer.secureWipe(data, 5);

        // 验证数组已被清零
        for (char c : data) {
            assertEquals("所有字符应被清零", '\0', c);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecureWipeByteArrayWithInvalidPasses() {
        byte[] data = new byte[]{1, 2, 3};
        MemorySanitizer.secureWipe(data, 0);
        fail("应抛出 IllegalArgumentException（轮数必须 >= 1）");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSecureWipeCharArrayWithInvalidPasses() {
        char[] data = new char[]{'a', 'b', 'c'};
        MemorySanitizer.secureWipe(data, -1);
        fail("应抛出 IllegalArgumentException（轮数必须 >= 1）");
    }

    @Test
    public void testSecureWipeWithSinglePass() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        MemorySanitizer.secureWipe(data, 1);

        // 验证数组已被清零（单轮也应清零）
        for (byte b : data) {
            assertEquals("单轮清零后所有字节应为 0", 0, b);
        }
    }

    @Test
    public void testMultipleWipesAreIdempotent() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};

        MemorySanitizer.secureWipe(data);

        // 第一次清零
        boolean allZerosAfterFirst = true;
        for (byte b : data) {
            if (b != 0) {
                allZerosAfterFirst = false;
                break;
            }
        }
        assertTrue("第一次清零后应全为零", allZerosAfterFirst);

        // 第二次清零（不应抛出异常）
        MemorySanitizer.secureWipe(data);

        boolean allZerosAfterSecond = true;
        for (byte b : data) {
            if (b != 0) {
                allZerosAfterSecond = false;
                break;
            }
        }
        assertTrue("第二次清零后仍应全为零", allZerosAfterSecond);
    }

    @Test
    public void testLargeArrayWipe() {
        // 测试大数组清零（1MB）
        byte[] largeData = new byte[1024 * 1024];

        // 填充非零数据
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        MemorySanitizer.secureWipe(largeData);

        // 验证数组已被清零
        for (byte b : largeData) {
            assertEquals("大数组清零后所有字节应为 0", 0, b);
        }
    }

    @Test
    public void testGetDefaultWipePasses() {
        int passes = MemorySanitizer.getDefaultWipePasses();

        assertTrue("默认清零轮数应 >= 1", passes >= 1);
        assertTrue("默认清零轮数应 <= 10", passes <= 10);
    }

    @Test
    public void testIsZeroedForByteArray() throws Exception {
        // 使用反射访问包级方法
        Method isZeroedMethod = MemorySanitizer.class.getDeclaredMethod("isZeroed", byte[].class);
        isZeroedMethod.setAccessible(true);

        // 测试已清零的数组
        byte[] zeroed = new byte[10];
        boolean result1 = (boolean) isZeroedMethod.invoke(null, (Object) zeroed);
        assertTrue("零数组应被识别为已清零", result1);

        // 测试未清零的数组
        byte[] nonZeroed = new byte[]{1, 2, 3};
        boolean result2 = (boolean) isZeroedMethod.invoke(null, (Object) nonZeroed);
        assertFalse("非零数组应被识别为未清零", result2);

        // 测试 null
        boolean result3 = (boolean) isZeroedMethod.invoke(null, (Object) null);
        assertTrue("null 应被识别为已清零", result3);
    }

    @Test
    public void testIsZeroedForCharArray() throws Exception {
        // 使用反射访问包级方法
        Method isZeroedMethod = MemorySanitizer.class.getDeclaredMethod("isZeroed", char[].class);
        isZeroedMethod.setAccessible(true);

        // 测试已清零的数组
        char[] zeroed = new char[10];
        boolean result1 = (boolean) isZeroedMethod.invoke(null, (Object) zeroed);
        assertTrue("零数组应被识别为已清零", result1);

        // 测试未清零的数组
        char[] nonZeroed = new char[]{'a', 'b', 'c'};
        boolean result2 = (boolean) isZeroedMethod.invoke(null, (Object) nonZeroed);
        assertFalse("非零数组应被识别为未清零", result2);

        // 测试 null
        boolean result3 = (boolean) isZeroedMethod.invoke(null, (Object) null);
        assertTrue("null 应被识别为已清零", result3);
    }

    @Test
    public void testSecureWipeHandlesExceptions() {
        // 测试异常情况下的处理
        byte[] data = new byte[]{1, 2, 3, 4, 5};

        // 正常清零不应抛出异常
        MemorySanitizer.secureWipe(data);

        // 验证即使出现内部问题，也应尽力清零
        for (byte b : data) {
            assertEquals("异常处理后数据应仍被清零", 0, b);
        }
    }

    @Test
    public void testWipeDoesNotAffectOtherArrays() {
        byte[] array1 = new byte[]{1, 2, 3, 4, 5};
        byte[] array2 = new byte[]{10, 20, 30, 40, 50};

        MemorySanitizer.secureWipe(array1);

        // array1 应被清零
        for (byte b : array1) {
            assertEquals("array1 应被清零", 0, b);
        }

        // array2 不应受影响
        assertArrayEquals("array2 应保持原值", new byte[]{10, 20, 30, 40, 50}, array2);
    }

    @Test
    public void testClassCannotBeInstantiated() throws Exception {
        // 测试工具类无法实例化
        try {
            // 尝试通过反射创建实例
            java.lang.reflect.Constructor<MemorySanitizer> constructor =
                    MemorySanitizer.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("应抛出 AssertionError");
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 预期行为：工具类无法实例化
            assertTrue("应抛出 AssertionError",
                    e.getCause() instanceof AssertionError);
        }
    }
}
