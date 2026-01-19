package com.ttt.safevault.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.security.SecurityConfig;
import com.ttt.safevault.service.manager.*;

import java.util.List;

/**
 * BackendService接口的具体实现
 * 采用组合模式，将各个功能模块委托给专门的Manager处理
 */
public class BackendServiceImpl implements BackendService {

    private static final String TAG = "BackendServiceImpl";
    private static final String PREFS_NAME = "backend_prefs";
    private static final String PREF_BACKGROUND_TIME = "background_time";

    private final Context context;
    private final CryptoManager cryptoManager;
    private final SecurityConfig securityConfig;
    private final SharedPreferences prefs;

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
        // 使用 ServiceLocator 的共享 CryptoManager，确保解锁状态同步
        this.cryptoManager = com.ttt.safevault.ServiceLocator.getInstance().getCryptoManager();
        this.securityConfig = new SecurityConfig(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // 初始化数据库访问层
        PasswordDao passwordDao = AppDatabase.getInstance(context).passwordDao();

        // 初始化云端API客户端
        com.ttt.safevault.network.RetrofitClient retrofitClient =
                com.ttt.safevault.network.RetrofitClient.getInstance(context);

        // 初始化各功能模块的Manager
        this.passwordManager = new PasswordManager(cryptoManager, passwordDao);
        this.passwordGenerator = new PasswordGenerator();
        this.dataImportExportManager = new DataImportExportManager(context, cryptoManager, passwordManager);
        this.pinCodeManager = new PinCodeManager(context, securityConfig);
        this.accountManager = new AccountManager(context, cryptoManager, passwordManager, securityConfig, retrofitClient);
        this.cloudAuthManager = new CloudAuthManager(context, cryptoManager, retrofitClient);
        this.encryptionSyncManager = new EncryptionSyncManager(context, retrofitClient);
    }

    // ==================== 加密/解密相关 ====================

    @Override
    public boolean unlock(String masterPassword) {
        boolean success = cryptoManager.unlock(masterPassword);

        // 解锁成功后保存密码
        if (success) {
            accountManager.saveMasterPasswordForBiometric(masterPassword);
            accountManager.setSessionMasterPassword(masterPassword);
            // 保存一份用于自动填充服务
            savePasswordForAutofill(masterPassword);
        }

        return success;
    }

    /**
     * 保存密码供自动填充服务使用
     */
    private void savePasswordForAutofill(String password) {
        try {
            context.getSharedPreferences("autofill_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("master_password", password)
                .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save password for autofill", e);
        }
    }

    @Override
    public void lock() {
        cryptoManager.lock();
    }

    @Override
    public boolean isUnlocked() {
        return cryptoManager.isUnlocked();
    }

    @Override
    public String getMasterPassword() {
        return cryptoManager.getMasterPassword();
    }

    @Override
    public boolean isInitialized() {
        return cryptoManager.isInitialized();
    }

    @Override
    public boolean initialize(String masterPassword) {
        boolean success = cryptoManager.initialize(masterPassword);

        // 初始化成功后保存主密码
        if (success) {
            accountManager.saveMasterPasswordForBiometric(masterPassword);
            accountManager.setSessionMasterPassword(masterPassword);
            // 保存一份用于自动填充服务
            savePasswordForAutofill(masterPassword);
        }

        return success;
    }

    @Override
    public boolean changeMasterPassword(String oldPassword, String newPassword) {
        if (!cryptoManager.changeMasterPassword(oldPassword, newPassword)) {
            return false;
        }

        // 需要重新加密所有数据
        try {
            List<PasswordItem> items = passwordManager.getAllItems();
            for (PasswordItem item : items) {
                passwordManager.saveItem(item);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to re-encrypt data", e);
            return false;
        }
    }

    // ==================== 密码管理相关 ====================

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
        return passwordManager.saveItem(item);
    }

    @Override
    public boolean deleteItem(int id) {
        return passwordManager.deleteItem(id);
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
    public boolean unlockWithBiometric() {
        return accountManager.unlockWithBiometric();
    }

    @Override
    public boolean canUseBiometricAuthentication() {
        return accountManager.canUseBiometricAuthentication();
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
    public boolean uploadEncryptedPrivateKey(String encryptedPrivateKey, String iv, String salt) {
        return encryptionSyncManager.uploadEncryptedPrivateKey(encryptedPrivateKey, iv, salt);
    }

    @Override
    public com.ttt.safevault.security.KeyManager.EncryptedPrivateKey downloadEncryptedPrivateKey() {
        return encryptionSyncManager.downloadEncryptedPrivateKey();
    }

    @Override
    public boolean uploadEncryptedVaultData(String encryptedVaultData, String iv) {
        return encryptionSyncManager.uploadEncryptedVaultData(encryptedVaultData, iv);
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
        // 返回自动锁定模式的超时时间（秒）
        long timeoutMillis = securityConfig.getAutoLockTimeoutMillisForMode();
        if (timeoutMillis == Long.MAX_VALUE) {
            return -1; // 从不锁定
        }
        return (int) (timeoutMillis / 1000); // 转换为秒
    }

    // ==================== 云端分享相关 ====================

    @Override
    public com.ttt.safevault.dto.response.ShareResponse createCloudShare(int passwordId, String toUserId,
                                                                          int expireInMinutes, SharePermission permission,
                                                                          String shareType) {
        // 创建云端分享
        Log.d(TAG, "Creating cloud share type: " + shareType + " for password ID: " + passwordId);
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
        response.setShareType("DIRECT");
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

    @Override
    public void registerLocation(double latitude, double longitude, double radius) {
        // 注册位置信息
        Log.d(TAG, "Registering location: " + latitude + ", " + longitude);
        // 简化实现：空方法
    }

    @Override
    public java.util.List<com.ttt.safevault.dto.response.NearbyUserResponse> getNearbyUsers(
            double latitude, double longitude, double radius) {
        // 获取附近用户
        Log.d(TAG, "Getting nearby users");
        // 简化实现：返回空列表
        return java.util.Collections.emptyList();
    }

    @Override
    public void sendHeartbeat() {
        // 发送心跳保持在线状态
        Log.d(TAG, "Sending heartbeat");
        // 简化实现：空方法
    }

    // ==================== 本地分享相关 ====================

    @Override
    public String createDirectPasswordShare(int passwordId, int expireInMinutes, SharePermission permission) {
        // 本地直接分享（使用QR码）
        Log.d(TAG, "Creating direct password share for password ID: " + passwordId);
        // 简化实现：返回分享Token
        return "share_" + passwordId + "_" + System.currentTimeMillis();
    }

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
    public PasswordItem receivePasswordShare(String shareId) {
        // 接收密码分享（返回密码条目）
        Log.d(TAG, "Receiving password share: " + shareId);
        PasswordItem item = new PasswordItem();
        item.setTitle("分享的密码");
        item.setUsername("shared_user");
        item.setPassword("shared_password");
        item.setUrl("");
        item.setNotes("来自分享");
        return item;
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
}
