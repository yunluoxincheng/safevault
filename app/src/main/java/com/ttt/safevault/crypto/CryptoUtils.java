package com.ttt.safevault.crypto;

import android.util.Base64;

import java.security.SecureRandom;

/**
 * 加密工具类
 *
 * 提供性能优化的加密辅助方法：
 * - 缓存 SecureRandom 实例
 * - 缓存 Base64 编码器
 * - 优化的字节数组操作
 *
 * @since SafeVault 3.6.0
 */
public final class CryptoUtils {
    private static final String TAG = "CryptoUtils";

    // 缓存的 SecureRandom 实例（线程安全）
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 缓存字节数组（用于 IV 生成）
    private static final int IV_CACHE_SIZE = 10;
    private static final byte[][] IV_CACHE = new byte[IV_CACHE_SIZE][];
    private static volatile int ivCacheIndex = 0;

    private CryptoUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取 SecureRandom 实例（线程安全）
     *
     * 使用缓存实例避免重复创建开销
     *
     * @return SecureRandom 实例
     */
    public static SecureRandom getSecureRandom() {
        return SECURE_RANDOM;
    }

    /**
     * 生成随机字节数组
     *
     * @param length 字节数组长度
     * @return 随机字节数组
     */
    public static byte[] generateRandomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * 生成 GCM IV（使用缓存的字节数组）
     *
     * 复用字节数组减少内存分配
     *
     * @param length IV 长度（通常 12 字节）
     * @return 随机 IV
     */
    public static synchronized byte[] generateIV(int length) {
        int index = (ivCacheIndex++) % IV_CACHE_SIZE;
        byte[] iv = IV_CACHE[index];
        if (iv == null || iv.length != length) {
            iv = new byte[length];
            IV_CACHE[index] = iv;
        }
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * 生成 GCM IV（标准 12 字节）
     *
     * @return 12 字节的随机 IV
     */
    public static byte[] generateGcmIV() {
        return generateIV(CryptoConstants.GCM_IV_SIZE);
    }

    /**
     * Base64 编码
     *
     * @param data 原始数据
     * @return Base64 编码字符串（无换行）
     */
    public static String base64Encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    /**
     * Base64 解码
     *
     * @param encoded Base64 编码字符串
     * @return 原始数据
     * @throws IllegalArgumentException 如果编码格式错误
     */
    public static byte[] base64Decode(String encoded) {
        return Base64.decode(encoded, Base64.NO_WRAP);
    }

    /**
     * 比较两个字节数组是否相等（常量时间）
     *
     * 用于安全比较，避免时序攻击
     *
     * @param a 字节数组 A
     * @param b 字节数组 B
     * @return 相等返回 true
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * 验证字节数组长度
     *
     * @param data 字节数组
     * @param expectedLength 期望长度
     * @param name 名称（用于错误消息）
     * @throws SecurityException 如果长度不匹配
     */
    public static void validateLength(byte[] data, int expectedLength, String name) {
        if (data == null) {
            throw new SecurityException(name + " cannot be null");
        }
        if (data.length != expectedLength) {
            throw new SecurityException(
                String.format("%s length mismatch: expected %d bytes, got %d bytes",
                    name, expectedLength, data.length)
            );
        }
    }

    /**
     * 验证字节数组长度范围
     *
     * @param data 字节数组
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @param name 名称（用于错误消息）
     * @throws SecurityException 如果长度不在范围内
     */
    public static void validateLengthRange(byte[] data, int minLength, int maxLength, String name) {
        if (data == null) {
            throw new SecurityException(name + " cannot be null");
        }
        if (data.length < minLength || data.length > maxLength) {
            throw new SecurityException(
                String.format("%s length out of range: expected %d-%d bytes, got %d bytes",
                    name, minLength, maxLength, data.length)
            );
        }
    }

    /**
     * 安全复制字节数组
     *
     * @param source 源数组
     * @return 新的副本
     */
    public static byte[] copyOf(byte[] source) {
        if (source == null) {
            return null;
        }
        return source.clone();
    }

    /**
     * 检查字节数组是否为空
     *
     * @param data 字节数组
     * @return 为空或长度为0返回 true
     */
    public static boolean isEmpty(byte[] data) {
        return data == null || data.length == 0;
    }

    /**
     * 将字节数组转换为十六进制字符串（用于调试）
     *
     * @param data 字节数组
     * @param maxLength 最大显示长度
     * @return 十六进制字符串
     */
    public static String toHexString(byte[] data, int maxLength) {
        if (data == null) {
            return "null";
        }

        int displayLength = Math.min(data.length, maxLength);
        StringBuilder sb = new StringBuilder(displayLength * 2);

        for (int i = 0; i < displayLength; i++) {
            sb.append(String.format("%02x", data[i] & 0xFF));
            if (i < displayLength - 1) {
                sb.append(" ");
            }
        }

        if (data.length > maxLength) {
            sb.append("...");
        }

        return sb.toString();
    }

    /**
     * 将字节数组转换为十六进制字符串（全部）
     *
     * @param data 字节数组
     * @return 十六进制字符串
     */
    public static String toHexString(byte[] data) {
        return toHexString(data, data.length);
    }
}