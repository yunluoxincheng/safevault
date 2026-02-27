package com.ttt.safevault.crypto;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SecurePaddingUtil 性能测试
 *
 * 测试覆盖：
 * - 填充操作耗时（目标 <1ms）
 * - 数据大小增长（最多 256 字节）
 * - 加密/解密性能影响
 */
@RunWith(RobolectricTestRunner.class)
public class SecurePaddingPerformanceTest {

    /**
     * 测试单个填充操作耗时
     *
     * 目标：填充操作 < 1ms
     */
    @Test
    public void testPerformance_PaddingOperation() {
        // 测试不同大小的数据
        int[] dataSizes = {10, 50, 100, 200, 255, 256, 257, 500};

        for (int size : dataSizes) {
            byte[] data = new byte[size];

            // 预热
            for (int i = 0; i < 100; i++) {
                SecurePaddingUtil.pad(data);
            }

            // 测量耗时
            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                SecurePaddingUtil.pad(data);
            }

            long endTime = System.nanoTime();
            long avgTimeNanos = (endTime - startTime) / iterations;
            double avgTimeMillis = avgTimeNanos / 1_000_000.0;

            System.out.println(String.format("Padding performance (size=%d): %.3f ms per operation",
                    size, avgTimeMillis));

            // 验证：填充操作应 < 1ms
            assertTrue("填充操作应 < 1ms (实际: " + avgTimeMillis + " ms, size=" + size + ")",
                    avgTimeMillis < 1.0);
        }
    }

    /**
     * 测试单个去填充操作耗时
     *
     * 目标：去填充操作 < 1ms
     */
    @Test
    public void testPerformance_UnpaddingOperation() {
        // 测试不同大小的数据
        int[] dataSizes = {10, 50, 100, 200, 255, 256, 257, 500};

        for (int size : dataSizes) {
            byte[] data = new byte[size];
            byte[] padded = SecurePaddingUtil.pad(data);

            // 预热
            for (int i = 0; i < 100; i++) {
                SecurePaddingUtil.unpad(padded);
            }

            // 测量耗时
            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                SecurePaddingUtil.unpad(padded);
            }

            long endTime = System.nanoTime();
            long avgTimeNanos = (endTime - startTime) / iterations;
            double avgTimeMillis = avgTimeNanos / 1_000_000.0;

            System.out.println(String.format("Unpadding performance (size=%d): %.3f ms per operation",
                    size, avgTimeMillis));

            // 验证：去填充操作应 < 1ms
            assertTrue("去填充操作应 < 1ms (实际: " + avgTimeMillis + " ms, size=" + size + ")",
                    avgTimeMillis < 1.0);
        }
    }

    /**
     * 测试数据大小增长
     *
     * 验证：每个字段最多增长 256 字节
     */
    @Test
    public void testPerformance_DataSizeGrowth() {
        int[] dataSizes = {1, 10, 50, 100, 200, 255, 256, 257, 500, 1000};

        System.out.println("Data size growth analysis:");

        for (int size : dataSizes) {
            byte[] data = new byte[size];
            byte[] padded = SecurePaddingUtil.pad(data);
            int growth = padded.length - size;

            System.out.println(String.format("  %4d -> %4d bytes (+%3d bytes, +%.1f%%)",
                    size, padded.length, growth, (growth * 100.0 / size)));

            // 验证：增长应 <= 256 字节
            assertTrue("数据增长应 <= 256 字节 (实际: " + growth + ", size=" + size + ")",
                    growth <= 256);
        }
    }

    /**
     * 测试批量操作性能
     *
     * 模拟加密 1000 个密码项的场景
     */
    @Test
    public void testPerformance_BatchOperations() {
        int itemCount = 1000;
        int avgPasswordLength = 20; // 平均密码长度

        List<byte[]> testData = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            // 生成随机长度的密码（10-50 字符）
            int length = 10 + (int) (Math.random() * 40);
            String password = generateString(length);
            testData.add(password.getBytes(StandardCharsets.UTF_8));
        }

        // 测试批量填充
        long startTime = System.nanoTime();
        List<byte[]> paddedData = new ArrayList<>();
        for (byte[] data : testData) {
            paddedData.add(SecurePaddingUtil.pad(data));
        }
        long paddingEndTime = System.nanoTime();

        // 测试批量去填充
        List<byte[]> unpaddedData = new ArrayList<>();
        for (byte[] padded : paddedData) {
            unpaddedData.add(SecurePaddingUtil.unpad(padded));
        }
        long unpaddingEndTime = System.nanoTime();

        long paddingTime = paddingEndTime - startTime;
        long unpaddingTime = unpaddingEndTime - paddingEndTime;
        long totalTime = unpaddingEndTime - startTime;

        double avgPaddingTime = (paddingTime / 1_000_000.0) / itemCount;
        double avgUnpaddingTime = (unpaddingTime / 1_000_000.0) / itemCount;
        double avgTotalTime = (totalTime / 1_000_000.0) / itemCount;

        System.out.println(String.format(
                "Batch operations (%d items):\n" +
                "  Total padding time: %.2f ms (avg: %.3f ms/item)\n" +
                "  Total unpadding time: %.2f ms (avg: %.3f ms/item)\n" +
                "  Total time: %.2f ms (avg: %.3f ms/item)",
                itemCount,
                paddingTime / 1_000_000.0, avgPaddingTime,
                unpaddingTime / 1_000_000.0, avgUnpaddingTime,
                totalTime / 1_000_000.0, avgTotalTime));

        // 验证：平均每个操作 < 1ms
        assertTrue("平均填充时间应 < 1ms (实际: " + avgPaddingTime + " ms)",
                avgPaddingTime < 1.0);
        assertTrue("平均去填充时间应 < 1ms (实际: " + avgUnpaddingTime + " ms)",
                avgUnpaddingTime < 1.0);
    }

    /**
     * 测试内存使用
     *
     * 验证：填充操作不会导致显著的内存峰值
     */
    @Test
    public void testPerformance_MemoryUsage() {
        Runtime runtime = Runtime.getRuntime();

        // 强制 GC
        System.gc();
        Thread.yield();

        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // 执行大量填充操作
        int operations = 10000;
        List<byte[]> paddedData = new ArrayList<>();

        for (int i = 0; i < operations; i++) {
            String data = "Test password data " + i;
            paddedData.add(SecurePaddingUtil.pad(data.getBytes(StandardCharsets.UTF_8)));
        }

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        double avgMemoryPerItem = memoryUsed / (double) operations;
        double overheadPercent = (memoryUsed / (double) (operations * 256)) * 100;

        System.out.println(String.format(
                "Memory usage (%d operations):\n" +
                "  Memory used: %.2f MB\n" +
                "  Average per item: %.2f bytes\n" +
                "  Overhead: %.1f%%",
                operations,
                memoryUsed / (1024.0 * 1024.0),
                avgMemoryPerItem,
                overheadPercent));

        // 验证：内存使用应合理（每个项目大约 256 字节 + 对象开销）
        assertTrue("平均每个项目的内存使用应 < 1000 字节 (实际: " + avgMemoryPerItem + ")",
                avgMemoryPerItem < 1000.0);
    }

    /**
     * 测试数据库大小影响
     *
     * 估算：100 个密码项，每个 5 个字段，使用填充后的数据库增长
     */
    @Test
    public void testPerformance_DatabaseSizeImpact() {
        int itemCount = 100;
        int fieldsPerItem = 5; // title, username, password, url, notes
        int avgFieldSize = 30; // 平均字段大小

        int totalFields = itemCount * fieldsPerItem;

        // 不使用填充的总大小
        long sizeWithoutPadding = totalFields * avgFieldSize;

        // 使用填充的总大小
        long sizeWithPadding = 0;
        for (int i = 0; i < totalFields; i++) {
            // 生成随机大小的字段
            int fieldSize = avgFieldSize / 2 + (int) (Math.random() * avgFieldSize);
            byte[] data = new byte[fieldSize];
            byte[] padded = SecurePaddingUtil.pad(data);
            sizeWithPadding += padded.length;
        }

        long sizeGrowth = sizeWithPadding - sizeWithoutPadding;
        double growthPercent = (sizeGrowth * 100.0) / sizeWithoutPadding;

        System.out.println(String.format(
                "Database size impact (%d items, %d fields):\n" +
                "  Without padding: %.2f KB\n" +
                "  With padding: %.2f KB\n" +
                "  Growth: %.2f KB (+%.1f%%)\n" +
                "  Average per field: +%.1f bytes",
                itemCount, fieldsPerItem,
                sizeWithoutPadding / 1024.0,
                sizeWithPadding / 1024.0,
                sizeGrowth / 1024.0, growthPercent,
                sizeGrowth / (double) totalFields));

        // 验证：数据库增长应可接受
        assertTrue("数据库增长应 < 500% (实际: " + growthPercent + "%)",
                growthPercent < 500.0);
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
