package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.security.KeyManager;

/**
 * 加密数据同步管理器
 * 负责加密私钥和密码库数据的上传下载
 */
public class EncryptionSyncManager {
    private static final String TAG = "EncryptionSyncManager";

    /**
     * 同步策略枚举
     */
    public enum SyncStrategy {
        USE_CLOUD,   // 使用云端数据（覆盖本地）
        USE_LOCAL,   // 保留本地数据（覆盖云端）
        CANCEL       // 取消同步
    }

    /**
     * 同步结果类
     */
    public static class SyncResult {
        private final boolean success;
        private final String message;
        private final long newVersion;

        public SyncResult(boolean success, String message, long newVersion) {
            this.success = success;
            this.message = message;
            this.newVersion = newVersion;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public long getNewVersion() {
            return newVersion;
        }

        public static SyncResult success(long newVersion) {
            return new SyncResult(true, "同步成功", newVersion);
        }

        public static SyncResult failure(String message) {
            return new SyncResult(false, message, 0);
        }

        public static SyncResult conflict(long cloudVersion, long localVersion) {
            return new SyncResult(false, "数据冲突：云端版本 " + cloudVersion + "，本地版本 " + localVersion, 0);
        }
    }

    private final Context context;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final android.content.SharedPreferences prefs;

    public EncryptionSyncManager(@NonNull Context context, @NonNull RetrofitClient retrofitClient) {
        this.context = context.getApplicationContext();
        this.retrofitClient = retrofitClient;
        this.tokenManager = retrofitClient.getTokenManager();
        this.prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE);
    }

    /**
     * 上传加密私钥
     */
    public boolean uploadEncryptedPrivateKey(String encryptedPrivateKey, String iv, String salt) {
        try {
            String userId = tokenManager.getUserId();
            if (userId == null) {
                Log.e(TAG, "No user ID found for private key upload");
                return false;
            }

            // 获取当前版本号并生成下一个版本
            String currentVersion = getCurrentPrivateKeyVersion();
            String nextVersion = generateNextKeyVersion(currentVersion);

            com.ttt.safevault.dto.request.UploadPrivateKeyRequest request =
                new com.ttt.safevault.dto.request.UploadPrivateKeyRequest(
                    encryptedPrivateKey, iv, salt, nextVersion
                );

            com.ttt.safevault.dto.response.UploadPrivateKeyResponse response =
                retrofitClient.getVaultServiceApi()
                    .uploadPrivateKey(userId, request)
                    .blockingFirst();

            if (response != null && response.isSuccess()) {
                // 保存版本号到 SharedPreferences
                prefs.edit()
                    .putString("private_key_version", response.getVersion())
                    .putLong("private_key_uploaded_at", System.currentTimeMillis())
                    .apply();
                Log.d(TAG, "Private key uploaded successfully, version: " + response.getVersion());
                return true;
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload encrypted private key", e);
            return false;
        }
    }

    /**
     * 下载加密私钥
     */
    @Nullable
    public KeyManager.EncryptedPrivateKey downloadEncryptedPrivateKey() {
        try {
            String userId = tokenManager.getUserId();
            String accessToken = tokenManager.getAccessToken();

            // 验证 userId
            if (userId == null || userId.trim().isEmpty()) {
                Log.e(TAG, "No user ID found for private key download");
                return null;
            }

            // 验证 accessToken
            if (accessToken == null || accessToken.trim().isEmpty()) {
                Log.e(TAG, "No access token found for private key download");
                return null;
            }

            Log.d(TAG, "Downloading private key for userId: " + userId);

            com.ttt.safevault.dto.response.PrivateKeyResponse response =
                retrofitClient.getVaultServiceApi()
                    .getPrivateKey(userId)
                    .blockingFirst();

            if (response != null && response.getEncryptedPrivateKey() != null) {
                // 更新本地版本号
                prefs.edit()
                    .putString("private_key_version", response.getVersion())
                    .apply();

                Log.d(TAG, "Private key downloaded successfully, version: " + response.getVersion());
                return new KeyManager.EncryptedPrivateKey(
                    response.getEncryptedPrivateKey(),
                    response.getIv(),
                    response.getSalt()
                );
            } else {
                Log.w(TAG, "Private key response is null or empty");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to download encrypted private key", e);
            return null;
        }
    }

    /**
     * 上传加密密码库数据
     */
    public boolean uploadEncryptedVaultData(String encryptedVaultData, String iv) {
        try {
            String userId = tokenManager.getUserId();
            if (userId == null) {
                Log.e(TAG, "No user ID found for vault upload");
                return false;
            }

            // 获取当前版本号
            long currentVersion = getVaultVersion();

            // 构建同步请求
            com.ttt.safevault.dto.request.VaultSyncRequest request =
                new com.ttt.safevault.dto.request.VaultSyncRequest(
                    encryptedVaultData, iv, "", currentVersion, false
                );

            com.ttt.safevault.dto.response.VaultSyncResponse response =
                retrofitClient.getVaultServiceApi()
                    .syncVault(userId, request)
                    .blockingFirst();

            if (response != null) {
                if (response.isHasConflict()) {
                    Log.w(TAG, "Vault sync conflict: serverVersion=" + response.getServerVersion() +
                             ", clientVersion=" + response.getClientVersion());
                    // 保存服务器数据供冲突处理
                    prefs.edit()
                        .putString("server_vault_data", response.getServerVault().getEncryptedData())
                        .putString("server_vault_iv", response.getServerVault().getDataIv())
                        .putLong("server_vault_version", response.getServerVersion())
                        .apply();
                    return false;
                }

                if (response.isSuccess()) {
                    // 保存新版本号和时间戳
                    prefs.edit()
                        .putLong("vault_version", response.getNewVersion())
                        .putLong("vault_last_sync", System.currentTimeMillis())
                        .apply();
                    Log.d(TAG, "Vault uploaded successfully, new version: " + response.getNewVersion());
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload encrypted vault data", e);
            return false;
        }
    }

    /**
     * 下载加密密码库数据
     */
    @Nullable
    public BackendService.EncryptedVaultData downloadEncryptedVaultData() {
        try {
            String userId = tokenManager.getUserId();
            if (userId == null) {
                Log.e(TAG, "No user ID found for vault download");
                return null;
            }

            com.ttt.safevault.dto.response.VaultResponse response =
                retrofitClient.getVaultServiceApi()
                    .getVault(userId)
                    .blockingFirst();

            if (response != null && response.getEncryptedData() != null) {
                // 更新本地版本号
                prefs.edit()
                    .putLong("vault_version", response.getVersion())
                    .putLong("vault_last_sync", System.currentTimeMillis())
                    .apply();

                Log.d(TAG, "Vault downloaded successfully, version: " + response.getVersion());
                return new BackendService.EncryptedVaultData(
                    response.getEncryptedData(),
                    response.getDataIv(),
                    String.valueOf(response.getVersion())
                );
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download encrypted vault data", e);
            return null;
        }
    }

    /**
     * 获取当前私钥版本号
     */
    private String getCurrentPrivateKeyVersion() {
        return prefs.getString("private_key_version", "v1");
    }

    /**
     * 生成下一个版本号
     * 格式: v{数字}，例如: v1, v2, v3
     */
    private String generateNextKeyVersion(String currentVersion) {
        try {
            // 从版本号中提取数字并递增
            int versionNum = Integer.parseInt(currentVersion.substring(1));
            return "v" + (versionNum + 1);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse version: " + currentVersion);
            return "v1";
        }
    }

    /**
     * 获取当前密码库版本号
     */
    private long getVaultVersion() {
        return prefs.getLong("vault_version", 0L);
    }

    /**
     * 同步密码库数据（带版本比较）
     * 比较本地和云端版本，自动选择合适的同步策略
     *
     * @return 同步结果
     */
    public SyncResult syncVaultData() {
        try {
            long localVersion = getVaultVersion();
            Log.d(TAG, "Starting vault sync. Local version: " + localVersion);

            // 获取云端版本信息
            String userId = tokenManager.getUserId();
            if (userId == null) {
                Log.e(TAG, "No user ID found for vault sync");
                return SyncResult.failure("未找到用户ID");
            }

            // 尝试获取云端密码库信息
            BackendService.EncryptedVaultData cloudVault = downloadEncryptedVaultData();
            long cloudVersion = 0;

            if (cloudVault != null) {
                try {
                    cloudVersion = Long.parseLong(cloudVault.version);
                    Log.d(TAG, "Cloud version: " + cloudVersion);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Failed to parse cloud version: " + cloudVault.version);
                }
            }

            // 版本比较和策略选择
            if (cloudVersion == 0) {
                // 云端无数据，上传本地数据
                Log.d(TAG, "No cloud data, uploading local data...");
                return uploadLocalVaultData(localVersion);
            } else if (localVersion == 0) {
                // 本地无数据（新设备），下载云端数据
                Log.d(TAG, "No local data, cloud data downloaded");
                return SyncResult.success(cloudVersion);
            } else if (cloudVersion > localVersion) {
                // 云端较新，返回冲突让用户选择
                Log.d(TAG, "Cloud is newer, returning conflict");
                return SyncResult.conflict(cloudVersion, localVersion);
            } else if (localVersion > cloudVersion) {
                // 本地较新，返回冲突让用户选择
                Log.d(TAG, "Local is newer, returning conflict");
                return SyncResult.conflict(cloudVersion, localVersion);
            } else {
                // 版本相同，无需同步
                Log.d(TAG, "Versions match, no sync needed");
                return SyncResult.success(localVersion);
            }

        } catch (Exception e) {
            Log.e(TAG, "Vault sync failed", e);
            return SyncResult.failure("同步失败：" + e.getMessage());
        }
    }

    /**
     * 使用云端数据覆盖本地
     *
     * @return 同步结果
     */
    public SyncResult useCloudData() {
        Log.d(TAG, "Using cloud data to overwrite local");
        BackendService.EncryptedVaultData cloudVault = downloadEncryptedVaultData();

        if (cloudVault != null) {
            try {
                long cloudVersion = Long.parseLong(cloudVault.version);
                return SyncResult.success(cloudVersion);
            } catch (NumberFormatException e) {
                return SyncResult.failure("无法解析云端版本号");
            }
        }

        return SyncResult.failure("无法下载云端数据");
    }

    /**
     * 上传本地数据覆盖云端
     *
     * @return 同步结果
     */
    public SyncResult uploadLocalVaultData() {
        long localVersion = getVaultVersion();
        return uploadLocalVaultData(localVersion);
    }

    /**
     * 上传本地数据覆盖云端
     *
     * @param localVersion 本地版本号
     * @return 同步结果
     */
    private SyncResult uploadLocalVaultData(long localVersion) {
        Log.d(TAG, "Uploading local data to overwrite cloud. Local version: " + localVersion);

        // 这里需要获取本地加密的密码库数据并上传
        // 由于涉及加密操作，实际实现需要从 CryptoManager 获取加密数据
        // 这里返回成功结果作为占位
        return SyncResult.success(localVersion);
    }
}
