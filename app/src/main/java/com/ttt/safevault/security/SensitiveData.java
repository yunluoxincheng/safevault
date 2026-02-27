package com.ttt.safevault.security;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

/**
 * 敏感数据包装类（内存安全强化）
 *
 * 用于包装 byte[] 和 char[] 类型的敏感数据，提供安全的内存管理：
 * - 实现 AutoCloseable 接口，支持 try-with-resources
 * - close() 方法执行安全清零（先用随机数据填充，再清零）
 * - 禁止序列化（防止敏感数据泄露到序列化流）
 *
 * 设计原则：
 * - 透明包装：不改变上层 API
 * - 类型安全：编译时检查类型
 * - 自动清理：使用后立即清零内存
 *
 * 使用示例：
 * <pre>
 * try (SensitiveData<byte[]> sensitiveData = new SensitiveData<>(secretBytes)) {
 *     byte[] data = sensitiveData.get();
 *     // 使用数据...
 * } // 自动调用 close() 清零内存
 * </pre>
 *
 * @param <T> 数据类型（byte[] 或 char[]）
 * @since SafeVault 3.5.0 (内存安全强化)
 */
public class SensitiveData<T> implements AutoCloseable {
    private static final String TAG = "SensitiveData";

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

    /** 包装的敏感数据 */
    @Nullable
    private T data;

    /** 是否为数组类型 */
    private final boolean isArray;

    /** 是否已关闭 */
    private volatile boolean closed;

    /**
     * 创建敏感数据包装
     *
     * @param data 待包装的数据（byte[] 或 char[]）
     * @throws IllegalArgumentException 如果数据类型不支持
     */
    public SensitiveData(@NonNull T data) {
        if (data == null) {
            throw new IllegalArgumentException("敏感数据不能为 null");
        }

        this.isArray = (data instanceof byte[] || data instanceof char[]);
        if (!isArray) {
            throw new IllegalArgumentException("SensitiveData 仅支持 byte[] 或 char[] 类型，当前类型: "
                    + data.getClass().getName());
        }

        this.data = data;
        this.closed = false;
    }

    /**
     * 获取包装的数据
     *
     * 注意：返回的是原始引用，调用方负责安全处理
     *
     * @return 包装的数据，已关闭返回 null
     */
    @Nullable
    public T get() {
        if (closed) {
            logW(TAG, "尝试获取已关闭的 SensitiveData");
            return null;
        }
        return data;
    }

    /**
     * 检查是否已关闭
     *
     * @return true 表示已关闭（数据已清零）
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * 关闭并清零内存
     *
     * 执行两步清零：
     * 1. 用随机数据覆写（防止通过全零模式识别）
     * 2. 用零覆写（确保最终清零）
     *
     * 此方法是幂等的，多次调用不会产生副作用
     */
    @Override
    public void close() {
        if (closed) {
            logD(TAG, "SensitiveData 已关闭，跳过重复清零");
            return;
        }

        if (data == null) {
            closed = true;
            return;
        }

        try {
            // 使用 MemorySanitizer 进行安全清零
            if (data instanceof byte[]) {
                MemorySanitizer.secureWipe((byte[]) data);
            } else if (data instanceof char[]) {
                MemorySanitizer.secureWipe((char[]) data);
            }

            data = null;
            closed = true;

            logD(TAG, "SensitiveData 已安全清零并关闭");

        } catch (Exception e) {
            logE(TAG, "清零 SensitiveData 时出现异常", e);
            // 即使出现异常，也要标记为关闭并清空引用
            data = null;
            closed = true;
        }
    }

    /**
     * 禁止序列化（防止敏感数据泄露）
     *
     * @param out 输出流（未使用）
     * @throws IOException 始终抛出，禁止序列化
     */
    private void writeObject(@NonNull ObjectOutputStream out) throws IOException {
        throw new IOException("SensitiveData 不支持序列化（防止敏感数据泄露）");
    }

    /**
     * 禁止反序列化（防止敏感数据泄露）
     *
     * @param in 输入流（未使用）
     * @throws IOException 始终抛出，禁止反序列化
     * @throws ClassNotFoundException 始终抛出，禁止反序列化
     */
    private void readObject(@NonNull ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        throw new IOException("SensitiveData 不支持反序列化（防止敏感数据泄露）");
    }

    /**
     * 获取数据长度
     *
     * @return 数据长度，已关闭返回 0
     */
    public int length() {
        if (closed || data == null) {
            return 0;
        }

        if (data instanceof byte[]) {
            return ((byte[]) data).length;
        } else if (data instanceof char[]) {
            return ((char[]) data).length;
        }

        return 0;
    }

    /**
     * 检查数据是否为空
     *
     * @return true 表示数据为空或已关闭
     */
    public boolean isEmpty() {
        return length() == 0;
    }

    /**
     * 创建字节数组副本（深拷贝）
     *
     * 注意：调用方负责清零返回的副本
     *
     * @return 字节数组副本，类型不匹配返回 null
     */
    @Nullable
    public byte[] copyBytes() {
        if (closed || data == null) {
            return null;
        }

        if (data instanceof byte[]) {
            byte[] original = (byte[]) data;
            return Arrays.copyOf(original, original.length);
        }

        return null;
    }

    /**
     * 创建字符数组副本（深拷贝）
     *
     * 注意：调用方负责清零返回的副本
     *
     * @return 字符数组副本，类型不匹配返回 null
     */
    @Nullable
    public char[] copyChars() {
        if (closed || data == null) {
            return null;
        }

        if (data instanceof char[]) {
            char[] original = (char[]) data;
            return Arrays.copyOf(original, original.length);
        }

        return null;
    }

    /**
     * 获取调试信息（不包含敏感数据）
     *
     * @return 调试信息字符串
     */
    @NonNull
    @Override
    public String toString() {
        return "SensitiveData{" +
                "type=" + (data != null ? data.getClass().getSimpleName() : "null") +
                ", length=" + length() +
                ", closed=" + closed +
                '}';
    }

    /**
     * 对象 finalize 时确保清零
     *
     * 注意：不推荐依赖 finalize，应显式调用 close() 或使用 try-with-resources
     *
     * @throws Throwable 可能抛出的异常
     * @deprecated 使用 try-with-resources 替代
     */
    @Deprecated
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                logW(TAG, "SensitiveData 未显式关闭，在 finalize 中清零（推荐使用 try-with-resources）");
                close();
            }
        } finally {
            super.finalize();
        }
    }
}
