package com.ttt.safevault.security;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * SensitiveData 单元测试
 *
 * 测试 SensitiveData 包装类的功能：
 * - 数据包装和获取
 * - 安全清零功能
 * - try-with-resources 行为
 * - 序列化禁止
 *
 * @since SafeVault 3.5.0 (内存安全强化)
 */
@RunWith(JUnit4.class)
public class SensitiveDataTest {

    @Test
    public void testCreateWithByteArray() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        try {
            SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

            assertNotNull("SensitiveData 不应为 null", sensitiveData);
            assertFalse("初始状态不应为关闭", sensitiveData.isClosed());
            assertEquals("长度应匹配", data.length, sensitiveData.length());
            assertFalse("不应为空", sensitiveData.isEmpty());
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testCreateWithCharArray() {
        char[] data = new char[]{'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};
        try {
            SensitiveData<char[]> sensitiveData = new SensitiveData<>(data);

            assertNotNull("SensitiveData 不应为 null", sensitiveData);
            assertFalse("初始状态不应为关闭", sensitiveData.isClosed());
            assertEquals("长度应匹配", data.length, sensitiveData.length());
            assertFalse("不应为空", sensitiveData.isEmpty());
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithNullData() {
        new SensitiveData<byte[]>(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateWithUnsupportedType() {
        new SensitiveData<String>("invalid");
    }

    @Test
    public void testGetData() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        try {
            SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

            byte[] retrieved = sensitiveData.get();
            assertNotNull("获取的数据不应为 null", retrieved);
            assertEquals("数据长度应匹配", data.length, retrieved.length);

            // 验证数据内容一致
            for (int i = 0; i < data.length; i++) {
                assertEquals("数据内容应匹配", data[i], retrieved[i]);
            }
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testGetDataAfterClose() {
        byte[] data = new byte[]{1, 2, 3, 4};
        try {
            SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

            sensitiveData.close();
            byte[] retrieved = sensitiveData.get();

            assertNull("关闭后获取数据应返回 null", retrieved);
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testCloseByteArray() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

        // 关闭前检查数据状态
        byte[] dataBeforeClose = sensitiveData.get();
        assertNotNull("关闭前数据不应为 null", dataBeforeClose);

        // 关闭
        sensitiveData.close();

        // 验证关闭状态
        assertTrue("关闭后 isClosed 应返回 true", sensitiveData.isClosed());
        assertEquals("关闭后长度应为 0", 0, sensitiveData.length());
        assertTrue("关闭后应为空", sensitiveData.isEmpty());
        assertNull("关闭后数据引用应为 null", sensitiveData.get());

        // 验证原始数组已被清零
        for (byte b : data) {
            assertEquals("原始数组应被清零", 0, b);
        }
    }

    @Test
    public void testCloseCharArray() {
        char[] data = new char[]{'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};
        SensitiveData<char[]> sensitiveData = new SensitiveData<>(data);

        // 关闭前检查数据状态
        char[] dataBeforeClose = sensitiveData.get();
        assertNotNull("关闭前数据不应为 null", dataBeforeClose);

        // 关闭
        sensitiveData.close();

        // 验证关闭状态
        assertTrue("关闭后 isClosed 应返回 true", sensitiveData.isClosed());
        assertEquals("关闭后长度应为 0", 0, sensitiveData.length());
        assertTrue("关闭后应为空", sensitiveData.isEmpty());
        assertNull("关闭后数据引用应为 null", sensitiveData.get());

        // 验证原始数组已被清零
        for (char c : data) {
            assertEquals("原始数组应被清零", '\0', c);
        }
    }

    @Test
    public void testCloseIsIdempotent() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

        // 第一次关闭
        sensitiveData.close();
        assertTrue("第一次关闭后应为已关闭状态", sensitiveData.isClosed());

        // 第二次关闭（不应抛出异常）
        sensitiveData.close();
        assertTrue("第二次关闭后仍应为已关闭状态", sensitiveData.isClosed());
    }

    @Test
    public void testTryWithResources() {
        byte[] originalData = new byte[]{10, 20, 30, 40, 50};

        // 使用 try-with-resources 自动关闭
        try (SensitiveData<byte[]> sensitiveData = new SensitiveData<>(originalData)) {
            assertNotNull("在 try 块中数据应可用", sensitiveData.get());
            assertFalse("在 try 块中不应为已关闭状态", sensitiveData.isClosed());
        }

        // try-with-resources 块结束后应自动关闭
        // 由于 SensitiveData 直接包装原始数组引用，关闭后原始数组会被清零
        for (byte b : originalData) {
            assertEquals("自动关闭后原始数组应被清零", 0, b);
        }
    }

    @Test
    public void testCopyBytes() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        try {
            SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

            byte[] copy = sensitiveData.copyBytes();

            assertNotNull("副本不应为 null", copy);
            assertEquals("副本长度应匹配", data.length, copy.length);

            // 验证内容一致
            for (int i = 0; i < data.length; i++) {
                assertEquals("副本内容应匹配", data[i], copy[i]);
            }

            // 验证是深拷贝（不是同一引用）
            assertNotSame("副本应是独立数组", data, copy);
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testCopyBytesAfterClose() {
        byte[] data = new byte[]{1, 2, 3, 4};
        SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

        sensitiveData.close();
        byte[] copy = sensitiveData.copyBytes();

        assertNull("关闭后复制应返回 null", copy);
    }

    @Test
    public void testCopyChars() {
        char[] data = new char[]{'p', 'a', 's', 's', 'w', 'o', 'r', 'd'};
        try {
            SensitiveData<char[]> sensitiveData = new SensitiveData<>(data);

            char[] copy = sensitiveData.copyChars();

            assertNotNull("副本不应为 null", copy);
            assertEquals("副本长度应匹配", data.length, copy.length);

            // 验证内容一致
            for (int i = 0; i < data.length; i++) {
                assertEquals("副本内容应匹配", data[i], copy[i]);
            }

            // 验证是深拷贝（不是同一引用）
            assertNotSame("副本应是独立数组", data, copy);
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testCopyCharsAfterClose() {
        char[] data = new char[]{'t', 'e', 's', 't'};
        SensitiveData<char[]> sensitiveData = new SensitiveData<>(data);

        sensitiveData.close();
        char[] copy = sensitiveData.copyChars();

        assertNull("关闭后复制应返回 null", copy);
    }

    @Test
    public void testCopyBytesReturnsNullForCharArray() {
        char[] data = new char[]{'t', 'e', 's', 't'};
        try {
            SensitiveData<char[]> sensitiveData = new SensitiveData<>(data);

            byte[] copy = sensitiveData.copyBytes();
            assertNull("字符数组类型应返回 null", copy);
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testCopyCharsReturnsNullForByteArray() {
        byte[] data = new byte[]{1, 2, 3, 4};
        try {
            SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

            char[] copy = sensitiveData.copyChars();
            assertNull("字节数组类型应返回 null", copy);
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testToString() {
        byte[] data = new byte[]{1, 2, 3, 4};
        try {
            SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

            String str = sensitiveData.toString();

            assertNotNull("toString 不应返回 null", str);
            assertTrue("toString 应包含类型信息", str.contains("byte[]"));
            assertTrue("toString 应包含长度信息", str.contains("length="));
            assertTrue("toString 应包含关闭状态", str.contains("closed="));
            assertFalse("toString 不应包含敏感数据", str.contains("1, 2, 3"));
        } finally {
            MemorySanitizer.secureWipe(data);
        }
    }

    @Test
    public void testIsEmpty() {
        byte[] data1 = new byte[]{1, 2, 3, 4, 5};
        byte[] data2 = new byte[0];

        try {
            // 测试有数据的情况
            SensitiveData<byte[]> hasData = new SensitiveData<>(data1);
            assertFalse("有数据时 isEmpty 应返回 false", hasData.isEmpty());

            // 测试空数组的情况
            SensitiveData<byte[]> emptyData = new SensitiveData<>(data2);
            assertTrue("空数组时 isEmpty 应返回 true", emptyData.isEmpty());

            // 测试关闭后的情况
            hasData.close();
            assertTrue("关闭后 isEmpty 应返回 true", hasData.isEmpty());
        } finally {
            MemorySanitizer.secureWipe(data1);
            MemorySanitizer.secureWipe(data2);
        }
    }

    @Test
    public void testLength() {
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        SensitiveData<byte[]> sensitiveData = new SensitiveData<>(data);

        assertEquals("长度应匹配", data.length, sensitiveData.length());

        sensitiveData.close();
        assertEquals("关闭后长度应为 0", 0, sensitiveData.length());
    }
}
