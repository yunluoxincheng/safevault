package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 加密数据同步管理器
 * 负责加密私钥和密码库数据的上传下载
 *
 * 三层安全架构：移除 KeyManager 依赖，使用 EncryptedPrivateKey 内部类
 *
 * @since SafeVault 3.4.0 (移除旧安全架构，完全迁移到三层架构)
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

    /**
     * 加密的私钥（用于云端同步）
     */
    public static class EncryptedPrivateKey {
        private final String encryptedKey;
        private final String iv;
        private final String salt;

        public EncryptedPrivateKey(String encryptedKey, String iv, String salt) {
            this.encryptedKey = encryptedKey;
            this.iv = iv;
            this.salt = salt;
        }

        public String getEncryptedKey() {
            return encryptedKey;
        }

        public String getIv() {
            return iv;
        }

        public String getSalt() {
            return salt;
        }
    }

    private final Context context;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final android.content.SharedPreferences prefs;
    private final ExecutorService databaseExecutor;  // 用于数据库操作的线程池

    public EncryptionSyncManager(@NonNull Context context, @NonNull RetrofitClient retrofitClient) {
        this.context = context.getApplicationContext();
        this.retrofitClient = retrofitClient;
        this.tokenManager = retrofitClient.getTokenManager();
        this.prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE);
        this.databaseExecutor = Executors.newSingleThreadExecutor();
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
    public EncryptedPrivateKey downloadEncryptedPrivateKey() {
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
                return new EncryptedPrivateKey(
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
    public boolean uploadEncryptedVaultData(String encryptedVaultData, String iv, String authTag) {
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
                    encryptedVaultData, iv, authTag, currentVersion, false
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
                    response.getDataAuthTag(),  // 添加 authTag
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
                // 本地无数据（新设备），下载并解密云端数据
                Log.d(TAG, "No local data, downloading and decrypting cloud data...");
                return decryptAndImportVaultData(cloudVault);
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
     * 解密云端数据并插入到本地数据库
     *
     * @return 同步结果
     */
    public SyncResult useCloudData() {
        Log.d(TAG, "Using cloud data to overwrite local");

        // 获取云端数据（不在这里更新版本号，等导入成功后再更新）
        try {
            String userId = tokenManager.getUserId();
            if (userId == null) {
                Log.e(TAG, "No user ID found for vault download");
                return SyncResult.failure("未找到用户ID");
            }

            com.ttt.safevault.dto.response.VaultResponse response =
                retrofitClient.getVaultServiceApi()
                    .getVault(userId)
                    .blockingFirst();

            if (response != null && response.getEncryptedData() != null) {
                long cloudVersion = response.getVersion();
                Log.d(TAG, "Cloud vault version: " + cloudVersion);

                // 创建 EncryptedVaultData 对象
                BackendService.EncryptedVaultData cloudVault = new BackendService.EncryptedVaultData(
                    response.getEncryptedData(),
                    response.getDataIv(),
                    response.getDataAuthTag(),
                    String.valueOf(cloudVersion)
                );

                // 解密并导入数据
                SyncResult result = decryptAndImportVaultData(cloudVault);

                if (result.isSuccess()) {
                    // 只在导入成功后才更新版本号
                    prefs.edit()
                        .putLong("vault_version", cloudVersion)
                        .putLong("vault_last_sync", System.currentTimeMillis())
                        .apply();
                    Log.d(TAG, "Cloud data imported successfully, version updated to: " + cloudVersion);
                    return SyncResult.success(cloudVersion);
                } else {
                    // 导入失败，不更新版本号
                    Log.e(TAG, "Failed to import cloud data: " + result.getMessage());
                    return result;
                }
            } else {
                return SyncResult.failure("云端无数据");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to download cloud data", e);
            return SyncResult.failure("下载云端数据失败：" + e.getMessage());
        }
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
     * 生成基于用户的固定 salt（用于密码库加密）
     * 使用用户邮箱的哈希确保 salt 可重现
     *
     * @param email 用户邮箱
     * @return Base64 编码的 salt
     */
    private String generateVaultSaltForUser(String email) {
        try {
            // 使用 SHA-256 哈希邮箱生成确定性的 32 字节 salt
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");

            // 添加 "SafeVaultVaultSalt" 前缀以区分不同用途的 salt
            String saltInput = "SafeVaultVaultSalt:" + email;
            byte[] hash = digest.digest(saltInput.getBytes(StandardCharsets.UTF_8));

            return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate vault salt", e);
            throw new RuntimeException("无法生成 salt", e);
        }
    }

    /**
     * 上传本地数据覆盖云端
     *
     * @param localVersion 本地版本号
     * @return 同步结果
     */
    private SyncResult uploadLocalVaultData(long localVersion) {
        Log.d(TAG, "Uploading local data to overwrite cloud. Local version: " + localVersion);

        try {
            // 1. 获取 BackupEncryptionManager 实例
            com.ttt.safevault.security.BackupEncryptionManager backupEncryptionManager =
                com.ttt.safevault.security.BackupEncryptionManager.getInstance(context);

            // 2. 检查应用是否已解锁
            com.ttt.safevault.security.CryptoSession cryptoSession =
                com.ttt.safevault.security.CryptoSession.getInstance();
            if (!cryptoSession.isUnlocked()) {
                Log.e(TAG, "Cannot upload vault: app is locked");
                return SyncResult.failure("应用已锁定，请先解锁");
            }

            // 3. 获取主密码（用于加密备份数据）
            // 从 BackendService 获取内存中的主密码
            com.ttt.safevault.model.BackendService backendService =
                com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
            String masterPassword = backendService.getMasterPassword();
            if (masterPassword == null || masterPassword.isEmpty()) {
                Log.e(TAG, "Cannot upload vault: master password not available");
                return SyncResult.failure("无法获取主密码，请重新解锁应用");
            }

            // 4. 获取所有密码条目

            // 5. 获取所有密码数据（在后台线程执行）
            java.util.List<com.ttt.safevault.model.PasswordItem> items;
            try {
                // 使用后台线程获取数据，避免主线程访问数据库
                items = java.util.concurrent.Executors.newSingleThreadExecutor().submit(
                    () -> backendService.getAllItems()
                ).get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get items from database", e);
                return SyncResult.failure("读取本地数据失败：" + e.getMessage());
            }

            if (items == null || items.isEmpty()) {
                Log.w(TAG, "No local data to upload");
                // 重要提示：不要用空数据覆盖云端！
                // 如果本地为空且云端有数据，应该下载云端数据
                BackendService.EncryptedVaultData cloudVault = downloadEncryptedVaultData();
                if (cloudVault != null && cloudVault.encryptedData != null && !cloudVault.encryptedData.isEmpty()) {
                    try {
                        long cloudVersion = Long.parseLong(cloudVault.version);
                        if (cloudVersion > 0) {
                            Log.w(TAG, "Local is empty but cloud has data (version " + cloudVersion + "), downloading from cloud instead");
                            return decryptAndImportVaultData(cloudVault);
                        }
                    } catch (NumberFormatException e) {
                        // 忽略版本解析错误，继续上传空数据
                    }
                }
                // 如果云端也没有数据，允许上传空数据
                items = new java.util.ArrayList<>();
            }

            Log.d(TAG, "Found " + items.size() + " items to upload");

            // 6. 序列化为 JSON
            String jsonData = com.ttt.safevault.utils.BackupJsonUtil.serializePasswordList(items);
            Log.d(TAG, "Serialized data length: " + jsonData.length() + " characters");

            // 7. 获取用户邮箱以生成固定 salt
            // 注意：必须使用邮箱而不是 userId，因为 salt 需要跨设备一致
            String email = tokenManager.getLastLoginEmail();
            if (email == null || email.isEmpty()) {
                // 降级：尝试从 SharedPreferences 获取
                email = prefs.getString("user_email", "");
            }
            if (email == null || email.isEmpty()) {
                Log.e(TAG, "Cannot get user email for salt generation");
                return SyncResult.failure("无法获取用户邮箱");
            }

            String fixedSalt = backupEncryptionManager.getOrGenerateUserSalt(email);
            Log.d(TAG, "Using fixed salt for encryption based on email: " + email);

            // 8. 使用主密码和固定 salt 加密数据（云端同步模式）
            com.ttt.safevault.security.BackupEncryptionManager.CloudBackupResult encryptionResult =
                backupEncryptionManager.encryptForCloudSync(jsonData, masterPassword, fixedSalt);

            Log.d(TAG, "Data encrypted successfully with fixed salt (Argon2id)");

            // 9. 上传加密数据到服务器
            boolean uploadSuccess = uploadEncryptedVaultData(
                encryptionResult.getEncryptedData(),
                encryptionResult.getIv(),
                encryptionResult.getAuthTag()
            );

            if (uploadSuccess) {
                Log.d(TAG, "Vault data uploaded successfully");
                // 获取更新后的版本号
                long newVersion = getVaultVersion();
                return SyncResult.success(newVersion);
            } else {
                Log.e(TAG, "Failed to upload vault data to server");
                return SyncResult.failure("上传到服务器失败");
            }

        } catch (IllegalStateException e) {
            // 应用已锁定
            Log.e(TAG, "Cannot upload vault: " + e.getMessage());
            return SyncResult.failure("应用已锁定，请先解锁");
        } catch (Exception e) {
            Log.e(TAG, "Failed to upload local vault data", e);
            return SyncResult.failure("上传失败：" + e.getMessage());
        }
    }

    /**
     * 解密并导入密码库数据到本地数据库
     *
     * @param cloudVault 云端加密数据
     * @return 同步结果
     */
    private SyncResult decryptAndImportVaultData(BackendService.EncryptedVaultData cloudVault) {
        Log.d(TAG, "========== START decryptAndImportVaultData ==========");
        Log.d(TAG, "Cloud vault version: " + cloudVault.version);
        Log.d(TAG, "Encrypted data length: " + (cloudVault.encryptedData != null ? cloudVault.encryptedData.length() : "null"));
        Log.d(TAG, "IV length: " + (cloudVault.iv != null ? cloudVault.iv.length() : "null"));

        try {
            // 1. 获取 BackupEncryptionManager 实例
            com.ttt.safevault.security.BackupEncryptionManager backupEncryptionManager =
                com.ttt.safevault.security.BackupEncryptionManager.getInstance(context);

            // 2. 检查应用是否已解锁
            com.ttt.safevault.security.CryptoSession cryptoSession =
                com.ttt.safevault.security.CryptoSession.getInstance();
            if (!cryptoSession.isUnlocked()) {
                Log.e(TAG, "Cannot import vault: app is locked");
                return SyncResult.failure("应用已锁定，请先解锁");
            }

            // 3. 获取主密码
            // 从 BackendService 获取内存中的主密码
            com.ttt.safevault.model.BackendService backendService =
                com.ttt.safevault.ServiceLocator.getInstance().getBackendService();
            String masterPassword = backendService.getMasterPassword();
            if (masterPassword == null || masterPassword.isEmpty()) {
                Log.e(TAG, "Cannot import vault: master password not available");
                Log.e(TAG, "Please log out and log in again to restore full functionality");
                return SyncResult.failure("无法获取主密码，请退出后重新登录");
            }
            Log.d(TAG, "Master password obtained, length: " + masterPassword.length());

            // 4. 获取用户邮箱以生成固定 salt（与加密时相同）
            // 注意：必须使用邮箱而不是 userId，确保与加密时使用相同的 salt
            String email = tokenManager.getLastLoginEmail();
            Log.d(TAG, "Email from tokenManager: " + (email != null ? email : "null"));

            if (email == null || email.isEmpty()) {
                // 降级：尝试从 SharedPreferences 获取
                email = prefs.getString("user_email", "");
                Log.d(TAG, "UserId from prefs: " + (email != null ? email : "null"));
            }
            if (email == null || email.isEmpty()) {
                Log.e(TAG, "Cannot get user email for salt generation");
                return SyncResult.failure("无法获取用户邮箱");
            }

            String fixedSalt = backupEncryptionManager.getOrGenerateUserSalt(email);
            Log.d(TAG, "Using fixed salt for decryption based on email: " + email);
            Log.d(TAG, "Generated salt (Base64): " + fixedSalt);
            Log.d(TAG, "Salt length: " + fixedSalt.length());
            Log.d(TAG, "IV (Base64): " + cloudVault.iv);
            Log.d(TAG, "AuthTag (Base64): " + (cloudVault.authTag != null ? cloudVault.authTag : "null"));

            // 5. 解密数据（使用与加密时相同的固定 salt，Argon2id）
            String decryptedJson;
            try {
                Log.d(TAG, "Starting decryption...");
                Log.d(TAG, "Encrypted data (first 100 chars): " +
                    (cloudVault.encryptedData.length() > 100 ? cloudVault.encryptedData.substring(0, 100) + "..." : cloudVault.encryptedData));

                // 检查 authTag 是否可用
                if (cloudVault.authTag == null || cloudVault.authTag.isEmpty()) {
                    Log.e(TAG, "AuthTag is missing from cloud vault data!");
                    return SyncResult.failure("云端数据缺少认证标签，无法解密");
                }

                decryptedJson = backupEncryptionManager.decryptCloudSync(
                    cloudVault.encryptedData,
                    masterPassword,
                    fixedSalt,  // 使用基于邮箱生成的固定 salt
                    cloudVault.iv,
                    cloudVault.authTag
                );

                Log.d(TAG, "Vault data decrypted successfully (Argon2id)");
                Log.d(TAG, "Decrypted JSON length: " + decryptedJson.length());
                Log.d(TAG, "Decrypted JSON (first 200 chars): " +
                    (decryptedJson.length() > 200 ? decryptedJson.substring(0, 200) + "..." : decryptedJson));

            } catch (Exception e) {
                Log.e(TAG, "Failed to decrypt vault data", e);
                Log.e(TAG, "Exception type: " + e.getClass().getName());
                Log.e(TAG, "Exception message: " + e.getMessage());
                return SyncResult.failure("解密数据失败：" + e.getMessage());
            }

            // 6. 反序列化密码列表
            java.util.List<com.ttt.safevault.model.PasswordItem> items;
            try {
                Log.d(TAG, "Starting deserialization...");
                items = com.ttt.safevault.utils.BackupJsonUtil.deserializePasswordList(decryptedJson);
                Log.d(TAG, "Deserialized " + items.size() + " password items");

                // 打印每个密码的基本信息
                for (int i = 0; i < items.size(); i++) {
                    com.ttt.safevault.model.PasswordItem item = items.get(i);
                    Log.d(TAG, "Item[" + i + "]: id=" + item.getId() +
                        ", title=" + item.getTitle() +
                        ", username=" + item.getUsername() +
                        ", password_length=" + (item.getPassword() != null ? item.getPassword().length() : "null"));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to deserialize password list", e);
                Log.e(TAG, "Exception type: " + e.getClass().getName());
                Log.e(TAG, "Exception message: " + e.getMessage());
                return SyncResult.failure("解析数据失败：" + e.getMessage());
            }

            // 7. 保存到本地数据库（在后台线程执行）
            try {
                Log.d(TAG, "BackendService obtained");

                // 使用 CountDownLatch 等待后台任务完成
                CountDownLatch latch = new CountDownLatch(1);
                final int[] importedCount = {0};
                final int[] failedCount = {0};
                final Exception[] saveException = {null};

                databaseExecutor.execute(() -> {
                    try {
                        // 清空现有数据
                        java.util.List<com.ttt.safevault.model.PasswordItem> existingItems =
                            backendService.getAllItems();
                        Log.d(TAG, "Existing local items count: " + existingItems.size());

                        for (com.ttt.safevault.model.PasswordItem item : existingItems) {
                            backendService.deleteItem(item.getId());
                        }
                        Log.d(TAG, "Cleared existing local data");

                        // 插入新数据
                        for (int i = 0; i < items.size(); i++) {
                            com.ttt.safevault.model.PasswordItem item = items.get(i);
                            // 使用负数ID表示新导入的条目
                            item.setId(-1);

                            Log.d(TAG, "Saving item[" + i + "]: title=" + item.getTitle());
                            long newId = backendService.saveItem(item);

                            if (newId > 0) {
                                importedCount[0]++;
                                Log.d(TAG, "Item saved successfully, new ID: " + newId);
                            } else {
                                failedCount[0]++;
                                Log.e(TAG, "Failed to save item: " + item.getTitle());
                            }
                        }

                        Log.d(TAG, "Import completed: " + importedCount[0] + " succeeded, " + failedCount[0] + " failed");

                        // 验证数据是否真的保存了
                        java.util.List<com.ttt.safevault.model.PasswordItem> verifyItems = backendService.getAllItems();
                        Log.d(TAG, "Verification: database now contains " + verifyItems.size() + " items");

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to save items to database", e);
                        saveException[0] = e;
                    } finally {
                        latch.countDown();
                    }
                });

                // 等待后台任务完成（最多30秒）
                boolean completed = latch.await(30, TimeUnit.SECONDS);
                if (!completed) {
                    Log.e(TAG, "Database operation timeout");
                    return SyncResult.failure("数据库操作超时");
                }

                // 检查是否有异常
                if (saveException[0] != null) {
                    throw saveException[0];
                }

                // 获取更新后的版本号
                long cloudVersion = Long.parseLong(cloudVault.version);
                Log.d(TAG, "========== END decryptAndImportVaultData SUCCESS ==========");
                return SyncResult.success(cloudVersion);

            } catch (Exception e) {
                Log.e(TAG, "Failed to save items to database", e);
                Log.e(TAG, "Exception type: " + e.getClass().getName());
                Log.e(TAG, "Exception message: " + e.getMessage());
                return SyncResult.failure("保存到本地数据库失败：" + e.getMessage());
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt and import vault data", e);
            Log.e(TAG, "Exception type: " + e.getClass().getName());
            Log.e(TAG, "Exception message: " + e.getMessage());
            return SyncResult.failure("导入数据失败：" + e.getMessage());
        }
    }
}
