package com.ttt.safevault.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.security.SessionGuard;
import com.ttt.safevault.security.SecureKeyStorageManager;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.service.manager.*;

import java.security.SecureRandom;
import java.util.List;

import javax.crypto.SecretKey;

/**
 * BackendService接口的具体实现
 * 采用组合模式，将各个功能模块委托给专门的Manager处理
 *
 * 三层安全架构：完全移除旧的安全架构，使用新的三层架构
 *
 * @since SafeVault 3.4.0 (移除旧安全架构，完全迁移到三层架构)
 */
public class BackendServiceImpl implements BackendService {

    private static final String TAG = "BackendServiceImpl";
    private static final String PREFS_NAME = "backend_prefs";
    private static final String PREF_BACKGROUND_TIME = "background_time";
    private static final String CRYPTO_PREFS_NAME = "crypto_prefs";
    private static final String KEY_MASTER_SALT = "master_salt";
    private static final String KEY_INITIALIZED = "initialized";

    private final Context context;
    private final SecurityConfig securityConfig;
    private final SharedPreferences prefs;
    private final SharedPreferences cryptoPrefs;

    // 三层安全架构组件
    private final SessionGuard sessionGuard;
    private final SecureKeyStorageManager secureKeyStorage;

    // 各功能模块的Manager
    private final PasswordManager passwordManager;
    private final PasswordGenerator passwordGenerator;
    private final DataImportExportManager dataImportExportManager;
    private final PinCodeManager pinCodeManager;
    private final AccountManager accountManager;
    private final CloudAuthManager cloudAuthManager;
    private final EncryptionSyncManager encryptionSyncManager;

    public BackendServiceImpl(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.securityConfig = new SecurityConfig(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.cryptoPrefs = context.getSharedPreferences(CRYPTO_PREFS_NAME, Context.MODE_PRIVATE);

        // 初始化三层安全架构组件
        this.sessionGuard = SessionGuard.getInstance();
        this.secureKeyStorage = SecureKeyStorageManager.getInstance(context);

        // 初始化数据库访问层
        PasswordDao passwordDao = AppDatabase.getInstance(context).passwordDao();

        // 初始化云端API客户端
        com.ttt.safevault.network.RetrofitClient retrofitClient =
                com.ttt.safevault.network.RetrofitClient.getInstance(context);

        // 初始化各功能模块的Manager
        this.passwordManager = new PasswordManager(passwordDao, context);
        this.passwordGenerator = new PasswordGenerator();
        this.dataImportExportManager = new DataImportExportManager(context, passwordManager);
        this.pinCodeManager = new PinCodeManager(context, securityConfig);
        this.accountManager = new AccountManager(context, passwordManager, securityConfig, retrofitClient);
        this.cloudAuthManager = new CloudAuthManager(context, retrofitClient);
        this.encryptionSyncManager = new EncryptionSyncManager(context, retrofitClient);

        Log.i(TAG, "BackendServiceImpl 初始化（三层安全架构）");
    }

    // ==================== 加密/解密相关 ====================

    @Override
    public boolean unlock(String masterPassword) {
        try {
            Log.d(TAG, "unlock() 被调用");

            // 获取盐值
            String saltBase64 = cryptoPrefs.getString(KEY_MASTER_SALT, null);
            if (saltBase64 == null) {
                Log.e(TAG, "盐值未找到，请先初始化");
                return false;
            }
            Log.d(TAG, "盐值已找到");

            // 派生 PasswordKey（使用 Argon2id）
            Log.d(TAG, "开始派生 PasswordKey（Argon2id）");
            SecretKey passwordKey = secureKeyStorage.derivePasswordKey(masterPassword, saltBase64);
            Log.d(TAG, "PasswordKey 派生成功");

            // 解密 DataKey（从 PasswordKey 加密版本）
            Log.d(TAG, "开始解密 DataKey");
            SecretKey dataKey = secureKeyStorage.decryptDataKeyWithPassword(masterPassword, saltBase64);
            if (dataKey == null) {
                Log.e(TAG, "DataKey 解密失败（返回 null）");
                return false;
            }
            Log.d(TAG, "DataKey 解密成功");

            // 缓存到 SessionGuard（会话解锁态）
            Log.d(TAG, "调用 sessionGuard.unlockWithDataKey()");
            sessionGuard.unlockWithDataKey(dataKey);
            Log.d(TAG, "sessionGuard.unlockWithDataKey() 完成");

            // 保存主密码到内存（用于云端认证等）
            accountManager.setSessionMasterPassword(masterPassword);

            // 清除后台时间记录
            // 这确保在自动填充场景下验证成功后，后续的会话锁定检查不会误判
            clearBackgroundTime();
            Log.d(TAG, "已清除后台时间记录");

            Log.i(TAG, "解锁成功（三层架构）");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "解锁失败", e);
            return false;
        }
    }

    @Override
    public void lock() {
        // 清除 SessionGuard 中的 DataKey（会话锁定态）
        sessionGuard.clear();
        Log.d(TAG, "已锁定（SessionGuard 已清除）");
    }

    @Override
    public boolean isUnlocked() {
        // 使用 SessionGuard 检查会话状态
        return sessionGuard.isUnlocked();
    }

    @Override
    public String getMasterPassword() {
        // 从 AccountManager 获取内存中的主密码
        return accountManager.getSessionMasterPassword();
    }

    @Override
    public void setSessionMasterPassword(String masterPassword) {
        // 设置会话主密码到 AccountManager（仅内存存储）
        accountManager.setSessionMasterPassword(masterPassword);
    }

    @Override
    public boolean isInitialized() {
        // 检查是否已初始化（盐值存在且 DataKey 存在）
        boolean hasSalt = cryptoPrefs.contains(KEY_MASTER_SALT);
        boolean initializedFlag = cryptoPrefs.getBoolean(KEY_INITIALIZED, false);
        boolean hasDataKey = secureKeyStorage.hasPasswordEncryptedDataKey();

        // 如果盐值和初始化标志存在，但 DataKey 不存在，说明是卸载重装后的异常状态
        // 需要返回 false，让系统走 initialize 流程（从云端恢复）
        boolean isInit = hasSalt && initializedFlag && hasDataKey;

        if (!isInit && (hasSalt || initializedFlag)) {
            Log.w(TAG, "检测到不完整的初始化状态：hasSalt=" + hasSalt +
                      ", initializedFlag=" + initializedFlag +
                      ", hasDataKey=" + hasDataKey +
                      "（可能是卸载重装后，需要从云端恢复）");
        }

        return isInit;
    }

    @Override
    public boolean initialize(String masterPassword) {
        try {
            // 生成盐值
            String saltBase64 = generateSalt();

            // 派生 PasswordKey（使用 Argon2id）
            SecretKey passwordKey = secureKeyStorage.derivePasswordKey(masterPassword, saltBase64);

            // 生成随机 DataKey
            SecretKey dataKey = secureKeyStorage.generateDataKey();

            // 生成 RSA 密钥对
            java.security.KeyPair keyPair = generateRSAKeyPair();
            if (keyPair == null) {
                Log.e(TAG, "RSA 密钥对生成失败");
                return false;
            }

            // 双重加密 DataKey 并保存（使用 SecureKeyStorageManager）
            // 注意：首次初始化时，用户可能还没有启用生物识别，DeviceKey 加密可能失败（这是允许的）
            SecretKey deviceKey = secureKeyStorage.getOrCreateDeviceKey();
            boolean dataKeySaved = secureKeyStorage.encryptAndSaveDataKey(dataKey, passwordKey, deviceKey);
            if (!dataKeySaved) {
                Log.e(TAG, "DataKey 保存失败");
                return false;
            }

            // 加密并保存 RSA 私钥
            boolean saved = secureKeyStorage.encryptAndSaveRsaPrivateKey(
                    keyPair.getPrivate(), dataKey, keyPair.getPublic());

            if (!saved) {
                Log.e(TAG, "RSA 私钥保存失败");
                return false;
            }

            // 保存盐值和初始化标志（到 cryptoPrefs）
            cryptoPrefs.edit()
                    .putString(KEY_MASTER_SALT, saltBase64)
                    .putBoolean(KEY_INITIALIZED, true)
                    .commit();

            // 缓存到 SessionGuard（会话解锁态）
            sessionGuard.unlockWithDataKey(dataKey);

            // 保存主密码到内存
            accountManager.setSessionMasterPassword(masterPassword);

            Log.i(TAG, "初始化成功（三层架构）");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "初始化失败", e);
            return false;
        }
    }

    @Override
    public boolean changeMasterPassword(String oldPassword, String newPassword) {
        try {
            // 1. 验证旧密码
            String saltBase64 = cryptoPrefs.getString(KEY_MASTER_SALT, null);
            if (saltBase64 == null) {
                Log.e(TAG, "盐值未找到");
                return false;
            }

            // 2. 使用旧密码解密 DataKey
            SecretKey dataKey = secureKeyStorage.decryptDataKeyWithPassword(oldPassword, saltBase64);

            // 3. 生成新盐值
            String newSaltBase64 = generateSalt();

            // 4. 使用新密码派生新 PasswordKey
            SecretKey newPasswordKey = secureKeyStorage.derivePasswordKey(newPassword, newSaltBase64);

            // 5. 获取 DeviceKey（如果存在）
            SecretKey deviceKey = null;
            if (secureKeyStorage.hasDeviceEncryptedDataKey()) {
                deviceKey = secureKeyStorage.getOrCreateDeviceKey();
            }

            // 6. 使用新 PasswordKey 重新加密 DataKey（使用 SecureKeyStorageManager）
            boolean dataKeySaved = secureKeyStorage.encryptAndSaveDataKey(dataKey, newPasswordKey, deviceKey);
            if (!dataKeySaved) {
                Log.e(TAG, "使用新密码重新加密 DataKey 失败");
                return false;
            }

            // 7. 保存新的盐值（到 cryptoPrefs）
            cryptoPrefs.edit()
                    .putString(KEY_MASTER_SALT, newSaltBase64)
                    .commit();

            // 8. 更新内存中的主密码
            accountManager.setSessionMasterPassword(newPassword);

            Log.i(TAG, "主密码修改成功");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "主密码修改失败", e);
            return false;
        }
    }

    /**
     * 生成 RSA 密钥对
     */
    private java.security.KeyPair generateRSAKeyPair() {
        try {
            java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception e) {
            Log.e(TAG, "RSA 密钥对生成失败", e);
            return null;
        }
    }

    /**
     * 生成随机盐值
     */
    private String generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP);
    }

    // ==================== 密码管理相关 ====================

    /**
     * 增加本地密码库版本号
     * 当本地数据发生变化（添加、修改、删除密码）时调用
     * 用于标记本地数据已修改，需要在下次同步时上传到云端
     */
    private void incrementLocalVersion() {
        long currentVersion = prefs.getLong("vault_version", 0L);
        prefs.edit().putLong("vault_version", currentVersion + 1).apply();
        Log.d(TAG, "Local vault version incremented from " + currentVersion + " to " + (currentVersion + 1));
    }

    @Override
    public PasswordItem decryptItem(int id) {
        return passwordManager.decryptItem(id);
    }

    @Override
    public List<PasswordItem> search(String query) {
        return passwordManager.search(query);
    }

    @Override
    public int saveItem(PasswordItem item) {
        int result = passwordManager.saveItem(item);
        // 保存成功后，增加本地版本号以标记数据已修改
        if (result > 0) {
            incrementLocalVersion();
            Log.d(TAG, "Password saved, local version incremented");
        }
        return result;
    }

    @Override
    public boolean deleteItem(int id) {
        boolean result = passwordManager.deleteItem(id);
        // 删除成功后，增加本地版本号以标记数据已修改
        if (result) {
            incrementLocalVersion();
            Log.d(TAG, "Password deleted, local version incremented");
        }
        return result;
    }

    @Override
    public List<PasswordItem> getAllItems() {
        return passwordManager.getAllItems();
    }

    // ==================== 密码生成相关 ====================

    @Override
    public String generatePassword(int length, boolean symbols) {
        return passwordGenerator.generatePassword(length, symbols);
    }

    @Override
    public String generatePassword(int length, boolean useUppercase, boolean useLowercase,
                                   boolean useNumbers, boolean useSymbols) {
        return passwordGenerator.generatePassword(length, useUppercase, useLowercase, useNumbers, useSymbols);
    }

    // ==================== 数据导入导出相关 ====================

    @Override
    public boolean exportData(String exportPath) {
        return dataImportExportManager.exportData(exportPath);
    }

    @Override
    public boolean importData(String importPath) {
        return dataImportExportManager.importData(importPath);
    }

    @Override
    public AppStats getStats() {
        return dataImportExportManager.getStats();
    }

    // ==================== PIN码管理相关 ====================

    @Override
    public boolean setPinCode(String pinCode) {
        return pinCodeManager.setPinCode(pinCode);
    }

    @Override
    public boolean verifyPinCode(String pinCode) {
        return pinCodeManager.verifyPinCode(pinCode);
    }

    @Override
    public boolean clearPinCode() {
        return pinCodeManager.clearPinCode();
    }

    @Override
    public boolean isPinCodeEnabled() {
        return pinCodeManager.isPinCodeEnabled();
    }

    // ==================== 账户管理相关 ====================

    @Override
    public void logout() {
        accountManager.logout();
    }

    @Override
    public boolean deleteAccount() {
        return accountManager.deleteAccount();
    }

    @Override
    public boolean resetLocalVault() {
        try {
            Log.d(TAG, "Resetting local vault...");

            // 1. 删除所有本地密码数据
            List<PasswordItem> items = passwordManager.getAllItems();
            for (PasswordItem item : items) {
                passwordManager.deleteItem(item.getId());
            }
            Log.d(TAG, "Local password data deleted");

            // 2. 清除会话
            sessionGuard.clear();

            // 3. 清除生物识别数据
            secureKeyStorage.clearBiometricData();

            // 4. 清除设置（保留用户偏好）
            securityConfig.clear();

            // 5. 清除 crypto_prefs（存储初始化标志和盐值）
            context.getSharedPreferences(CRYPTO_PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
            Log.d(TAG, "Crypto preferences cleared");

            // 6. 清除 SecureKeyStorageManager 中的所有密钥数据
            secureKeyStorage.clearAll();
            Log.d(TAG, "SecureKeyStorage data cleared");

            // 7. 清除 backend_prefs
            prefs.edit().clear().apply();

            Log.i(TAG, "Local vault reset successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to reset local vault", e);
            return false;
        }
    }

    // ==================== 云端认证相关 ====================

    @Override
    public com.ttt.safevault.dto.response.AuthResponse register(String username, String password, String displayName) {
        return cloudAuthManager.register(username, password, displayName);
    }

    @Override
    public com.ttt.safevault.dto.response.AuthResponse login(String username, String password) {
        return cloudAuthManager.login(username, password);
    }

    @Override
    public com.ttt.safevault.dto.response.AuthResponse refreshToken(String refreshToken) {
        return cloudAuthManager.refreshToken(refreshToken);
    }

    @Override
    public boolean isCloudLoggedIn() {
        return cloudAuthManager.isCloudLoggedIn();
    }

    @Override
    public void logoutCloud() {
        cloudAuthManager.logoutCloud();
    }

    @Override
    public com.ttt.safevault.dto.response.CompleteRegistrationResponse completeRegistration(
            String email, String username, String masterPassword) {
        return cloudAuthManager.completeRegistration(email, username, masterPassword);
    }

    // ==================== 加密数据同步相关 ====================

    @Override
    public boolean uploadEncryptedPrivateKey(String encryptedPrivateKey, String iv, String salt, String authTag) {
        return encryptionSyncManager.uploadEncryptedPrivateKey(encryptedPrivateKey, iv, salt, authTag);
    }

    @Override
    public com.ttt.safevault.service.manager.EncryptionSyncManager.EncryptedPrivateKey downloadEncryptedPrivateKey() {
        return encryptionSyncManager.downloadEncryptedPrivateKey();
    }

    @Override
    public boolean uploadEncryptedVaultData(String encryptedVaultData, String iv, String authTag) {
        return encryptionSyncManager.uploadEncryptedVaultData(encryptedVaultData, iv, authTag);
    }

    @Override
    public EncryptedVaultData downloadEncryptedVaultData() {
        return encryptionSyncManager.downloadEncryptedVaultData();
    }

    // ==================== 后台时间相关 ====================

    @Override
    public void recordBackgroundTime() {
        prefs.edit().putLong(PREF_BACKGROUND_TIME, System.currentTimeMillis()).apply();
    }

    @Override
    public void clearBackgroundTime() {
        prefs.edit().remove(PREF_BACKGROUND_TIME).apply();
    }

    @Override
    public long getBackgroundTime() {
        return prefs.getLong(PREF_BACKGROUND_TIME, 0);
    }

    @Override
    public int getAutoLockTimeout() {
        // 返回会话锁定模式的超时时间（秒）
        long timeoutMillis = securityConfig.getAutoLockTimeoutMillisForMode();
        if (timeoutMillis == Long.MAX_VALUE) {
            return -1; // 从不锁定
        }
        return (int) (timeoutMillis / 1000); // 转换为秒
    }

    // ==================== 云端分享相关 ====================

    @Override
    public com.ttt.safevault.dto.response.ShareResponse createCloudShare(int passwordId, String toUserId,
                                                                          int expireInMinutes, SharePermission permission) {
        // 创建云端分享（仅支持用户对用户端到端加密）
        Log.d(TAG, "Creating cloud share for password ID: " + passwordId + ", to user: " + toUserId);
        // 简化实现：返回ShareResponse
        com.ttt.safevault.dto.response.ShareResponse response =
            new com.ttt.safevault.dto.response.ShareResponse();
        response.setShareId("cloud_share_" + System.currentTimeMillis());
        response.setShareToken("token_" + System.currentTimeMillis());
        return response;
    }

    @Override
    public com.ttt.safevault.dto.response.ReceivedShareResponse receiveCloudShare(String shareId) {
        // 接收云端分享
        Log.d(TAG, "Receiving cloud share: " + shareId);
        // 简化实现：返回ReceivedShareResponse
        com.ttt.safevault.dto.response.ReceivedShareResponse response =
            new com.ttt.safevault.dto.response.ReceivedShareResponse();
        response.setShareId(shareId);

        // 使用PasswordData对象设置密码数据
        com.ttt.safevault.dto.PasswordData passwordData = new com.ttt.safevault.dto.PasswordData();
        passwordData.setTitle("云端分享密码");
        passwordData.setUsername("shared_user");
        passwordData.setPassword("shared_password");
        passwordData.setUrl("");
        passwordData.setNotes("");
        response.setPasswordData(passwordData);

        response.setFromUserId("unknown_user");
        response.setFromDisplayName("Unknown User");
        response.setPermission(new SharePermission(true, true, true));
        response.setExpiresAt(0L);
        return response;
    }

    @Override
    public void revokeCloudShare(String shareId) {
        // 撤销云端分享
        Log.d(TAG, "Revoking cloud share: " + shareId);
        // 简化实现：空方法
    }

    @Override
    public void saveCloudShare(String shareId) {
        // 保存云端分享到本地
        Log.d(TAG, "Saving cloud share: " + shareId);
        // 简化实现：空方法
    }

    @Override
    public java.util.List<com.ttt.safevault.dto.response.ReceivedShareResponse> getMyCloudShares() {
        // 获取我创建的云端分享列表
        Log.d(TAG, "Getting my cloud shares");
        // 简化实现：返回空列表
        return java.util.Collections.emptyList();
    }

    @Override
    public java.util.List<com.ttt.safevault.dto.response.ReceivedShareResponse> getReceivedCloudShares() {
        // 获取我接收的云端分享列表
        Log.d(TAG, "Getting received cloud shares");
        // 简化实现：返回空列表
        return java.util.Collections.emptyList();
    }

    // ==================== 离线分享相关 ====================

    @Override
    public String createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission) {
        // 创建离线分享（版本2：密钥已嵌入）
        Log.d(TAG, "Creating offline share for password ID: " + passwordId);
        // 简化实现：返回QR码内容
        PasswordItem item = passwordManager.decryptItem(passwordId);
        if (item == null) {
            return null;
        }
        // 这里应该加密数据并返回Base64编码的QR码内容
        // 简化实现：直接返回JSON
        return "offline_share_" + passwordId;
    }

    @Override
    public PasswordItem receiveOfflineShare(String encryptedData) {
        // 接收离线分享（返回密码条目）
        Log.d(TAG, "Receiving offline share");
        // 简化实现：返回一个示例密码项
        PasswordItem item = new PasswordItem();
        item.setTitle("离线分享密码");
        item.setUsername("shared_user");
        item.setPassword("shared_password");
        item.setUrl("");
        item.setNotes("");
        return item;
    }

    @Override
    public int saveSharedPassword(String shareId) {
        // 保存分享的密码到本地
        Log.d(TAG, "Saving shared password: " + shareId);
        // 简化实现：返回新密码ID
        PasswordItem item = new PasswordItem();
        item.setTitle("分享的密码");
        item.setUsername("shared_user");
        item.setPassword("shared_password");
        item.setUrl("");
        item.setNotes("");
        return passwordManager.saveItem(item);
    }

    @Override
    public boolean revokePasswordShare(String shareId) {
        // 撤销密码分享
        Log.d(TAG, "Revoking password share: " + shareId);
        // 简化实现：总是返回成功
        return true;
    }

    @Override
    public List<PasswordShare> getMyShares() {
        // 获取我创建的分享列表
        Log.d(TAG, "Getting my shares");
        // 简化实现：返回空列表
        return java.util.Collections.emptyList();
    }

    @Override
    public List<PasswordShare> getReceivedShares() {
        // 获取我接收的分享列表
        Log.d(TAG, "Getting received shares");
        // 简化实现：返回空列表
        return java.util.Collections.emptyList();
    }

    @Override
    public PasswordShare getShareDetails(String shareId) {
        // 获取分享详情
        Log.d(TAG, "Getting share details: " + shareId);
        PasswordShare share = new PasswordShare();
        share.setShareId(shareId);
        share.setFromUserId("unknown");
        share.setExpireTime(0);
        share.setPermission(new SharePermission(true, true, true));
        return share;
    }

    @Override
    public int addPassword(String title, String username, String password, String url, String notes) {
        // 添加密码（用于保存分享的密码）
        Log.d(TAG, "Adding password: " + title);
        PasswordItem item = new PasswordItem();
        item.setTitle(title);
        item.setUsername(username);
        item.setPassword(password);
        item.setUrl(url);
        item.setNotes(notes);
        return passwordManager.saveItem(item);
    }

    // ==================== 基本密码操作 ====================

    @Override
    public boolean addPassword(PasswordItem password) {
        if (password == null) {
            return false;
        }
        Log.d(TAG, "Adding password item: " + password.getTitle());
        int result = passwordManager.saveItem(password);
        return result > 0;
    }

    @Override
    public PasswordItem getPasswordById(int passwordId) {
        Log.d(TAG, "Getting password by ID: " + passwordId);
        return passwordManager.decryptItem(passwordId);
    }

    @Override
    public List<PasswordItem> getAllPasswords() {
        Log.d(TAG, "Getting all passwords");
        return passwordManager.getAllItems();
    }

    // ==================== ECC 公钥上传 ====================

    @Override
    public boolean uploadEccPublicKey(String x25519PublicKey, String ed25519PublicKey, String keyVersion) {
        Log.d(TAG, "Uploading ECC public keys (X25519: " + x25519PublicKey.substring(0, Math.min(20, x25519PublicKey.length())) + "...)");
        return cloudAuthManager.uploadEccPublicKey(x25519PublicKey, ed25519PublicKey, keyVersion);
    }
}
