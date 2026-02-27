package com.ttt.safevault.security;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKey;

/**
 * 内存清零工具类（内存安全强化）
 *
 * 提供安全清零方法，防止敏感数据泄露到内存转储：
 * - secureWipe(byte[]): 清零字节数组
 * - secureWipe(char[]): 清零字符数组
 * - secureWipe(SecretKey): 清零密钥（尽最大努力）
 *
 * 清零策略（多轮覆盖）：
 * 1. 用随机数据覆写（防止通过全零模式识别已清零区域）
 * 2. 用零覆写（确保最终清零）
 * 3. 多次重复（增加攻击者从内存转储中恢复的难度）
 *
 * 设计原则：
 * - 防止内存分析工具通过"全零"模式识别已清零区域
 * - 增加攻击者从内存转储中提取密钥的难度
 * - 纯 Java 实现，不依赖第三方库
 *
 * @since SafeVault 3.5.0 (内存安全强化)
 */
public final class MemorySanitizer {
    private static final String TAG = "MemorySanitizer";

    /**
     * 安全的日志记录（防止单元测试中 Log 未 mock 导致崩溃）
     */
    private static void logD(String tag, String msg) {
        try {
            Log.d(tag, msg);
        } catch (RuntimeException e) {
            // 单元测试中 Log 可能未 mock，忽略日志错误
        }
    }

    /**
     * 安全的日志记录（防止单元测试中 Log 未 mock 导致崩溃）
     */
    private static void logW(String tag, String msg) {
        try {
            Log.w(tag, msg);
        } catch (RuntimeException e) {
            // 单元测试中 Log 可能未 mock，忽略日志错误
        }
    }

    /**
     * 安全的日志记录（防止单元测试中 Log 未 mock 导致崩溃）
     */
    private static void logE(String tag, String msg) {
        try {
            Log.e(tag, msg);
        } catch (RuntimeException e) {
            // 单元测试中 Log 可能未 mock，忽略日志错误
        }
    }

    /**
     * 安全的日志记录（防止单元测试中 Log 未 mock 导致崩溃）
     */
    private static void logE(String tag, String msg, Throwable tr) {
        try {
            Log.e(tag, msg, tr);
        } catch (RuntimeException e) {
            // 单元测试中 Log 可能未 mock，忽略日志错误
        }
    }

    /** 默认清零轮数（多轮覆盖增加安全性） */
    private static final int DEFAULT_WIPE_PASSES = 3;

    /** 单例 SecureRandom（线程安全，性能优化） */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 私有构造函数（工具类） */
    private MemorySanitizer() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 安全清零字节数组（多轮覆盖）
     *
     * 执行三步清零：
     * 1. 用随机数据覆写（防止通过全零模式识别）
     * 2. 用零覆写（确保最终清零）
     * 3. 多次重复（DEFAULT_WIPE_PASSES 轮）
     *
     * @param data 待清零的字节数组（null 安全）
     */
    public static void secureWipe(@Nullable byte[] data) {
        if (data == null) {
            logD(TAG, "secureWipe(byte[]): data 为 null，跳过清零");
            return;
        }

        if (data.length == 0) {
            logD(TAG, "secureWipe(byte[]): data 长度为 0，跳过清零");
            return;
        }

        try {
            // 多轮覆盖
            for (int pass = 0; pass < DEFAULT_WIPE_PASSES; pass++) {
                // 第1-2轮：用随机数据覆写
                if (pass < DEFAULT_WIPE_PASSES - 1) {
                    RANDOM.nextBytes(data);
                } else {
                    // 最后一轮：用零覆写
                    Arrays.fill(data, (byte) 0);
                }
            }

            logD(TAG, "字节数组已安全清零（长度: " + data.length + "，轮数: " + DEFAULT_WIPE_PASSES + "）");

        } catch (Exception e) {
            // 即使出现异常，也要尝试清零
            Arrays.fill(data, (byte) 0);
            logE(TAG, "清零字节数组时出现异常，已回退到简单清零", e);
        }
    }

    /**
     * 安全清零字符数组（多轮覆盖）
     *
     * 执行三步清零：
     * 1. 用随机字符覆写（防止通过全零模式识别）
     * 2. 用空字符覆写（确保最终清零）
     * 3. 多次重复（DEFAULT_WIPE_PASSES 轮）
     *
     * @param data 待清零的字符数组（null 安全）
     */
    public static void secureWipe(@Nullable char[] data) {
        if (data == null) {
            logD(TAG, "secureWipe(char[]): data 为 null，跳过清零");
            return;
        }

        if (data.length == 0) {
            logD(TAG, "secureWipe(char[]): data 长度为 0，跳过清零");
            return;
        }

        try {
            // 多轮覆盖
            for (int pass = 0; pass < DEFAULT_WIPE_PASSES; pass++) {
                // 第1-2轮：用随机字符覆写
                if (pass < DEFAULT_WIPE_PASSES - 1) {
                    for (int i = 0; i < data.length; i++) {
                        data[i] = (char) (RANDOM.nextInt() & 0xFFFF);
                    }
                } else {
                    // 最后一轮：用空字符覆写
                    Arrays.fill(data, '\0');
                }
            }

            logD(TAG, "字符数组已安全清零（长度: " + data.length + "，轮数: " + DEFAULT_WIPE_PASSES + "）");

        } catch (Exception e) {
            // 即使出现异常，也要尝试清零
            Arrays.fill(data, '\0');
            logE(TAG, "清零字符数组时出现异常，已回退到简单清零", e);
        }
    }

    /**
     * 安全清零 SecretKey（尽最大努力）
     *
     * 注意：此方法只能清零 SecretKey 的外部副本，
     * 无法清零 AndroidKeyStore 内部存储（依赖硬件保护）
     *
     * 执行步骤：
     * 1. 获取密钥编码（getEncoded()）
     * 2. 清零编码字节（secureWipe）
     * 3. 清空引用
     *
     * @param key 待清零的密钥（null 安全）
     */
    public static void secureWipe(@Nullable SecretKey key) {
        if (key == null) {
            logD(TAG, "secureWipe(SecretKey): key 为 null，跳过清零");
            return;
        }

        try {
            byte[] keyBytes = key.getEncoded();
            if (keyBytes != null) {
                // 清零密钥字节
                secureWipe(keyBytes);
                logD(TAG, "SecretKey 已安全清零（算法: " + key.getAlgorithm() + "）");
            } else {
                logW(TAG, "SecretKey.getEncoded() 返回 null（可能是 AndroidKeyStore 密钥，依赖硬件保护）");
            }

        } catch (Exception e) {
            logE(TAG, "清零 SecretKey 时出现异常（可能已被 GC 回收）", e);
        }
    }

    /**
     * 安全清零字节数组（自定义轮数）
     *
     * @param data 待清零的字节数组（null 安全）
     * @param passes 清零轮数（必须 >= 1）
     * @throws IllegalArgumentException 如果 passes < 1
     */
    public static void secureWipe(@Nullable byte[] data, int passes) {
        if (passes < 1) {
            throw new IllegalArgumentException("清零轮数必须 >= 1，当前值: " + passes);
        }

        if (data == null || data.length == 0) {
            secureWipe(data);
            return;
        }

        try {
            // 多轮覆盖
            for (int pass = 0; pass < passes; pass++) {
                // 前 (passes-1) 轮：用随机数据覆写
                if (pass < passes - 1) {
                    RANDOM.nextBytes(data);
                } else {
                    // 最后一轮：用零覆写
                    Arrays.fill(data, (byte) 0);
                }
            }

            logD(TAG, "字节数组已安全清零（长度: " + data.length + "，轮数: " + passes + "）");

        } catch (Exception e) {
            // 即使出现异常，也要尝试清零
            Arrays.fill(data, (byte) 0);
            logE(TAG, "清零字节数组时出现异常，已回退到简单清零", e);
        }
    }

    /**
     * 安全清零字符数组（自定义轮数）
     *
     * @param data 待清零的字符数组（null 安全）
     * @param passes 清零轮数（必须 >= 1）
     * @throws IllegalArgumentException 如果 passes < 1
     */
    public static void secureWipe(@Nullable char[] data, int passes) {
        if (passes < 1) {
            throw new IllegalArgumentException("清零轮数必须 >= 1，当前值: " + passes);
        }

        if (data == null || data.length == 0) {
            secureWipe(data);
            return;
        }

        try {
            // 多轮覆盖
            for (int pass = 0; pass < passes; pass++) {
                // 前 (passes-1) 轮：用随机字符覆写
                if (pass < passes - 1) {
                    for (int i = 0; i < data.length; i++) {
                        data[i] = (char) (RANDOM.nextInt() & 0xFFFF);
                    }
                } else {
                    // 最后一轮：用空字符覆写
                    Arrays.fill(data, '\0');
                }
            }

            logD(TAG, "字符数组已安全清零（长度: " + data.length + "，轮数: " + passes + "）");

        } catch (Exception e) {
            // 即使出现异常，也要尝试清零
            Arrays.fill(data, '\0');
            logE(TAG, "清零字符数组时出现异常，已回退到简单清零", e);
        }
    }

    /**
     * 获取默认清零轮数
     *
     * @return 默认清零轮数
     */
    public static int getDefaultWipePasses() {
        return DEFAULT_WIPE_PASSES;
    }

    /**
     * 验证数组是否已清零（用于测试）
     *
     * 注意：此方法仅用于单元测试验证，不应在生产代码中使用
     *
     * @param data 待验证的字节数组
     * @return true 表示数组已全部清零
     */
    static boolean isZeroed(@Nullable byte[] data) {
        if (data == null) {
            return true;
        }

        for (byte b : data) {
            if (b != 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * 验证数组是否已清零（用于测试）
     *
     * 注意：此方法仅用于单元测试验证，不应在生产代码中使用
     *
     * @param data 待验证的字符数组
     * @return true 表示数组已全部清零
     */
    static boolean isZeroed(@Nullable char[] data) {
        if (data == null) {
            return true;
        }

        for (char c : data) {
            if (c != '\0') {
                return false;
            }
        }

        return true;
    }
}
