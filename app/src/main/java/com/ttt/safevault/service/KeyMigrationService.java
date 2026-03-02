package com.ttt.safevault.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.CryptoConstants;
import com.ttt.safevault.crypto.Ed25519Signer;
import com.ttt.safevault.crypto.Ed25519SignerFactory;
import com.ttt.safevault.crypto.X25519KeyManager;
import com.ttt.safevault.crypto.X25519KeyManagerFactory;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.CryptoSession;
import com.ttt.safevault.analytics.AnalyticsManager;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.SecretKey;

/**
 * 密钥迁移服务
 * 负责从 RSA 加密协议 (v2.0) 迁移到 X25519/Ed25519 协议 (v3.0)
 *
 * 迁移策略：
 * 1. 非强制迁移，用户可选择
 * 2. RSA 密钥保留用于兼容旧分享
 * 3. 新用户默认生成所有密钥（RSA + X25519 + Ed25519）
 * 4. 老用户可选择迁移到 v3.0
 *
 * 迁移流程（老用户）：
 * 1. 检查是否已迁移（幂等性）
 * 2. 解锁获取 DataKey
 * 3. 生成新的 X25519/Ed25519 密钥对
 * 4. 用 DataKey 加密存储新私钥
 * 5. 保存公钥
 * 6. 上传公钥到服务器
 * 7. 更新版本标识
 *
 * @since SafeVault 3.0.0
 */
public class KeyMigrationService {
    private static final String TAG = "KeyMigrationService";

    private final Context context;
    private final SecureKeyStorageManager keyStorage;

    /**
     * 迁移状态枚举
     */
    public enum MigrationStatus {
        /** 未开始 */
        NOT_STARTED,
        /** 进行中 */
        IN_PROGRESS,
        /** 已完成 */
        COMPLETED,
        /** 已失败 */
        FAILED
    }

    /**
     * 迁移结果类
     */
    public static class MigrationResult {
        private final boolean success;
        private final String errorMessage;
        private final MigrationStatus status;
        private final long timestamp;

        private MigrationResult(boolean success, String errorMessage, MigrationStatus status) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }

        public static MigrationResult success() {
            return new MigrationResult(true, null, MigrationStatus.COMPLETED);
        }

        public static MigrationResult inProgress() {
            return new MigrationResult(false, null, MigrationStatus.IN_PROGRESS);
        }

        public static MigrationResult failed(@NonNull String errorMessage) {
            return new MigrationResult(false, errorMessage, MigrationStatus.FAILED);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        public MigrationStatus getStatus() {
            return status;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    /**
     * 迁移进度监听器
     */
    public interface MigrationProgressListener {
        /**
         * 迁移进度更新
         *
         * @param progress 进度百分比 (0-100)
         * @param message 当前步骤的消息
         */
        void onProgress(int progress, String message);

        /**
         * 迁移完成
         *
         * @param result 迁移结果
         */
        void onComplete(@NonNull MigrationResult result);
    }

    /**
     * 构造函数
     *
     * @param context Android 上下文
     */
    public KeyMigrationService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.keyStorage = SecureKeyStorageManager.getInstance(context);
    }

    /**
     * 获取分析管理器实例
     */
    private AnalyticsManager getAnalytics() {
        return AnalyticsManager.getInstance(context);
    }

    /**
     * 检查是否已完成迁移到 v3.0
     *
     * @return 已迁移返回 true
     */
    public boolean hasMigratedToV3() {
        return keyStorage.hasMigratedToV3();
    }

    /**
     * 获取当前密钥版本
     *
     * @return 密钥版本（"v2" 或 "v3"），未找到返回 null
     */
    @Nullable
    public String getCurrentKeyVersion() {
        return keyStorage.getKeyVersion();
    }

    /**
     * 检查会话是否已解锁（可以使用缓存的 DataKey）
     *
     * @return true 表示会话已解锁，可以直接迁移；false 表示需要输入主密码
     */
    public boolean isSessionUnlocked() {
        CryptoSession cryptoSession = CryptoSession.getInstance();
        return cryptoSession.isUnlocked();
    }

    /**
     * 迁移到 X25519/Ed25519 (v3.0) - 使用会话中的 DataKey
     *
     * 异步执行迁移，通过监听器回调结果
     * 此方法用于已登录用户，不需要重复输入主密码
     *
     * @param backendService 后端服务（用于上传公钥）
     * @param listener 进度监听器
     */
    public void migrateToX25519AsyncWithSession(
            @NonNull BackendService backendService,
            @NonNull MigrationProgressListener listener
    ) {
        new Thread(() -> {
            MigrationResult result = migrateToX25519WithSession(backendService);
            listener.onComplete(result);
        }).start();
    }

    /**
     * 迁移到 X25519/Ed25519 (v3.0) - 使用会话中的 DataKey
     *
     * 同步执行迁移，使用 CryptoSession 中缓存的 DataKey
     *
     * @param backendService 后端服务（用于上传公钥）
     * @return 迁移结果
     */
    @NonNull
    public MigrationResult migrateToX25519WithSession(@NonNull BackendService backendService) {
        Log.i(TAG, "开始迁移到 X25519/Ed25519 (v3.0) - 使用会话 DataKey");

        // 1. 检查是否已迁移（幂等性）
        if (hasMigratedToV3()) {
            Log.i(TAG, "已完成迁移，无需重复执行");
            // 记录跳过事件
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SKIPPED, null);
            return MigrationResult.success();
        }

        // 2. 从会话获取 DataKey
        CryptoSession cryptoSession = CryptoSession.getInstance();
        if (!cryptoSession.isUnlocked()) {
            Log.e(TAG, "会话未解锁，无法获取 DataKey");
            return MigrationResult.failed("会话未解锁，请先登录");
        }

        SecretKey dataKey = cryptoSession.getDataKey();
        if (dataKey == null) {
            Log.e(TAG, "无法从会话获取 DataKey");
            return MigrationResult.failed("无法获取加密密钥");
        }

        try {
            // 记录迁移开始
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_IN_PROGRESS, null);

            // 3. 设置迁移状态为进行中
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.IN_PROGRESS);
            Log.d(TAG, "迁移状态：IN_PROGRESS");

            // 4. 生成新的 X25519/Ed25519 密钥对
            Log.d(TAG, "步骤 1/5: 生成 X25519/Ed25519 密钥对...");
            KeyPair x25519KeyPair = X25519KeyManagerFactory.create(context).generateKeyPair();
            KeyPair ed25519KeyPair = Ed25519SignerFactory.create(context).generateKeyPair();
            Log.i(TAG, "密钥对生成成功");

            // 5. 用 DataKey 加密存储新私钥
            Log.d(TAG, "步骤 2/5: 加密并存储 X25519 私钥...");
            boolean x25519Saved = keyStorage.encryptAndSaveX25519PrivateKey(
                    x25519KeyPair.getPrivate(),
                    dataKey,
                    x25519KeyPair.getPublic()
            );
            if (!x25519Saved) {
                throw new SecurityException("X25519 私钥存储失败");
            }
            Log.i(TAG, "X25519 私钥存储成功");

            Log.d(TAG, "步骤 3/5: 加密并存储 Ed25519 私钥...");
            boolean ed25519Saved = keyStorage.encryptAndSaveEd25519PrivateKey(
                    ed25519KeyPair.getPrivate(),
                    dataKey,
                    ed25519KeyPair.getPublic()
            );
            if (!ed25519Saved) {
                throw new SecurityException("Ed25519 私钥存储失败");
            }
            Log.i(TAG, "Ed25519 私钥存储成功");

            // 6. 验证密钥对完整性
            Log.d(TAG, "步骤 4/5: 验证密钥对完整性...");
            if (!keyStorage.validateX25519KeyPair()) {
                throw new SecurityException("X25519 密钥对完整性验证失败");
            }
            if (!keyStorage.validateEd25519KeyPair()) {
                throw new SecurityException("Ed25519 密钥对完整性验证失败");
            }
            Log.i(TAG, "密钥对完整性验证通过");

            // 7. 更新版本标识
            Log.d(TAG, "步骤 5/5: 更新密钥版本标识...");
            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V3);

            // 8. 上传公钥到服务器（后台异步执行，失败不影响迁移）
            try {
                uploadPublicKeysToServer(backendService);
                Log.i(TAG, "公钥上传成功");
            } catch (Exception e) {
                Log.w(TAG, "公钥上传失败，但不影响迁移完成: " + e.getMessage());
                // 不抛出异常，公钥可以稍后重新上传
            }

            // 9. 标记迁移完成
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.COMPLETED);
            // 设置迁移完成标记（区分新用户和迁移用户）
            keyStorage.setMigrationCompletedFlag(true);
            Log.i(TAG, "迁移成功完成（使用会话 DataKey）");

            // 记录迁移成功事件
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SUCCESS, null);

            return MigrationResult.success();

        } catch (Exception e) {
            Log.e(TAG, "迁移失败", e);

            // 标记迁移失败
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.FAILED);
            keyStorage.setMigrationError("迁移失败: " + e.getMessage());

            // 记录迁移失败事件
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_FAILED, e.getMessage());

            return MigrationResult.failed(e.getMessage());
        }
    }

    /**
     * 迁移到 X25519/Ed25519 (v3.0)
     *
     * 异步执行迁移，通过监听器回调结果
     *
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64 编码）
     * @param backendService 后端服务（用于上传公钥）
     * @param listener 进度监听器
     */
    public void migrateToX25519Async(
            @NonNull String masterPassword,
            @NonNull String saltBase64,
            @NonNull BackendService backendService,
            @NonNull MigrationProgressListener listener
    ) {
        new Thread(() -> {
            MigrationResult result = migrateToX25519(masterPassword, saltBase64, backendService);
            listener.onComplete(result);
        }).start();
    }

    /**
     * 迁移到 X25519/Ed25519 (v3.0)
     *
     * 同步执行迁移
     *
     * @param masterPassword 主密码
     * @param saltBase64 盐值（Base64 编码）
     * @param backendService 后端服务（用于上传公钥）
     * @return 迁移结果
     */
    @NonNull
    public MigrationResult migrateToX25519(
            @NonNull String masterPassword,
            @NonNull String saltBase64,
            @NonNull BackendService backendService
    ) {
        Log.i(TAG, "开始迁移到 X25519/Ed25519 (v3.0)");

        // 1. 检查是否已迁移（幂等性）
        if (hasMigratedToV3()) {
            Log.i(TAG, "已完成迁移，无需重复执行");
            // 记录跳过事件
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SKIPPED, null);
            return MigrationResult.success();
        }

        try {
            // 记录迁移开始
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_IN_PROGRESS, null);

            // 2. 设置迁移状态为进行中
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.IN_PROGRESS);
            Log.d(TAG, "迁移状态：IN_PROGRESS");

            // 3. 解锁获取 DataKey
            Log.d(TAG, "步骤 1/6: 解锁获取 DataKey...");
            SecretKey dataKey = keyStorage.decryptDataKeyWithPassword(masterPassword, saltBase64);
            Log.i(TAG, "DataKey 解锁成功");

            // 4. 生成新的 X25519/Ed25519 密钥对
            Log.d(TAG, "步骤 2/6: 生成 X25519/Ed25519 密钥对...");
            KeyPair x25519KeyPair = X25519KeyManagerFactory.create(context).generateKeyPair();
            KeyPair ed25519KeyPair = Ed25519SignerFactory.create(context).generateKeyPair();
            Log.i(TAG, "密钥对生成成功");

            // 5. 用 DataKey 加密存储新私钥
            Log.d(TAG, "步骤 3/6: 加密并存储 X25519 私钥...");
            boolean x25519Saved = keyStorage.encryptAndSaveX25519PrivateKey(
                    x25519KeyPair.getPrivate(),
                    dataKey,
                    x25519KeyPair.getPublic()
            );
            if (!x25519Saved) {
                throw new SecurityException("X25519 私钥存储失败");
            }
            Log.i(TAG, "X25519 私钥存储成功");

            Log.d(TAG, "步骤 4/6: 加密并存储 Ed25519 私钥...");
            boolean ed25519Saved = keyStorage.encryptAndSaveEd25519PrivateKey(
                    ed25519KeyPair.getPrivate(),
                    dataKey,
                    ed25519KeyPair.getPublic()
            );
            if (!ed25519Saved) {
                throw new SecurityException("Ed25519 私钥存储失败");
            }
            Log.i(TAG, "Ed25519 私钥存储成功");

            // 6. 验证密钥对完整性
            Log.d(TAG, "步骤 5/6: 验证密钥对完整性...");
            if (!keyStorage.validateX25519KeyPair()) {
                throw new SecurityException("X25519 密钥对完整性验证失败");
            }
            if (!keyStorage.validateEd25519KeyPair()) {
                throw new SecurityException("Ed25519 密钥对完整性验证失败");
            }
            Log.i(TAG, "密钥对完整性验证通过");

            // 7. 更新版本标识
            Log.d(TAG, "步骤 6/6: 更新密钥版本标识...");
            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V3);

            // 8. 上传公钥到服务器（后台异步执行，失败不影响迁移）
            try {
                uploadPublicKeysToServer(backendService);
                Log.i(TAG, "公钥上传成功");
            } catch (Exception e) {
                Log.w(TAG, "公钥上传失败，但不影响迁移完成: " + e.getMessage());
                // 不抛出异常，公钥可以稍后重新上传
            }

            // 9. 标记迁移完成
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.COMPLETED);
            // 设置迁移完成标记（区分新用户和迁移用户）
            keyStorage.setMigrationCompletedFlag(true);
            Log.i(TAG, "迁移成功完成");

            // 记录迁移成功事件
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SUCCESS, null);

            return MigrationResult.success();

        } catch (Exception e) {
            Log.e(TAG, "迁移失败", e);

            // 标记迁移失败
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.FAILED);
            keyStorage.setMigrationError("迁移失败: " + e.getMessage());

            // 记录迁移失败事件
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_FAILED, e.getMessage());

            return MigrationResult.failed(e.getMessage());
        }
    }

    /**
     * 初始化加密密钥（新用户）
     *
     * 为新用户同时生成所有密钥：
     * - RSA 密钥对 (v2.0 兼容)
     * - X25519 密钥对 (v3.0)
     * - Ed25519 密钥对 (v3.0)
     *
     * @param dataKey DataKey（已生成）
     * @return 初始化成功返回 true
     */
    public boolean initializeCryptoKeys(@NonNull SecretKey dataKey) {
        Log.i(TAG, "初始化加密密钥（新用户）");

        try {
            // 1. 生成 RSA 密钥对（v2.0 兼容）
            Log.d(TAG, "生成 RSA 密钥对...");
            java.security.KeyPairGenerator rsaKeyGen = java.security.KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(2048);
            java.security.KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

            // 2. 生成 X25519 密钥对（v3.0）
            Log.d(TAG, "生成 X25519 密钥对...");
            KeyPair x25519KeyPair = X25519KeyManagerFactory.create(context).generateKeyPair();

            // 3. 生成 Ed25519 密钥对（v3.0）
            Log.d(TAG, "生成 Ed25519 密钥对...");
            KeyPair ed25519KeyPair = Ed25519SignerFactory.create(context).generateKeyPair();

            // 4. 保存 RSA 密钥对
            Log.d(TAG, "保存 RSA 密钥对...");
            boolean rsaSaved = keyStorage.encryptAndSaveRsaPrivateKey(
                    rsaKeyPair.getPrivate(),
                    dataKey,
                    rsaKeyPair.getPublic()
            );
            if (!rsaSaved) {
                throw new SecurityException("RSA 密钥对存储失败");
            }

            // 5. 保存 X25519 密钥对
            Log.d(TAG, "保存 X25519 密钥对...");
            boolean x25519Saved = keyStorage.encryptAndSaveX25519PrivateKey(
                    x25519KeyPair.getPrivate(),
                    dataKey,
                    x25519KeyPair.getPublic()
            );
            if (!x25519Saved) {
                throw new SecurityException("X25519 密钥对存储失败");
            }

            // 6. 保存 Ed25519 密钥对
            Log.d(TAG, "保存 Ed25519 密钥对...");
            boolean ed25519Saved = keyStorage.encryptAndSaveEd25519PrivateKey(
                    ed25519KeyPair.getPrivate(),
                    dataKey,
                    ed25519KeyPair.getPublic()
            );
            if (!ed25519Saved) {
                throw new SecurityException("Ed25519 密钥对存储失败");
            }

            // 7. 设置版本标识
            Log.d(TAG, "设置密钥版本...");
            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V3);

            Log.i(TAG, "加密密钥初始化成功（v3.0）");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "加密密钥初始化失败", e);
            return false;
        }
    }

    /**
     * 上传公钥到服务器
     *
     * @param backendService 后端服务
     * @throws Exception 上传失败时抛出
     */
    private void uploadPublicKeysToServer(@NonNull BackendService backendService) throws Exception {
        Log.d(TAG, "上传公钥到服务器...");

        // 获取公钥
        String x25519PublicKey = keyStorage.getX25519PublicKeyBase64();
        String ed25519PublicKey = keyStorage.getEd25519PublicKeyBase64();

        if (x25519PublicKey == null || ed25519PublicKey == null) {
            throw new IllegalStateException("公钥不存在");
        }

        // 调用后端 API 上传公钥
        boolean uploaded = backendService.uploadEccPublicKey(
                x25519PublicKey,
                ed25519PublicKey,
                com.ttt.safevault.crypto.CryptoConstants.KEY_VERSION_V3
        );

        if (!uploaded) {
            throw new IllegalStateException("公钥上传失败");
        }

        Log.d(TAG, "X25519 公钥: " + x25519PublicKey.substring(0, Math.min(20, x25519PublicKey.length())) + "...");
        Log.d(TAG, "Ed25519 公钥: " + ed25519PublicKey.substring(0, Math.min(20, ed25519PublicKey.length())) + "...");
    }

    /**
     * 回滚迁移（删除 v3.0 密钥，保留 v2.0）
     *
     * 警告：此操作不可逆！
     */
    public void rollbackMigration() {
        Log.w(TAG, "回滚迁移：删除 v3.0 密钥");

        try {
            // 删除 X25519 密钥
            android.content.SharedPreferences prefs = context.getSharedPreferences(
                    "secure_key_storage",
                    Context.MODE_PRIVATE
            );
            prefs.edit()
                    .remove("x25519_public_key")
                    .remove("encrypted_x25519_private_key")
                    .remove("ed25519_public_key")
                    .remove("encrypted_ed25519_private_key")
                    .commit();

            // 恢复密钥版本
            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V2);

            // 重置迁移状态
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.NOT_STARTED);

            // 清除迁移完成标记
            keyStorage.setMigrationCompletedFlag(false);

            Log.w(TAG, "迁移回滚完成");
        } catch (Exception e) {
            Log.e(TAG, "迁移回滚失败", e);
        }
    }

    /**
     * 重新上传公钥到服务器
     *
     * 用于网络失败后的重试
     *
     * @param backendService 后端服务
     * @return 上传成功返回 true
     */
    public boolean retryUploadPublicKeys(@NonNull BackendService backendService) {
        try {
            uploadPublicKeysToServer(backendService);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "公钥上传失败", e);
            return false;
        }
    }

    /**
     * 获取迁移状态
     *
     * @return 迁移状态
     */
    @NonNull
    public SecureKeyStorageManager.MigrationStatus getMigrationStatus() {
        return keyStorage.getMigrationStatus();
    }

    /**
     * 获取迁移错误消息
     *
     * @return 错误消息，无错误返回 null
     */
    @Nullable
    public String getMigrationError() {
        return keyStorage.getMigrationError();
    }

    /**
     * 获取迁移时间戳
     *
     * @return 迁移时间戳（毫秒），未迁移返回 0
     */
    public long getMigrationTimestamp() {
        return keyStorage.getMigrationTimestamp();
    }
}