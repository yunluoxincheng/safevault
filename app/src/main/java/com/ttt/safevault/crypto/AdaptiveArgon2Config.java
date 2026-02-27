package com.ttt.safevault.crypto;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Argon2 自适应性能配置工具类
 *
 * 根据设备能力自动计算最优 Argon2id 参数，解决低端设备上的性能问题：
 * - 检测设备可用内存和 CPU 核心数
 * - 设定最低安全下限，防止参数过低
 * - 自动存储参数，避免重复计算
 * - 用户无感知，无需选择
 *
 * 最低安全下限（OWASP 建议的调整值）：
 * - 内存: 64MB (65536 KB)
 * - 迭代次数: 2
 * - 并行度: 2
 *
 * 标准配置（当前值）：
 * - 内存: 128MB (131072 KB)
 * - 迭代次数: 3
 * - 并行度: 4
 *
 * @since SafeVault 3.6.0
 */
public class AdaptiveArgon2Config {
    private static final String TAG = "AdaptiveArgon2Config";
    private static final String PREFS_NAME = "argon2_adaptive_config";

    // SharedPreferences 键
    private static final String KEY_MEMORY_COST = "adaptive_memory_cost";
    private static final String KEY_TIME_COST = "adaptive_time_cost";
    private static final String KEY_PARALLELISM = "adaptive_parallelism";
    private static final String KEY_INITIALIZED = "adaptive_initialized";

    // ========== 最低安全下限（OWASP 建议的调整值） ==========
    /** 最低内存成本（KB）- 64MB */
    private static final int MIN_MEMORY_KB = 65536;
    /** 最低迭代次数 */
    private static final int MIN_ITERATIONS = 2;
    /** 最低并行度 */
    private static final int MIN_PARALLELISM = 2;

    // ========== 标准配置（当前固定值） ==========
    /** 标准内存成本（KB）- 128MB */
    private static final int STANDARD_MEMORY_KB = 131072;
    /** 标准迭代次数 */
    private static final int STANDARD_ITERATIONS = 3;
    /** 标准并行度 */
    private static final int STANDARD_PARALLELISM = 4;

    // ========== 内存使用比例 ==========
    /** 使用设备可用内存的比例（25%） */
    private static final float MEMORY_USAGE_RATIO = 0.25f;

    /**
     * Argon2 参数配置
     */
    public static class Argon2Parameters {
        private final int memory;
        private final int iterations;
        private final int parallelism;

        public Argon2Parameters(int memory, int iterations, int parallelism) {
            this.memory = memory;
            this.iterations = iterations;
            this.parallelism = parallelism;
        }

        public int getMemory() {
            return memory;
        }

        public int getIterations() {
            return iterations;
        }

        public int getParallelism() {
            return parallelism;
        }

        @Override
        public String toString() {
            return String.format("Argon2id(m=%dMB,t=%d,p=%d)",
                    memory / 1024, iterations, parallelism);
        }

        /**
         * 获取详细参数信息
         */
        public String getDetailedInfo() {
            return String.format("Argon2id(memoryCost=%dKB (%dMB), timeCost=%d, parallelism=%d)",
                    memory, memory / 1024, iterations, parallelism);
        }
    }

    /**
     * 初始化自适应参数
     *
     * 检测设备能力，计算最优参数，并存储到 SharedPreferences。
     * 如果参数已存在，则不会重新计算。
     *
     * @param context 应用上下文
     * @return 计算出的参数配置
     */
    @NonNull
    public static Argon2Parameters initializeParameters(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 检查是否已初始化
        if (prefs.getBoolean(KEY_INITIALIZED, false)) {
            Argon2Parameters params = getStoredParameters(context);
            Log.i(TAG, "使用已存储的自适应参数: " + params);
            return params;
        }

        // 计算最优参数
        Argon2Parameters params = getOptimalParameters(context);

        // 存储参数
        prefs.edit()
                .putInt(KEY_MEMORY_COST, params.getMemory())
                .putInt(KEY_TIME_COST, params.getIterations())
                .putInt(KEY_PARALLELISM, params.getParallelism())
                .putBoolean(KEY_INITIALIZED, true)
                .apply();

        Log.i(TAG, "自适应参数初始化完成: " + params);
        return params;
    }

    /**
     * 获取已存储的参数配置
     *
     * 如果参数不存在，返回标准配置。
     *
     * @param context 应用上下文
     * @return 参数配置
     */
    @NonNull
    public static Argon2Parameters getStoredParameters(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int memory = prefs.getInt(KEY_MEMORY_COST, STANDARD_MEMORY_KB);
        int iterations = prefs.getInt(KEY_TIME_COST, STANDARD_ITERATIONS);
        int parallelism = prefs.getInt(KEY_PARALLELISM, STANDARD_PARALLELISM);

        return new Argon2Parameters(memory, iterations, parallelism);
    }

    /**
     * 根据设备能力计算最优参数
     *
     * 计算逻辑：
     * - 内存成本: min(max(可用内存 * 0.25, 最低下限), 标准配置)
     * - 并行度: min(max(CPU核心数, 最低下限), 标准配置)
     * - 迭代次数: 内存 >= 标准配置 ? 标准迭代 : 最低迭代
     *
     * @param context 应用上下文
     * @return 计算出的参数配置
     */
    @NonNull
    public static Argon2Parameters getOptimalParameters(@NonNull Context context) {
        // 1. 检测设备可用内存
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            Log.w(TAG, "无法获取 ActivityManager，使用标准配置");
            return getStandardParameters();
        }

        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        // 系统返回的单位是字节，转换为 KB
        long availableMemoryKB = memoryInfo.totalMem / 1024;
        long usableMemoryKB = (long) (availableMemoryKB * MEMORY_USAGE_RATIO);

        // 2. 计算内存成本（不低于最低下限，不高于标准配置）
        int memory = Math.max(MIN_MEMORY_KB,
                             Math.min((int) usableMemoryKB, STANDARD_MEMORY_KB));

        // 3. 检测 CPU 核心数
        int cpuCores = Runtime.getRuntime().availableProcessors();

        // 4. 计算并行度（不低于最低下限，不高于标准配置）
        int parallelism = Math.max(MIN_PARALLELISM,
                                  Math.min(cpuCores, STANDARD_PARALLELISM));

        // 5. 计算迭代次数（内存充足时使用标准迭代，否则使用最低迭代）
        int iterations = (memory >= STANDARD_MEMORY_KB) ? STANDARD_ITERATIONS : MIN_ITERATIONS;

        Argon2Parameters params = new Argon2Parameters(memory, iterations, parallelism);

        Log.i(TAG, "计算最优参数完成:");
        Log.i(TAG, "  设备总内存: " + (availableMemoryKB / 1024) + "MB");
        Log.i(TAG, "  可用内存(25%): " + (usableMemoryKB / 1024) + "MB");
        Log.i(TAG, "  CPU 核心数: " + cpuCores);
        Log.i(TAG, "  计算结果: " + params.getDetailedInfo());

        return params;
    }

    /**
     * 获取最低安全下限参数
     *
     * @return 最低安全下限参数
     */
    @NonNull
    public static Argon2Parameters getMinimumParameters() {
        return new Argon2Parameters(MIN_MEMORY_KB, MIN_ITERATIONS, MIN_PARALLELISM);
    }

    /**
     * 获取标准配置参数
     *
     * @return 标准配置参数
     */
    @NonNull
    public static Argon2Parameters getStandardParameters() {
        return new Argon2Parameters(STANDARD_MEMORY_KB, STANDARD_ITERATIONS, STANDARD_PARALLELISM);
    }

    /**
     * 检查是否使用降级参数
     *
     * @param context 应用上下文
     * @return 如果参数低于标准配置返回 true
     */
    public static boolean isUsingDegradedParameters(@NonNull Context context) {
        Argon2Parameters params = getStoredParameters(context);
        return params.getMemory() < STANDARD_MEMORY_KB ||
               params.getIterations() < STANDARD_ITERATIONS ||
               params.getParallelism() < STANDARD_PARALLELISM;
    }

    /**
     * 重置自适应参数
     *
     * 清除已存储的参数，下次调用 initializeParameters 时会重新计算。
     *
     * @param context 应用上下文
     */
    public static void resetParameters(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
        Log.i(TAG, "自适应参数已重置");
    }

    /**
     * 检查是否已初始化
     *
     * @param context 应用上下文
     * @return 已初始化返回 true
     */
    public static boolean isInitialized(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_INITIALIZED, false);
    }
}
