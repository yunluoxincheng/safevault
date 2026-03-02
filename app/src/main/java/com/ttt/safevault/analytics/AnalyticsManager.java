package com.ttt.safevault.analytics;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.CryptoConstants;

/**
 * 分析管理器
 *
 * 负责记录和统计应用的各种指标，包括：
 * - 迁移成功率
 * - 密钥版本使用比例
 * - 性能指标
 *
 * @since SafeVault 3.6.0
 */
public class AnalyticsManager {
    private static final String TAG = "AnalyticsManager";
    private static final String PREFS_NAME = "analytics_prefs";

    // 埋点事件类型
    public static final String EVENT_KEY_MIGRATION = "key_migration";
    public static final String EVENT_SHARE_CREATED = "share_created";
    public static final String EVENT_SHARE_RECEIVED = "share_received";
    public static final String EVENT_CRYPTO_OPERATION = "crypto_operation";

    // 迁移状态
    public static final String MIGRATION_STATUS_SUCCESS = "success";
    public static final String MIGRATION_STATUS_FAILED = "failed";
    public static final String MIGRATION_STATUS_IN_PROGRESS = "in_progress";
    public static final String MIGRATION_STATUS_SKIPPED = "skipped";

    // 加密操作类型
    public static final String CRYPTO_OP_V2_ENCRYPT = "v2_encrypt";
    public static final String CRYPTO_OP_V2_DECRYPT = "v2_decrypt";
    public static final String CRYPTO_OP_V3_ENCRYPT = "v3_encrypt";
    public static final String CRYPTO_OP_V3_DECRYPT = "v3_decrypt";

    // 偏好键
    private static final String KEY_MIGRATION_ATTEMPTS = "migration_attempts";
    private static final String KEY_MIGRATION_SUCCESS_COUNT = "migration_success_count";
    private static final String KEY_MIGRATION_FAILURE_COUNT = "migration_failure_count";
    private static final String KEY_V2_SHARE_COUNT = "v2_share_count";
    private static final String KEY_V3_SHARE_COUNT = "v3_share_count";
    private static final String KEY_TOTAL_SHARES = "total_shares";

    private static AnalyticsManager instance;
    private final SharedPreferences prefs;
    private final Context context;

    private AnalyticsManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取单例实例
     */
    public static synchronized AnalyticsManager getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new AnalyticsManager(context);
        }
        return instance;
    }

    /**
     * 记录迁移事件
     *
     * @param status 迁移状态
     * @param errorMessage 错误消息（失败时）
     */
    public void recordMigrationEvent(@NonNull String status, @Nullable String errorMessage) {
        try {
            int attempts = prefs.getInt(KEY_MIGRATION_ATTEMPTS, 0) + 1;
            prefs.edit().putInt(KEY_MIGRATION_ATTEMPTS, attempts).apply();

            if (MIGRATION_STATUS_SUCCESS.equals(status)) {
                int successCount = prefs.getInt(KEY_MIGRATION_SUCCESS_COUNT, 0) + 1;
                prefs.edit().putInt(KEY_MIGRATION_SUCCESS_COUNT, successCount).apply();
                Log.i(TAG, "记录迁移成功事件，成功次数: " + successCount);
            } else if (MIGRATION_STATUS_FAILED.equals(status)) {
                int failureCount = prefs.getInt(KEY_MIGRATION_FAILURE_COUNT, 0) + 1;
                prefs.edit().putInt(KEY_MIGRATION_FAILURE_COUNT, failureCount).apply();
                Log.e(TAG, "记录迁移失败事件，失败次数: " + failureCount + ", 错误: " + errorMessage);
            } else if (MIGRATION_STATUS_IN_PROGRESS.equals(status)) {
                Log.d(TAG, "记录迁移进行中事件");
            } else if (MIGRATION_STATUS_SKIPPED.equals(status)) {
                Log.d(TAG, "记录迁移跳过事件（已迁移）");
            }
        } catch (Exception e) {
            Log.e(TAG, "记录迁移事件失败", e);
        }
    }

    /**
     * 记录分享创建事件
     *
     * @param protocolVersion 协议版本（"2.0" 或 "3.0"）
     */
    public void recordShareCreated(@NonNull String protocolVersion) {
        try {
            int totalShares = prefs.getInt(KEY_TOTAL_SHARES, 0) + 1;
            prefs.edit().putInt(KEY_TOTAL_SHARES, totalShares).apply();

            if (CryptoConstants.PROTOCOL_VERSION_V2.equals(protocolVersion)) {
                int v2Count = prefs.getInt(KEY_V2_SHARE_COUNT, 0) + 1;
                prefs.edit().putInt(KEY_V2_SHARE_COUNT, v2Count).apply();
                Log.d(TAG, "记录 v2.0 分享创建，总数: " + v2Count);
            } else if (CryptoConstants.PROTOCOL_VERSION_V3.equals(protocolVersion)) {
                int v3Count = prefs.getInt(KEY_V3_SHARE_COUNT, 0) + 1;
                prefs.edit().putInt(KEY_V3_SHARE_COUNT, v3Count).apply();
                Log.d(TAG, "记录 v3.0 分享创建，总数: " + v3Count);
            }
        } catch (Exception e) {
            Log.e(TAG, "记录分享创建事件失败", e);
        }
    }

    /**
     * 记录分享接收事件
     *
     * @param protocolVersion 协议版本（"2.0" 或 "3.0"）
     */
    public void recordShareReceived(@NonNull String protocolVersion) {
        try {
            if (CryptoConstants.PROTOCOL_VERSION_V2.equals(protocolVersion)) {
                Log.d(TAG, "记录 v2.0 分享接收");
            } else if (CryptoConstants.PROTOCOL_VERSION_V3.equals(protocolVersion)) {
                Log.d(TAG, "记录 v3.0 分享接收");
            }
        } catch (Exception e) {
            Log.e(TAG, "记录分享接收事件失败", e);
        }
    }

    /**
     * 记录加密操作性能指标
     *
     * @param operation 操作类型
     * @param durationMs 执行时长（毫秒）
     */
    public void recordCryptoOperation(@NonNull String operation, long durationMs) {
        try {
            // 性能指标可以用于后续优化分析
            Log.d(TAG, String.format("记录加密操作: %s, 耗时: %d ms", operation, durationMs));

            // 如果操作耗时异常，记录警告
            long expectedThreshold;
            switch (operation) {
                case CRYPTO_OP_V3_ENCRYPT:
                case CRYPTO_OP_V3_DECRYPT:
                    expectedThreshold = 100; // v3.0 应该很快（< 100ms）
                    break;
                case CRYPTO_OP_V2_ENCRYPT:
                case CRYPTO_OP_V2_DECRYPT:
                    expectedThreshold = 500; // v2.0 较慢（< 500ms）
                    break;
                default:
                    expectedThreshold = 1000;
            }

            if (durationMs > expectedThreshold) {
                Log.w(TAG, String.format("加密操作耗时异常: %s, 耗时: %d ms (预期阈值: %d ms)",
                    operation, durationMs, expectedThreshold));
            }
        } catch (Exception e) {
            Log.e(TAG, "记录加密操作失败", e);
        }
    }

    /**
     * 获取迁移成功率
     *
     * @return 成功率（0.0 - 1.0），如果没有数据返回 0
     */
    public float getMigrationSuccessRate() {
        int successCount = prefs.getInt(KEY_MIGRATION_SUCCESS_COUNT, 0);
        int failureCount = prefs.getInt(KEY_MIGRATION_FAILURE_COUNT, 0);
        int totalAttempts = successCount + failureCount;

        if (totalAttempts == 0) {
            return 0f;
        }

        float rate = (float) successCount / totalAttempts;
        Log.d(TAG, "迁移成功率: " + String.format("%.2f%%", rate * 100) + " (" +
               successCount + "/" + totalAttempts + ")");
        return rate;
    }

    /**
     * 获取密钥版本使用比例
     *
     * @return 包含 v2 和 v3 比例的对象
     */
    public KeyVersionStats getKeyVersionStats() {
        int v2Count = prefs.getInt(KEY_V2_SHARE_COUNT, 0);
        int v3Count = prefs.getInt(KEY_V3_SHARE_COUNT, 0);
        int totalShares = prefs.getInt(KEY_TOTAL_SHARES, 0);

        KeyVersionStats stats = new KeyVersionStats();

        if (totalShares == 0) {
            stats.v2Percentage = 0f;
            stats.v3Percentage = 0f;
            stats.totalShares = 0;
        } else {
            stats.v2Percentage = (float) v2Count / totalShares;
            stats.v3Percentage = (float) v3Count / totalShares;
            stats.totalShares = totalShares;
        }

        Log.d(TAG, String.format("密钥版本统计 - 总数: %d, v2.0: %.1f%%, v3.0: %.1f%%",
            stats.totalShares, stats.v2Percentage * 100, stats.v3Percentage * 100));

        return stats;
    }

    /**
     * 获取迁移统计数据
     *
     * @return 迁移统计数据
     */
    public MigrationStats getMigrationStats() {
        int attempts = prefs.getInt(KEY_MIGRATION_ATTEMPTS, 0);
        int successCount = prefs.getInt(KEY_MIGRATION_SUCCESS_COUNT, 0);
        int failureCount = prefs.getInt(KEY_MIGRATION_FAILURE_COUNT, 0);

        return new MigrationStats(attempts, successCount, failureCount, getMigrationSuccessRate());
    }

    /**
     * 清除所有分析数据（用于测试或隐私保护）
     */
    public void clearAllData() {
        prefs.edit().clear().apply();
        Log.i(TAG, "已清除所有分析数据");
    }

    /**
     * 重置分享计数（用于统计新周期）
     */
    public void resetShareCounters() {
        prefs.edit()
            .putInt(KEY_V2_SHARE_COUNT, 0)
            .putInt(KEY_V3_SHARE_COUNT, 0)
            .putInt(KEY_TOTAL_SHARES, 0)
            .apply();
        Log.d(TAG, "已重置分享计数器");
    }

    /**
     * 密钥版本统计类
     */
    public static class KeyVersionStats {
        public float v2Percentage;  // v2.0 使用比例（0.0 - 1.0）
        public float v3Percentage;  // v3.0 使用比例（0.0 - 1.0）
        public int totalShares;     // 总分享数

        public KeyVersionStats() {
            this.v2Percentage = 0f;
            this.v3Percentage = 0f;
            this.totalShares = 0;
        }

        @Override
        public String toString() {
            return String.format("KeyVersionStats{total=%d, v2=%.1f%%, v3=%.1f%%}",
                totalShares, v2Percentage * 100, v3Percentage * 100);
        }
    }

    /**
     * 迁移统计类
     */
    public static class MigrationStats {
        public final int totalAttempts;   // 总尝试次数
        public final int successCount;    // 成功次数
        public final int failureCount;    // 失败次数
        public final float successRate;   // 成功率（0.0 - 1.0）

        public MigrationStats(int totalAttempts, int successCount, int failureCount, float successRate) {
            this.totalAttempts = totalAttempts;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.successRate = successRate;
        }

        @Override
        public String toString() {
            return String.format("MigrationStats{attempts=%d, success=%d, failed=%d, rate=%.1f%%}",
                totalAttempts, successCount, failureCount, successRate * 100);
        }
    }
}