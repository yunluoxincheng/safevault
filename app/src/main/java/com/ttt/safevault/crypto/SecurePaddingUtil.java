package com.ttt.safevault.crypto;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 安全随机填充工具类
 * 用于防止通过密文长度推断明文长度的攻击
 *
 * 设计原则：
 * - 作用在 byte[] 而非 String（AES-GCM 加密的是字节）
 * - 使用随机填充而非 \0（防止模式分析）
 * - 块大小为 256 字节（大多数密码字段 < 256 字节）
 * - 在最后一个字节记录填充长度（支持解密时去除填充）
 *
 * @since SafeVault 3.6.0
 */
public class SecurePaddingUtil {
    private static final String TAG = "SecurePaddingUtil";

    /**
     * 块大小：256 字节
     * - 大多数密码字段 < 256 字节
     * - 一次填充即可隐藏长度
     * - 数据库增长可控（每个字段最多 +256 字节）
     */
    public static final int BLOCK_SIZE = 256;

    /**
     * 单例 SecureRandom（线程安全）
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 私有构造函数（工具类）
     */
    private SecurePaddingUtil() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * 填充字节数组到下一个块边界
     *
     * 算法：
     * 1. 计算填充长度 = BLOCK_SIZE - (plaintext.length % BLOCK_SIZE)
     * 2. 创建目标数组（plaintext.length + paddingLength）
     * 3. 复制原始数据
     * 4. 用随机字节填充剩余空间（关键：不是 \0）
     * 5. 在最后一个字节写入填充长度（用于验证和去填充）
     *
     * @param plaintext 原始明文字节
     * @return 填充后的字节数组
     * @throws IllegalArgumentException 如果 plaintext 为 null
     */
    @NonNull
    public static byte[] pad(@NonNull byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext cannot be null");
        }

        // 计算填充长度（至少填充 1 字节用于存储 paddingLength）
        int paddingLength = BLOCK_SIZE - (plaintext.length % BLOCK_SIZE);
        if (paddingLength == 0) {
            // 如果正好是块大小的倍数，仍需填充一个完整的块
            paddingLength = BLOCK_SIZE;
        }

        int targetLength = plaintext.length + paddingLength;

        byte[] padded = new byte[targetLength];

        // 1. 复制原始数据
        System.arraycopy(plaintext, 0, padded, 0, plaintext.length);

        // 2. 填充随机字节（关键：不是 \0）
        // 从 plaintext.length 开始填充，但保留最后一个字节用于存储 paddingLength
        // 注意：使用循环而非 nextBytes(byte[], int, int) 以兼容 Android 10+
        byte[] randomBytes = new byte[paddingLength - 1];
        RANDOM.nextBytes(randomBytes);
        System.arraycopy(randomBytes, 0, padded, plaintext.length, paddingLength - 1);

        // 3. 在最后一个字节写入填充长度
        padded[targetLength - 1] = (byte) paddingLength;

        Log.d(TAG, "padded: " + plaintext.length + " -> " + targetLength + " bytes (+"
                + paddingLength + " padding)");
        return padded;
    }

    /**
     * 去除填充
     *
     * 算法：
     * 1. 读取最后一个字节获取填充长度
     * 2. 验证填充长度在合理范围内 (1, BLOCK_SIZE]
     * 3. 返回去除填充后的原始数据
     *
     * 注意：当填充长度为 BLOCK_SIZE (256) 时，存储为 0（因为 byte 无法表示 256）
     *
     * @param padded 填充后的字节数组
     * @return 去除填充后的原始字节数组
     * @throws IllegalArgumentException 如果 padded 为 null 或填充长度无效
     */
    @NonNull
    public static byte[] unpad(@NonNull byte[] padded) {
        if (padded == null) {
            throw new IllegalArgumentException("padded cannot be null");
        }

        if (padded.length < 1) {
            throw new IllegalArgumentException("padded data too short: " + padded.length);
        }

        // 读取最后一个字节获取填充长度
        int paddingLength = padded[padded.length - 1] & 0xFF; // 转为无符号

        // 特殊情况：0 表示填充长度为 BLOCK_SIZE (256)
        if (paddingLength == 0) {
            paddingLength = BLOCK_SIZE;
        }

        // 验证填充长度合理性
        if (paddingLength < 1 || paddingLength > BLOCK_SIZE) {
            throw new IllegalArgumentException(
                    "Invalid padding length: " + paddingLength + " (must be 1-"
                            + BLOCK_SIZE + ")");
        }

        if (paddingLength > padded.length) {
            throw new IllegalArgumentException(
                    "Padding length exceeds data size: " + paddingLength + " > "
                            + padded.length);
        }

        int originalLength = padded.length - paddingLength;
        byte[] unpadded = Arrays.copyOf(padded, originalLength);

        Log.d(TAG, "unpadded: " + padded.length + " -> " + originalLength + " bytes (-"
                + paddingLength + " padding)");
        return unpadded;
    }

    /**
     * 填充字符串（便捷方法）
     * 将字符串转为 UTF-8 字节后填充
     *
     * @param plaintext 原始明文字符串
     * @return 填充后的字节数组
     * @throws IllegalArgumentException 如果 plaintext 为 null
     */
    @NonNull
    public static byte[] padString(@NonNull String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext cannot be null");
        }
        byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        return pad(plaintextBytes);
    }

    /**
     * 去除填充并转为字符串（便捷方法）
     *
     * @param padded 填充后的字节数组
     * @return 去除填充后的字符串
     * @throws IllegalArgumentException 如果 padded 为 null 或填充长度无效
     */
    @NonNull
    public static String unpadToString(@NonNull byte[] padded) {
        byte[] unpadded = unpad(padded);
        return new String(unpadded, StandardCharsets.UTF_8);
    }

    /**
     * 检查数据是否已填充（启发式检查）
     * 检查最后一个字节的值是否在合理范围内
     *
     * 注意：这不是 100% 准确的检测方法，仅用于辅助判断
     * 特殊情况：0 也可能是有效的填充长度（表示 BLOCK_SIZE）
     *
     * @param data 要检查的数据
     * @return true 表示可能已填充，false 表示可能未填充
     */
    public static boolean isPadded(@Nullable byte[] data) {
        if (data == null || data.length < 1) {
            return false;
        }

        int potentialPaddingLength = data[data.length - 1] & 0xFF;
        // 0 表示填充长度为 BLOCK_SIZE (256)
        if (potentialPaddingLength == 0) {
            potentialPaddingLength = BLOCK_SIZE;
        }
        return potentialPaddingLength > 0 && potentialPaddingLength <= BLOCK_SIZE
                && potentialPaddingLength <= data.length;
    }

    /**
     * 获取填充后的数据大小
     * 计算原始数据填充后的大小（不实际执行填充）
     *
     * @param originalLength 原始数据长度
     * @return 填充后的数据长度
     */
    public static int getPaddedLength(int originalLength) {
        if (originalLength < 0) {
            throw new IllegalArgumentException("originalLength cannot be negative");
        }
        int paddingLength = BLOCK_SIZE - (originalLength % BLOCK_SIZE);
        if (paddingLength == 0) {
            paddingLength = BLOCK_SIZE;
        }
        return originalLength + paddingLength;
    }

    /**
     * 获取块大小
     *
     * @return 块大小（字节）
     */
    public static int getBlockSize() {
        return BLOCK_SIZE;
    }
}
