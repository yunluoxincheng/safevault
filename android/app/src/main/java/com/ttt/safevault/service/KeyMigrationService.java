package com.ttt.safevault.service;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.analytics.AnalyticsManager;
import com.ttt.safevault.crypto.CryptoConstants;
import com.ttt.safevault.crypto.Ed25519SignerFactory;
import com.ttt.safevault.crypto.X25519KeyManagerFactory;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.SessionGuard;

import java.security.KeyPair;

import javax.crypto.SecretKey;

/**
 * 密钥迁移服务：负责将用户密钥从 v2(RSA) 迁移到 v3(X25519/Ed25519)。
 */
public class KeyMigrationService {
    private static final String TAG = "KeyMigrationService";

    private final Context context;
    private final SecureKeyStorageManager keyStorage;

    public enum MigrationStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public enum MigrationStartAction {
        CAN_MIGRATE_WITH_SESSION,
        CAN_MIGRATE_WITH_PASSWORD,
        NEED_PASSWORD,
        NOT_AVAILABLE
    }

    public static class MigrationStartDecision {
        @NonNull
        private final MigrationStartAction action;
        @Nullable
        private final String reason;

        private MigrationStartDecision(@NonNull MigrationStartAction action, @Nullable String reason) {
            this.action = action;
            this.reason = reason;
        }

        public static MigrationStartDecision of(@NonNull MigrationStartAction action, @Nullable String reason) {
            return new MigrationStartDecision(action, reason);
        }

        @NonNull
        public MigrationStartAction getAction() {
            return action;
        }

        @Nullable
        public String getReason() {
            return reason;
        }
    }

    public enum MigrationErrorType {
        SESSION_REQUIRED,
        AUTH_REQUIRED,
        NETWORK,
        CRYPTO,
        STORAGE,
        UNKNOWN
    }

    public static class MigrationResult {
        private final boolean success;
        @Nullable
        private final String errorMessage;
        @Nullable
        private final MigrationErrorType errorType;
        @NonNull
        private final MigrationStatus status;
        private final long timestamp;

        private MigrationResult(
                boolean success,
                @Nullable String errorMessage,
                @Nullable MigrationErrorType errorType,
                @NonNull MigrationStatus status
        ) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.errorType = errorType;
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }

        public static MigrationResult success() {
            return new MigrationResult(true, null, null, MigrationStatus.COMPLETED);
        }

        public static MigrationResult inProgress() {
            return new MigrationResult(false, null, null, MigrationStatus.IN_PROGRESS);
        }

        public static MigrationResult failed(@NonNull MigrationErrorType errorType, @NonNull String errorMessage) {
            return new MigrationResult(false, errorMessage, errorType, MigrationStatus.FAILED);
        }

        // 保留旧签名，避免潜在调用方中断
        public static MigrationResult failed(@NonNull String errorMessage) {
            return failed(MigrationErrorType.UNKNOWN, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        @Nullable
        public String getErrorMessage() {
            return errorMessage;
        }

        @Nullable
        public MigrationErrorType getErrorType() {
            return errorType;
        }

        @NonNull
        public MigrationStatus getStatus() {
            return status;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    public interface MigrationProgressListener {
        void onProgress(int progress, String message);

        void onComplete(@NonNull MigrationResult result);
    }

    public KeyMigrationService(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.keyStorage = SecureKeyStorageManager.getInstance(context);
    }

    private AnalyticsManager getAnalytics() {
        return AnalyticsManager.getInstance(context);
    }

    public boolean hasMigratedToV3() {
        return keyStorage.hasMigratedToV3();
    }

    @Nullable
    public String getCurrentKeyVersion() {
        return keyStorage.getKeyVersion();
    }

    public boolean isSessionUnlocked() {
        SessionGuard sessionGuard = SessionGuard.getInstance();
        return sessionGuard.isUnlocked();
    }

    @NonNull
    public MigrationStartDecision resolveMigrationStartDecision(
            @Nullable String masterPassword,
            @Nullable String saltBase64
    ) {
        if (isSessionUnlocked()) {
            return MigrationStartDecision.of(
                    MigrationStartAction.CAN_MIGRATE_WITH_SESSION,
                    "会话已解锁，可直接迁移"
            );
        }

        if (masterPassword != null && saltBase64 != null) {
            return MigrationStartDecision.of(
                    MigrationStartAction.CAN_MIGRATE_WITH_PASSWORD,
                    "已准备密码参数，可直接迁移"
            );
        }

        if (masterPassword != null || saltBase64 != null) {
            return MigrationStartDecision.of(
                    MigrationStartAction.NOT_AVAILABLE,
                    "迁移参数不完整，请重新验证身份"
            );
        }

        return MigrationStartDecision.of(
                MigrationStartAction.NEED_PASSWORD,
                "需要验证主密码"
        );
    }

    public void migrateToX25519AsyncWithSession(
            @NonNull BackendService backendService,
            @NonNull MigrationProgressListener listener
    ) {
        new Thread(() -> {
            MigrationResult result = migrateToX25519WithSession(backendService);
            listener.onComplete(result);
        }).start();
    }

    @NonNull
    public MigrationResult migrateToX25519WithSession(@NonNull BackendService backendService) {
        Log.i(TAG, "开始迁移到 X25519/Ed25519 (v3.0) - 使用会话 DataKey");

        if (hasMigratedToV3()) {
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SKIPPED, null);
            return MigrationResult.success();
        }

        SessionGuard sessionGuard = SessionGuard.getInstance();
        if (!sessionGuard.isUnlocked()) {
            return MigrationResult.failed(MigrationErrorType.SESSION_REQUIRED, "会话未解锁，请先登录");
        }

        SecretKey dataKey = sessionGuard.getDataKey();
        if (dataKey == null) {
            return MigrationResult.failed(MigrationErrorType.SESSION_REQUIRED, "无法获取加密密钥");
        }

        try {
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_IN_PROGRESS, null);
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.IN_PROGRESS);

            KeyPair x25519KeyPair = X25519KeyManagerFactory.create(context).generateKeyPair();
            KeyPair ed25519KeyPair = Ed25519SignerFactory.create(context).generateKeyPair();

            boolean x25519Saved = keyStorage.encryptAndSaveX25519PrivateKey(
                    x25519KeyPair.getPrivate(),
                    dataKey,
                    x25519KeyPair.getPublic()
            );
            if (!x25519Saved) {
                throw new SecurityException("X25519 私钥存储失败");
            }

            boolean ed25519Saved = keyStorage.encryptAndSaveEd25519PrivateKey(
                    ed25519KeyPair.getPrivate(),
                    dataKey,
                    ed25519KeyPair.getPublic()
            );
            if (!ed25519Saved) {
                throw new SecurityException("Ed25519 私钥存储失败");
            }

            if (!keyStorage.validateX25519KeyPair()) {
                throw new SecurityException("X25519 密钥对完整性验证失败");
            }
            if (!keyStorage.validateEd25519KeyPair()) {
                throw new SecurityException("Ed25519 密钥对完整性验证失败");
            }

            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V3);
            try {
                uploadPublicKeysToServer(backendService);
            } catch (Exception e) {
                Log.w(TAG, "公钥上传失败，但不影响迁移完成: " + getSafeErrorMessage(e));
            }

            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.COMPLETED);
            keyStorage.setMigrationCompletedFlag(true);
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SUCCESS, null);

            return MigrationResult.success();
        } catch (Exception e) {
            Log.e(TAG, "迁移失败", e);
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.FAILED);
            keyStorage.setMigrationError("迁移失败: " + getSafeErrorMessage(e));
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_FAILED, getSafeErrorMessage(e));
            return MigrationResult.failed(classifyMigrationError(e), getSafeErrorMessage(e));
        }
    }

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

    @NonNull
    public MigrationResult migrateToX25519(
            @NonNull String masterPassword,
            @NonNull String saltBase64,
            @NonNull BackendService backendService
    ) {
        Log.i(TAG, "开始迁移到 X25519/Ed25519 (v3.0)");

        if (hasMigratedToV3()) {
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SKIPPED, null);
            return MigrationResult.success();
        }

        try {
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_IN_PROGRESS, null);
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.IN_PROGRESS);

            SecretKey dataKey = keyStorage.decryptDataKeyWithPassword(masterPassword, saltBase64);

            KeyPair x25519KeyPair = X25519KeyManagerFactory.create(context).generateKeyPair();
            KeyPair ed25519KeyPair = Ed25519SignerFactory.create(context).generateKeyPair();

            boolean x25519Saved = keyStorage.encryptAndSaveX25519PrivateKey(
                    x25519KeyPair.getPrivate(),
                    dataKey,
                    x25519KeyPair.getPublic()
            );
            if (!x25519Saved) {
                throw new SecurityException("X25519 私钥存储失败");
            }

            boolean ed25519Saved = keyStorage.encryptAndSaveEd25519PrivateKey(
                    ed25519KeyPair.getPrivate(),
                    dataKey,
                    ed25519KeyPair.getPublic()
            );
            if (!ed25519Saved) {
                throw new SecurityException("Ed25519 私钥存储失败");
            }

            if (!keyStorage.validateX25519KeyPair()) {
                throw new SecurityException("X25519 密钥对完整性验证失败");
            }
            if (!keyStorage.validateEd25519KeyPair()) {
                throw new SecurityException("Ed25519 密钥对完整性验证失败");
            }

            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V3);
            try {
                uploadPublicKeysToServer(backendService);
            } catch (Exception e) {
                Log.w(TAG, "公钥上传失败，但不影响迁移完成: " + getSafeErrorMessage(e));
            }

            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.COMPLETED);
            keyStorage.setMigrationCompletedFlag(true);
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_SUCCESS, null);

            return MigrationResult.success();
        } catch (Exception e) {
            Log.e(TAG, "迁移失败", e);
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.FAILED);
            keyStorage.setMigrationError("迁移失败: " + getSafeErrorMessage(e));
            getAnalytics().recordMigrationEvent(AnalyticsManager.MIGRATION_STATUS_FAILED, getSafeErrorMessage(e));
            return MigrationResult.failed(classifyMigrationError(e), getSafeErrorMessage(e));
        }
    }

    public boolean initializeCryptoKeys(@NonNull SecretKey dataKey) {
        Log.i(TAG, "初始化加密密钥（新用户）");

        try {
            java.security.KeyPairGenerator rsaKeyGen = java.security.KeyPairGenerator.getInstance("RSA");
            rsaKeyGen.initialize(2048);
            java.security.KeyPair rsaKeyPair = rsaKeyGen.generateKeyPair();

            KeyPair x25519KeyPair = X25519KeyManagerFactory.create(context).generateKeyPair();
            KeyPair ed25519KeyPair = Ed25519SignerFactory.create(context).generateKeyPair();

            boolean rsaSaved = keyStorage.encryptAndSaveRsaPrivateKey(
                    rsaKeyPair.getPrivate(),
                    dataKey,
                    rsaKeyPair.getPublic()
            );
            if (!rsaSaved) {
                throw new SecurityException("RSA 密钥对存储失败");
            }

            boolean x25519Saved = keyStorage.encryptAndSaveX25519PrivateKey(
                    x25519KeyPair.getPrivate(),
                    dataKey,
                    x25519KeyPair.getPublic()
            );
            if (!x25519Saved) {
                throw new SecurityException("X25519 密钥对存储失败");
            }

            boolean ed25519Saved = keyStorage.encryptAndSaveEd25519PrivateKey(
                    ed25519KeyPair.getPrivate(),
                    dataKey,
                    ed25519KeyPair.getPublic()
            );
            if (!ed25519Saved) {
                throw new SecurityException("Ed25519 密钥对存储失败");
            }

            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V3);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "初始化加密密钥失败", e);
            return false;
        }
    }

    private void uploadPublicKeysToServer(@NonNull BackendService backendService) throws Exception {
        String x25519PublicKey = keyStorage.getX25519PublicKeyBase64();
        String ed25519PublicKey = keyStorage.getEd25519PublicKeyBase64();

        if (x25519PublicKey == null || ed25519PublicKey == null) {
            throw new IllegalStateException("公钥不存在");
        }

        boolean uploaded = backendService.uploadEccPublicKey(
                x25519PublicKey,
                ed25519PublicKey,
                CryptoConstants.KEY_VERSION_V3
        );

        if (!uploaded) {
            throw new IllegalStateException("公钥上传失败");
        }
    }

    public void rollbackMigration() {
        Log.w(TAG, "回滚迁移：删除 v3.0 密钥");

        try {
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

            keyStorage.setKeyVersion(CryptoConstants.KEY_VERSION_V2);
            keyStorage.setMigrationStatus(SecureKeyStorageManager.MigrationStatus.NOT_STARTED);
            keyStorage.setMigrationCompletedFlag(false);
        } catch (Exception e) {
            Log.e(TAG, "迁移回滚失败", e);
        }
    }

    public boolean retryUploadPublicKeys(@NonNull BackendService backendService) {
        try {
            uploadPublicKeysToServer(backendService);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "公钥上传失败", e);
            return false;
        }
    }

    @NonNull
    public SecureKeyStorageManager.MigrationStatus getMigrationStatus() {
        return keyStorage.getMigrationStatus();
    }

    @Nullable
    public String getMigrationError() {
        return keyStorage.getMigrationError();
    }

    public long getMigrationTimestamp() {
        return keyStorage.getMigrationTimestamp();
    }

    public boolean hasRsaPublicKey() {
        return keyStorage.getRsaPublicKey() != null;
    }

    public boolean hasX25519PublicKey() {
        return keyStorage.getX25519PublicKeyBase64() != null;
    }

    public boolean hasEd25519PublicKey() {
        return keyStorage.getEd25519PublicKeyBase64() != null;
    }

    @Nullable
    public String getRsaPublicKeyBase64() {
        return keyStorage.getRsaPublicKeyBase64();
    }

    @Nullable
    public String getX25519PublicKeyBase64() {
        return keyStorage.getX25519PublicKeyBase64();
    }

    @Nullable
    public String getEd25519PublicKeyBase64() {
        return keyStorage.getEd25519PublicKeyBase64();
    }

    public boolean isRetryableError(@Nullable MigrationErrorType errorType) {
        if (errorType == null) {
            return false;
        }

        return errorType == MigrationErrorType.NETWORK
                || errorType == MigrationErrorType.CRYPTO
                || errorType == MigrationErrorType.STORAGE
                || errorType == MigrationErrorType.UNKNOWN;
    }

    @NonNull
    private MigrationErrorType classifyMigrationError(@NonNull Exception e) {
        if (e instanceof SecurityException) {
            return MigrationErrorType.CRYPTO;
        }

        String message = getSafeErrorMessage(e);
        if (message.contains("会话") || message.contains("DataKey")) {
            return MigrationErrorType.SESSION_REQUIRED;
        }
        if (message.contains("密码") || message.contains("解锁")) {
            return MigrationErrorType.AUTH_REQUIRED;
        }
        if (message.contains("上传") || message.contains("网络") || message.contains("服务器")) {
            return MigrationErrorType.NETWORK;
        }
        if (message.contains("保存") || message.contains("存储") || message.contains("密钥")) {
            return MigrationErrorType.STORAGE;
        }
        return MigrationErrorType.UNKNOWN;
    }

    @NonNull
    private String getSafeErrorMessage(@NonNull Exception e) {
        return e.getMessage() != null ? e.getMessage() : "未知错误";
    }
}
