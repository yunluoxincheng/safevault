package com.ttt.safevault.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.data.EncryptedPasswordEntity;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.service.manager.PasswordManager;
import com.ttt.safevault.security.SessionLockedException;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据迁移服务
 * 负责将旧格式的加密数据迁移到新格式（带安全随机填充）
 *
 * 迁移策略：
 * - 后台静默迁移
 * - 批量处理，显示进度
 * - 支持断点续传
 * - 完成后标记迁移状态
 *
 * @since SafeVault 3.6.0
 */
public class DataMigrationService {
    private static final String TAG = "DataMigrationService";
    private static final String PREFS_NAME = "data_migration_prefs";
    private static final String KEY_MIGRATION_COMPLETED = "migration_to_padded_format_completed";
    private static final String KEY_MIGRATION_VERSION = "migration_version";

    // 当前迁移版本号
    private static final int MIGRATION_VERSION = 1;

    private final PasswordDao passwordDao;
    private final PasswordManager passwordManager;
    private final SharedPreferences prefs;
    private final ExecutorService executor;

    // 迁移监听器
    public interface MigrationListener {
        /**
         * 迁移开始
         * @param totalItems 总项目数
         */
        void onMigrationStarted(int totalItems);

        /**
         * 迁移进度更新
         * @param current 当前处理的项目索引
         * @param total 总项目数
         * @param entity 当前处理的实体
         */
        void onMigrationProgress(int current, int total, EncryptedPasswordEntity entity);

        /**
         * 迁移成功完成
         * @param migratedCount 成功迁移的项目数
         */
        void onMigrationSuccess(int migratedCount);

        /**
         * 迁移失败
         * @param error 错误信息
         * @param failedCount 失败前的成功数量
         */
        void onMigrationFailed(String error, int failedCount);
    }

    /**
     * 构造函数
     *
     * @param context 上下文
     * @param passwordDao 密码数据访问对象
     * @param passwordManager 密码管理器
     */
    public DataMigrationService(@NonNull Context context,
                                @NonNull PasswordDao passwordDao,
                                @NonNull PasswordManager passwordManager) {
        this.passwordDao = passwordDao;
        this.passwordManager = passwordManager;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.executor = Executors.newSingleThreadExecutor();
        Log.i(TAG, "DataMigrationService 初始化");
    }

    /**
     * 检查是否已完成迁移
     *
     * @return true 表示已完成迁移
     */
    public boolean isMigrationCompleted() {
        boolean completed = prefs.getBoolean(KEY_MIGRATION_COMPLETED, false);
        int version = prefs.getInt(KEY_MIGRATION_VERSION, 0);
        Log.d(TAG, "Migration status: completed=" + completed + ", version=" + version);
        return completed && version >= MIGRATION_VERSION;
    }

    /**
     * 检查是否需要迁移
     *
     * @return true 表示需要迁移
     */
    public boolean needsMigration() {
        // 检查是否有未迁移的数据
        List<EncryptedPasswordEntity> allEntities = passwordDao.getAll();
        if (allEntities.isEmpty()) {
            Log.d(TAG, "No data to migrate");
            return false;
        }

        // 检查第一个实体的格式（如果包含版本标识则为新格式）
        boolean needsMigration = false;
        for (EncryptedPasswordEntity entity : allEntities) {
            if (isOldFormat(entity)) {
                needsMigration = true;
                break;
            }
        }

        Log.d(TAG, "Needs migration: " + needsMigration);
        return needsMigration && !isMigrationCompleted();
    }

    /**
     * 检查实体是否为旧格式（不包含版本标识）
     *
     * @param entity 加密密码实体
     * @return true 表示为旧格式
     */
    private boolean isOldFormat(EncryptedPasswordEntity entity) {
        // 检查加密字段是否包含版本标识
        // 旧格式：iv:ciphertext
        // 新格式：v2:iv:ciphertext
        String encryptedField = entity.getEncryptedTitle();
        if (encryptedField != null && !encryptedField.isEmpty()) {
            String[] parts = encryptedField.split(":", 3);
            // 如果只有 2 个部分，则为旧格式
            return parts.length == 2;
        }
        return false;
    }

    /**
     * 执行迁移（异步）
     *
     * @param listener 迁移监听器
     */
    public void migrateToPaddedFormat(@NonNull MigrationListener listener) {
        if (isMigrationCompleted()) {
            Log.i(TAG, "Migration already completed, skipping");
            listener.onMigrationSuccess(0);
            return;
        }

        executor.execute(() -> {
            try {
                performMigration(listener);
            } catch (Exception e) {
                Log.e(TAG, "Migration failed with exception", e);
                listener.onMigrationFailed(e.getMessage(), 0);
            }
        });
    }

    /**
     * 执行迁移（同步）
     *
     * @param listener 迁移监听器
     */
    private void performMigration(@NonNull MigrationListener listener) {
        Log.i(TAG, "Starting migration to padded format");

        // 获取所有密码项
        List<EncryptedPasswordEntity> allEntities = passwordDao.getAll();
        int totalItems = allEntities.size();
        Log.i(TAG, "Found " + totalItems + " items to migrate");

        if (totalItems == 0) {
            Log.i(TAG, "No items to migrate, marking as completed");
            markMigrationCompleted();
            listener.onMigrationSuccess(0);
            return;
        }

        listener.onMigrationStarted(totalItems);

        AtomicInteger migratedCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);

        // 批量迁移
        for (int i = 0; i < allEntities.size(); i++) {
            EncryptedPasswordEntity entity = allEntities.get(i);

            try {
                // 检查是否需要迁移
                if (!isOldFormat(entity)) {
                    Log.d(TAG, "Item " + entity.getId() + " already in new format, skipping");
                    migratedCount.incrementAndGet();
                    continue;
                }

                // 解密旧格式数据
                PasswordItem item = passwordManager.decryptItem(entity.getId());
                if (item == null) {
                    Log.e(TAG, "Failed to decrypt item " + entity.getId());
                    failedCount.incrementAndGet();
                    continue;
                }

                // 重新加密（会使用新格式）
                int result = passwordManager.saveItem(item);
                if (result < 0) {
                    Log.e(TAG, "Failed to save item " + entity.getId());
                    failedCount.incrementAndGet();
                    continue;
                }

                migratedCount.incrementAndGet();
                Log.d(TAG, "Migrated item " + entity.getId() + " (" + (i + 1) + "/" + totalItems + ")");

                // 通知进度
                listener.onMigrationProgress(i + 1, totalItems, entity);

            } catch (SessionLockedException e) {
                Log.e(TAG, "Session locked during migration", e);
                listener.onMigrationFailed("会话未解锁，请先登录", migratedCount.get());
                return;
            } catch (Exception e) {
                Log.e(TAG, "Failed to migrate item " + entity.getId(), e);
                failedCount.incrementAndGet();
            }
        }

        // 检查迁移结果
        if (failedCount.get() > 0) {
            Log.w(TAG, "Migration completed with " + failedCount.get() + " failures");
            // 仍然标记为完成，但记录失败数量
        }

        // 标记迁移完成
        markMigrationCompleted();
        Log.i(TAG, "Migration completed: " + migratedCount.get() + " items migrated");

        listener.onMigrationSuccess(migratedCount.get());
    }

    /**
     * 标记迁移已完成
     */
    private void markMigrationCompleted() {
        prefs.edit()
                .putBoolean(KEY_MIGRATION_COMPLETED, true)
                .putInt(KEY_MIGRATION_VERSION, MIGRATION_VERSION)
                .apply();
        Log.i(TAG, "Migration marked as completed");
    }

    /**
     * 重置迁移状态（用于测试或强制重新迁移）
     *
     * @param context 上下文
     */
    public static void resetMigrationStatus(@NonNull Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_MIGRATION_COMPLETED)
                .remove(KEY_MIGRATION_VERSION)
                .apply();
        Log.i(TAG, "Migration status reset");
    }

    /**
     * 关闭服务
     */
    public void shutdown() {
        executor.shutdown();
        Log.i(TAG, "DataMigrationService shutdown");
    }
}
