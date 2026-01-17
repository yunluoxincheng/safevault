package com.ttt.safevault.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.EncryptedPasswordEntity;
import com.ttt.safevault.data.PasswordDao;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.model.ShareStatus;
import com.ttt.safevault.security.BiometricKeyManager;
import com.ttt.safevault.security.SecurityConfig;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.reactivex.rxjava3.core.Observable;

/**
 * BackendService接口的具体实现
 * 提供所有后端功能：加密存储、密码生成等
 */
public class BackendServiceImpl implements BackendService {

    private static final String TAG = "BackendServiceImpl";
    private static final String PREFS_NAME = "backend_prefs";
    private static final String PREF_BACKGROUND_TIME = "background_time";
    private static final String PREF_LAST_BACKUP = "last_backup";
    private static final String PREF_BIOMETRIC_ENCRYPTED_PASSWORD = "biometric_encrypted_password";
    private static final String PREF_BIOMETRIC_IV = "biometric_iv";
    private static final String PREF_USER_ID = "user_id";

    // 密码生成字符集
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBERS = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

    private final Context context;
    private final CryptoManager cryptoManager;
    private final PasswordDao passwordDao;
    private final SecurityConfig securityConfig;
    private final SharedPreferences prefs;
    private final SecureRandom secureRandom;
    private BiometricKeyManager biometricKeyManager;

    // 分享功能相关的内存存储（简化实现，生产环境应使用数据库）
    private final Map<String, PasswordShare> sharesMap = new ConcurrentHashMap<>();

    // 云端服务API客户端
    private com.ttt.safevault.network.RetrofitClient retrofitClient;
    private com.ttt.safevault.network.TokenManager tokenManager;

    public BackendServiceImpl(@NonNull Context context) {
        this.context = context.getApplicationContext();
        // 使用 ServiceLocator 的共享 CryptoManager，确保解锁状态同步
        this.cryptoManager = com.ttt.safevault.ServiceLocator.getInstance().getCryptoManager();
        this.passwordDao = AppDatabase.getInstance(context).passwordDao();
        this.securityConfig = new SecurityConfig(context);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.secureRandom = new SecureRandom();
        
        // 初始化云端API客户端
        this.retrofitClient = com.ttt.safevault.network.RetrofitClient.getInstance(context);
        this.tokenManager = this.retrofitClient.getTokenManager();
        
        // 初始化生物识别密钥管理器
        try {
            this.biometricKeyManager = BiometricKeyManager.getInstance();
            // 初始化生物识别密钥（如果不存在的话）
            this.biometricKeyManager.initializeKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize biometric key manager", e);
            this.biometricKeyManager = null;
        }
    }

    @Override
    public boolean unlock(String masterPassword) {
        boolean success = cryptoManager.unlock(masterPassword);
        
        // 解锁成功后保存密码
        if (success) {
            saveMasterPasswordForBiometric(masterPassword);
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
    public PasswordItem decryptItem(int id) {
        EncryptedPasswordEntity entity = passwordDao.getById(id);
        if (entity == null) {
            return null;
        }
        return decryptEntity(entity);
    }

    @Override
    public List<PasswordItem> search(String query) {
        List<PasswordItem> results = new ArrayList<>();
        List<PasswordItem> allItems = getAllItems();

        if (query == null || query.trim().isEmpty()) {
            return allItems;
        }

        String lowerQuery = query.toLowerCase().trim();
        for (PasswordItem item : allItems) {
            if (matchesQuery(item, lowerQuery)) {
                results.add(item);
            }
        }

        return results;
    }

    private boolean matchesQuery(PasswordItem item, String query) {
        return (item.getTitle() != null && item.getTitle().toLowerCase().contains(query)) ||
               (item.getUsername() != null && item.getUsername().toLowerCase().contains(query)) ||
               (item.getUrl() != null && item.getUrl().toLowerCase().contains(query)) ||
               (item.getNotes() != null && item.getNotes().toLowerCase().contains(query));
    }

    @Override
    public int saveItem(PasswordItem item) {
        try {
            EncryptedPasswordEntity entity = encryptItem(item);

            if (item.getId() > 0) {
                // 更新现有记录
                entity.setId(item.getId());
                passwordDao.update(entity);
                return item.getId();
            } else {
                // 插入新记录
                long newId = passwordDao.insert(entity);
                return (int) newId;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save item", e);
            return -1;
        }
    }

    @Override
    public boolean deleteItem(int id) {
        try {
            return passwordDao.deleteById(id) > 0;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete item", e);
            return false;
        }
    }

    @Override
    public String generatePassword(int length, boolean symbols) {
        return generatePassword(length, true, true, true, symbols);
    }

    @Override
    public String generatePassword(int length, boolean useUppercase, boolean useLowercase,
                                   boolean useNumbers, boolean useSymbols) {
        StringBuilder charPool = new StringBuilder();

        if (useUppercase) charPool.append(UPPERCASE);
        if (useLowercase) charPool.append(LOWERCASE);
        if (useNumbers) charPool.append(NUMBERS);
        if (useSymbols) charPool.append(SYMBOLS);

        // 默认至少包含小写字母和数字
        if (charPool.length() == 0) {
            charPool.append(LOWERCASE).append(NUMBERS);
        }

        StringBuilder password = new StringBuilder(length);
        String pool = charPool.toString();

        // 确保密码包含所选的每种字符类型
        List<String> requiredChars = new ArrayList<>();
        if (useUppercase) requiredChars.add(UPPERCASE);
        if (useLowercase) requiredChars.add(LOWERCASE);
        if (useNumbers) requiredChars.add(NUMBERS);
        if (useSymbols) requiredChars.add(SYMBOLS);

        // 先添加每种类型至少一个字符
        for (String chars : requiredChars) {
            if (password.length() < length) {
                password.append(chars.charAt(secureRandom.nextInt(chars.length())));
            }
        }

        // 填充剩余长度
        while (password.length() < length) {
            password.append(pool.charAt(secureRandom.nextInt(pool.length())));
        }

        // 打乱顺序
        char[] passwordArray = password.toString().toCharArray();
        for (int i = passwordArray.length - 1; i > 0; i--) {
            int j = secureRandom.nextInt(i + 1);
            char temp = passwordArray[i];
            passwordArray[i] = passwordArray[j];
            passwordArray[j] = temp;
        }

        return new String(passwordArray);
    }

    @Override
    public List<PasswordItem> getAllItems() {
        List<PasswordItem> items = new ArrayList<>();
        try {
            Log.d(TAG, "getAllItems: isUnlocked=" + cryptoManager.isUnlocked());
            List<EncryptedPasswordEntity> entities = passwordDao.getAll();
            Log.d(TAG, "getAllItems: found " + entities.size() + " entities in database");
            
            for (EncryptedPasswordEntity entity : entities) {
                PasswordItem item = decryptEntity(entity);
                if (item != null) {
                    items.add(item);
                } else {
                    Log.w(TAG, "getAllItems: failed to decrypt entity id=" + entity.getId());
                }
            }
            Log.d(TAG, "getAllItems: successfully decrypted " + items.size() + " items");
        } catch (Exception e) {
            Log.e(TAG, "Failed to get all items", e);
        }
        return items;
    }

    @Override
    public boolean isUnlocked() {
        return cryptoManager.isUnlocked();
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
            saveMasterPasswordForBiometric(masterPassword);
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
            List<PasswordItem> items = getAllItems();
            for (PasswordItem item : items) {
                saveItem(item);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to re-encrypt data", e);
            return false;
        }
    }

    @Override
    public boolean exportData(String exportPath) {
        try {
            // 1. 获取所有密码数据
            List<PasswordItem> items = getAllItems();

            if (items.isEmpty()) {
                Log.w(TAG, "No data to export");
                return false;
            }

            // 2. 序列化为 JSON
            String jsonData = com.ttt.safevault.utils.BackupJsonUtil.serializePasswordList(items);

            // 3. 使用主密码加密数据
            String masterPassword = cryptoManager.getMasterPassword();
            com.ttt.safevault.utils.BackupCryptoUtil.EncryptionResult encryptionResult =
                    com.ttt.safevault.utils.BackupCryptoUtil.encrypt(jsonData, masterPassword);

            // 4. 构建导出数据结构
            String deviceId = com.ttt.safevault.security.KeyManager.getInstance(context).getDeviceId();
            String appVersion = getAppVersion();

            com.ttt.safevault.dto.ExportData exportData =
                    com.ttt.safevault.utils.BackupJsonUtil.buildExportData(
                            encryptionResult.getEncryptedData(),
                            encryptionResult.getIv(),
                            encryptionResult.getSalt(),
                            items.size(),
                            deviceId,
                            appVersion
                    );

            // 5. 序列化为最终 JSON
            String finalJson = com.ttt.safevault.utils.BackupJsonUtil.serializeExportData(exportData);

            // 6. 写入文件
            java.io.FileWriter writer = new java.io.FileWriter(exportPath);
            writer.write(finalJson);
            writer.close();

            Log.d(TAG, "Data exported successfully to: " + exportPath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to export data", e);
            return false;
        }
    }

    @Override
    public boolean importData(String importPath) {
        try {
            // 1. 读取文件
            java.io.File file = new java.io.File(importPath);
            if (!file.exists()) {
                Log.e(TAG, "Import file does not exist: " + importPath);
                return false;
            }

            java.io.FileReader reader = new java.io.FileReader(file);
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                content.append(buffer, 0, length);
            }
            reader.close();

            // 2. 反序列化导出数据
            com.ttt.safevault.dto.ExportData exportData =
                    com.ttt.safevault.utils.BackupJsonUtil.deserializeExportData(content.toString());

            // 3. 验证数据
            com.ttt.safevault.utils.BackupValidationUtil.ValidationResult validationResult =
                    com.ttt.safevault.utils.BackupValidationUtil.validateExportData(exportData);

            if (!validationResult.isValid()) {
                Log.e(TAG, "Invalid backup file: " + validationResult.getErrorMessage());
                return false;
            }

            // 4. 解密数据
            String masterPassword = cryptoManager.getMasterPassword();
            com.ttt.safevault.dto.ExportData.DataContainer dataContainer = exportData.getData();

            String decryptedJson = com.ttt.safevault.utils.BackupCryptoUtil.decrypt(
                    dataContainer.getEncryptedData(),
                    masterPassword,
                    dataContainer.getSalt(),
                    dataContainer.getIv()
            );

            // 5. 反序列化密码列表
            List<PasswordItem> items = com.ttt.safevault.utils.BackupJsonUtil.deserializePasswordList(decryptedJson);

            // 6. 清空现有数据并导入新数据
            // 注意：这里简单实现，直接替换所有数据
            for (PasswordItem item : items) {
                // 使用负数ID表示新导入的条目
                item.setId(-1);
                saveItem(item);
            }

            Log.d(TAG, "Data imported successfully from: " + importPath + ", items: " + items.size());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to import data", e);
            return false;
        }
    }

    /**
     * 获取应用版本
     */
    private String getAppVersion() {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }

    @Override
    public AppStats getStats() {
        try {
            List<PasswordItem> items = getAllItems();
            int totalItems = items.size();
            int weakPasswords = 0;
            int duplicatePasswords = 0;

            Set<String> passwordSet = new HashSet<>();
            for (PasswordItem item : items) {
                String pwd = item.getPassword();
                if (pwd != null) {
                    // 检查弱密码
                    if (isWeakPassword(pwd)) {
                        weakPasswords++;
                    }
                    // 检查重复密码
                    if (!passwordSet.add(pwd)) {
                        duplicatePasswords++;
                    }
                }
            }

            long lastBackup = prefs.getLong(PREF_LAST_BACKUP, 0);
            int daysSinceBackup = lastBackup > 0 ?
                    (int) ((System.currentTimeMillis() - lastBackup) / (1000 * 60 * 60 * 24)) : -1;

            return new AppStats(totalItems, weakPasswords, duplicatePasswords, daysSinceBackup);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get stats", e);
            return new AppStats(0, 0, 0, -1);
        }
    }

    private boolean isWeakPassword(String password) {
        if (password == null || password.length() < 8) {
            return true;
        }

        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasSymbol = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");

        int types = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasNumber ? 1 : 0) + (hasSymbol ? 1 : 0);
        return types < 3;
    }

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

    /**
     * 加密PasswordItem为EncryptedPasswordEntity
     * 每个字段使用独立的IV，IV与密文拼接存储
     */
    private EncryptedPasswordEntity encryptItem(PasswordItem item) {
        EncryptedPasswordEntity entity = new EncryptedPasswordEntity();

        // 每个字段独立加密，IV拼接到密文前
        entity.setEncryptedTitle(encryptField(item.getTitle()));
        entity.setEncryptedUsername(encryptField(item.getUsername()));
        entity.setEncryptedPassword(encryptField(item.getPassword()));
        entity.setEncryptedUrl(encryptField(item.getUrl()));
        entity.setEncryptedNotes(encryptField(item.getNotes()));

        entity.setUpdatedAt(item.getUpdatedAt() > 0 ? item.getUpdatedAt() : System.currentTimeMillis());

        return entity;
    }

    /**
     * 加密单个字段，返回格式: iv:ciphertext
     */
    @Nullable
    private String encryptField(@Nullable String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        CryptoManager.EncryptedData data = cryptoManager.encrypt(plaintext);
        if (data == null) {
            return null;
        }
        return data.iv + ":" + data.ciphertext;
    }

    /**
     * 解密EncryptedPasswordEntity为PasswordItem
     */
    @Nullable
    private PasswordItem decryptEntity(EncryptedPasswordEntity entity) {
        try {
            PasswordItem item = new PasswordItem();
            item.setId(entity.getId());
            item.setTitle(decryptField(entity.getEncryptedTitle()));
            item.setUsername(decryptField(entity.getEncryptedUsername()));
            item.setPassword(decryptField(entity.getEncryptedPassword()));
            item.setUrl(decryptField(entity.getEncryptedUrl()));
            item.setNotes(decryptField(entity.getEncryptedNotes()));
            item.setUpdatedAt(entity.getUpdatedAt());

            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt entity", e);
            return null;
        }
    }

    /**
     * 解密单个字段，输入格式: iv:ciphertext
     */
    @Nullable
    private String decryptField(@Nullable String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }
        String[] parts = encrypted.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return cryptoManager.decrypt(parts[1], parts[0]);
    }

    // ========== 新增：账户操作接口实现 ==========

    @Override
    public boolean setPinCode(String pinCode) {
        // 验证PIN码格式（4-6位数字）
        if (pinCode == null || !pinCode.matches("\\d{4,6}")) {
            Log.e(TAG, "Invalid PIN code format");
            return false;
        }

        try {
            // 使用AndroidKeyStore加密存储PIN码
            // 注意：这里使用简单的哈希存储，生产环境应该使用更安全的方式
            String hashedPin = hashPinCode(pinCode);

            prefs.edit()
                    .putString("pin_code", hashedPin)
                    .apply();

            // 标记PIN码已启用
            securityConfig.setPinCodeEnabled(true);

            Log.d(TAG, "PIN code set successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to set PIN code", e);
            return false;
        }
    }

    @Override
    public boolean verifyPinCode(String pinCode) {
        String storedHashedPin = prefs.getString("pin_code", null);
        if (storedHashedPin == null) {
            return false;
        }

        String hashedInput = hashPinCode(pinCode);
        return storedHashedPin.equals(hashedInput);
    }

    @Override
    public boolean clearPinCode() {
        try {
            prefs.edit()
                    .remove("pin_code")
                    .apply();

            // 标记PIN码已禁用
            securityConfig.setPinCodeEnabled(false);

            Log.d(TAG, "PIN code cleared successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear PIN code", e);
            return false;
        }
    }

    /**
     * 对PIN码进行哈希（使用SHA-256）
     * 生产环境应该使用加盐哈希或其他更安全的方式
     */
    private String hashPinCode(String pinCode) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            // 添加固定的盐值（实际应用中应该使用随机盐）
            String saltedPin = pinCode + "SafeVaultPinSalt2024";
            byte[] hash = digest.digest(saltedPin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "Failed to hash PIN code", e);
            throw new RuntimeException("PIN码哈希失败", e);
        }
    }

    @Override
    public boolean isPinCodeEnabled() {
        return securityConfig.isPinCodeEnabled();
    }

    @Override
    public void logout() {
        lock();
        // 清除内存中的敏感数据
    }

    @Override
    public boolean deleteAccount() {
        try {
            Log.d(TAG, "Starting account deletion...");

            // 1. 调用后端API删除云端数据
            String token = tokenManager.getAccessToken();
            if (token != null && !token.isEmpty()) {
                try {
                    com.ttt.safevault.dto.response.DeleteAccountResponse response =
                        retrofitClient.getAuthServiceApi()
                            .deleteAccount("Bearer " + token)
                            .blockingFirst();

                    if (response != null && response.isSuccess()) {
                        Log.d(TAG, "Cloud account deleted successfully");
                    } else {
                        Log.w(TAG, "Cloud account deletion failed: " +
                            (response != null ? response.getMessage() : "Unknown error"));
                        // 继续删除本地数据
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to delete cloud account", e);
                    // 继续删除本地数据
                }
            } else {
                Log.w(TAG, "No access token, skipping cloud deletion");
            }

            // 2. 删除所有本地密码数据
            List<PasswordItem> items = getAllItems();
            for (PasswordItem item : items) {
                deleteItem(item.getId());
            }
            Log.d(TAG, "Local password data deleted");

            // 3. 清除加密密钥和生物识别数据
            cryptoManager.lock();
            clearBiometricData();
            Log.d(TAG, "Encryption keys cleared");

            // 4. 清除所有设置和Token
            securityConfig.clear();
            tokenManager.clearTokens();
            prefs.edit().clear().apply();
            Log.d(TAG, "Settings and tokens cleared");

            Log.d(TAG, "Account deletion completed successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete account", e);
            return false;
        }
    }

    @Override
    public boolean unlockWithBiometric() {
        // 检查生物识别是否启用
        if (!securityConfig.isBiometricEnabled()) {
            Log.e(TAG, "Biometric not enabled");
            return false;
        }
        
        // 检查是否已初始化
        if (!cryptoManager.isInitialized()) {
            Log.e(TAG, "Crypto manager not initialized");
            return false;
        }
        
        // 获取保存的加密主密码
        String masterPassword = getMasterPasswordForBiometric();
        if (masterPassword == null) {
            Log.e(TAG, "No master password stored for biometric unlock");
            return false;
        }
        
        // 使用主密码解锁
        return cryptoManager.unlock(masterPassword);
    }

    @Override
    public boolean canUseBiometricAuthentication() {
        return securityConfig.isBiometricEnabled() && hasMasterPasswordForBiometric();
    }

    /**
     * 保存主密码用于生物识别解锁
     */
    private void saveMasterPasswordForBiometric(String masterPassword) {
        if (biometricKeyManager == null) {
            Log.e(TAG, "BiometricKeyManager not initialized");
            return;
        }
        
        try {
            // 获取加密Cipher
            javax.crypto.Cipher cipher = biometricKeyManager.getEncryptCipher();
            
            // 加密主密码
            byte[] encrypted = cipher.doFinal(masterPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            
            // 保存加密数据
            prefs.edit()
                .putString(PREF_BIOMETRIC_ENCRYPTED_PASSWORD, 
                    android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
                .putString(PREF_BIOMETRIC_IV, 
                    android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
                .apply();
            
            Log.d(TAG, "Master password saved for biometric unlock");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save master password for biometric", e);
        }
    }

    /**
     * 获取用于生物识别解锁的主密码
     */
    private String getMasterPasswordForBiometric() {
        if (biometricKeyManager == null) {
            Log.e(TAG, "BiometricKeyManager not initialized");
            return null;
        }
        
        try {
            String encryptedPassword = prefs.getString(PREF_BIOMETRIC_ENCRYPTED_PASSWORD, null);
            String ivString = prefs.getString(PREF_BIOMETRIC_IV, null);
            
            if (encryptedPassword == null || ivString == null) {
                Log.e(TAG, "No encrypted password or IV found");
                return null;
            }
            
            byte[] encrypted = android.util.Base64.decode(encryptedPassword, android.util.Base64.NO_WRAP);
            byte[] iv = android.util.Base64.decode(ivString, android.util.Base64.NO_WRAP);
            
            // 获取解密Cipher
            javax.crypto.Cipher cipher = biometricKeyManager.getDecryptCipher(iv);
            
            // 解密主密码
            byte[] decrypted = cipher.doFinal(encrypted);
            
            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt master password for biometric", e);
            // 解密失败，可能是密钥已重建，清除旧数据
            clearBiometricData();
            return null;
        }
    }
    
    /**
     * 清除生物识别加密数据
     */
    private void clearBiometricData() {
        prefs.edit()
            .remove(PREF_BIOMETRIC_ENCRYPTED_PASSWORD)
            .remove(PREF_BIOMETRIC_IV)
            .apply();
        Log.d(TAG, "Biometric data cleared");
    }

    /**
     * 检查是否有保存的生物识别密码
     */
    private boolean hasMasterPasswordForBiometric() {
        return prefs.contains(PREF_BIOMETRIC_ENCRYPTED_PASSWORD) && 
               prefs.contains(PREF_BIOMETRIC_IV);
    }

    // ========== 辅助方法 ==========

    /**
     * 获取当前用户ID
     */
    private String getCurrentUserId() {
        String userId = prefs.getString(PREF_USER_ID, null);
        if (userId == null) {
            // 创建新用户
            userId = "user_" + UUID.randomUUID().toString();
            prefs.edit().putString(PREF_USER_ID, userId).apply();
        }
        return userId;
    }

    // ========== 新增：分享管理接口实现 ==========

    @Override
    public String createPasswordShare(int passwordId, String toUserId,
                                     int expireInMinutes, SharePermission permission) {
        try {
            // 验证密码是否存在
            PasswordItem item = decryptItem(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }
            
            // 生成分享ID
            String shareId = "share_" + UUID.randomUUID().toString();
            
            // 创建分享对象
            PasswordShare share = new PasswordShare();
            share.setShareId(shareId);
            share.setPasswordId(passwordId);
            share.setFromUserId(getCurrentUserId());
            share.setToUserId(toUserId);
            share.setCreatedAt(System.currentTimeMillis());
            
            // 计算过期时间
            if (expireInMinutes > 0) {
                long expireTime = System.currentTimeMillis() + (expireInMinutes * 60 * 1000L);
                share.setExpireTime(expireTime);
            } else {
                share.setExpireTime(0);
            }
            
            share.setPermission(permission);
            share.setStatus(ShareStatus.ACTIVE);
            
            // 加密密码数据（简化实现，直接存储JSON）
            String encryptedData = encryptPasswordForShare(item);
            share.setEncryptedData(encryptedData);
            
            // 保存分享
            sharesMap.put(shareId, share);
            Log.d(TAG, "Share created: " + shareId);
            
            return shareId;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create share", e);
            return null;
        }
    }

    @Override
    public String createDirectPasswordShare(int passwordId, int expireInMinutes,
                                           SharePermission permission) {
        // 直接分享与toUserId为null的普通分享相同
        return createPasswordShare(passwordId, null, expireInMinutes, permission);
    }

    @Override
    public PasswordItem receivePasswordShare(String shareId) {
        try {
            PasswordShare share = getShareDetails(shareId);
            if (share == null) {
                Log.e(TAG, "Share not found: " + shareId);
                return null;
            }
            
            // 验证分享状态
            if (!share.isAvailable()) {
                Log.e(TAG, "Share not available: " + shareId);
                return null;
            }
            
            // 解密密码数据
            PasswordItem item = decryptPasswordFromShare(share.getEncryptedData());
            
            // 更新分享状态
            share.setStatus(ShareStatus.ACCEPTED);
            
            Log.d(TAG, "Share received: " + shareId);
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to receive share", e);
            return null;
        }
    }

    @Override
    public boolean revokePasswordShare(String shareId) {
        try {
            PasswordShare share = sharesMap.get(shareId);
            if (share == null) {
                return false;
            }
            
            // 验证所有权
            if (!share.getFromUserId().equals(getCurrentUserId())) {
                Log.e(TAG, "Not authorized to revoke share: " + shareId);
                return false;
            }
            
            // 验证是否可撤销
            if (!share.getPermission().isRevocable()) {
                Log.e(TAG, "Share is not revocable: " + shareId);
                return false;
            }
            
            // 更新状态
            share.setStatus(ShareStatus.REVOKED);
            Log.d(TAG, "Share revoked: " + shareId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to revoke share", e);
            return false;
        }
    }

    @Override
    public List<PasswordShare> getMyShares() {
        List<PasswordShare> myShares = new ArrayList<>();
        String currentUserId = getCurrentUserId();
        
        for (PasswordShare share : sharesMap.values()) {
            if (currentUserId.equals(share.getFromUserId())) {
                myShares.add(share);
            }
        }
        
        return myShares;
    }

    @Override
    public List<PasswordShare> getReceivedShares() {
        List<PasswordShare> receivedShares = new ArrayList<>();
        String currentUserId = getCurrentUserId();
        
        for (PasswordShare share : sharesMap.values()) {
            if (currentUserId.equals(share.getToUserId()) || share.getToUserId() == null) {
                receivedShares.add(share);
            }
        }
        
        return receivedShares;
    }

    @Override
    public int saveSharedPassword(String shareId) {
        try {
            PasswordItem item = receivePasswordShare(shareId);
            if (item == null) {
                return -1;
            }
            
            // 保存到密码库
            return saveItem(item);
        } catch (Exception e) {
            Log.e(TAG, "Failed to save shared password", e);
            return -1;
        }
    }

    @Override
    public PasswordShare getShareDetails(String shareId) {
        return sharesMap.get(shareId);
    }

    // ========== 新增：加密传输接口实现 ==========

    @Override
    public String generateShareData(PasswordItem passwordItem,
                                   String receiverPublicKey,
                                   SharePermission permission) {
        try {
            // 简化实现：直接序列化为JSON（生产环境应使用真正的加密）
            return encryptPasswordForShare(passwordItem);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate share data", e);
            return null;
        }
    }

    @Override
    public PasswordItem parseShareData(String shareData) {
        try {
            // 简化实现：从JSON解析（生产环境应使用真正的解密）
            return decryptPasswordFromShare(shareData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse share data", e);
            return null;
        }
    }

    /**
     * 加密密码用于分享（简化实现）
     */
    private String encryptPasswordForShare(PasswordItem item) {
        // 简化实现：返回JSON字符串
        // 生产环境应使用真正的端到端加密
        return "{\"title\":\"" + (item.getTitle() != null ? item.getTitle() : "") + 
               "\",\"username\":\"" + (item.getUsername() != null ? item.getUsername() : "") + 
               "\",\"password\":\"" + (item.getPassword() != null ? item.getPassword() : "") + 
               "\",\"url\":\"" + (item.getUrl() != null ? item.getUrl() : "") + 
               "\",\"notes\":\"" + (item.getNotes() != null ? item.getNotes() : "") + "\"}";
    }

    /**
     * 从分享数据解密密码（简化实现）
     */
    private PasswordItem decryptPasswordFromShare(String encryptedData) {
        // 简化实现：从JSON解析
        // 生产环境应使用真正的解密
        try {
            PasswordItem item = new PasswordItem();
            // 简单的JSON解析（生产环境应使用JSON库）
            if (encryptedData.contains("\"title\":\"")) {
                String title = extractJsonValue(encryptedData, "title");
                String username = extractJsonValue(encryptedData, "username");
                String password = extractJsonValue(encryptedData, "password");
                String url = extractJsonValue(encryptedData, "url");
                String notes = extractJsonValue(encryptedData, "notes");
                
                item.setTitle(title);
                item.setUsername(username);
                item.setPassword(password);
                item.setUrl(url);
                item.setNotes(notes);
            }
            return item;
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt password from share", e);
            return null;
        }
    }

    /**
     * 从JSON字符串提取值（简化实现）
     */
    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":\"";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) {
                return "";
            }
            startIndex += searchKey.length();
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) {
                return "";
            }
            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            return "";
        }
    }

    // ========== 新增：离线分享接口实现 ==========

    @Override
    public String createOfflineShare(int passwordId,
                                    int expireInMinutes, SharePermission permission) {
        try {
            // 获取密码数据
            PasswordItem item = decryptItem(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }

            // 使用OfflineShareUtils创建离线分享（版本2：嵌入密钥）
            com.ttt.safevault.utils.OfflineShareUtils.OfflineSharePacket packet =
                com.ttt.safevault.utils.OfflineShareUtils.createOfflineShare(
                    item, expireInMinutes, permission
                );

            if (packet == null) {
                Log.e(TAG, "Failed to create offline share");
                return null;
            }

            Log.d(TAG, "Offline share created successfully");
            return packet.qrContent;

        } catch (Exception e) {
            Log.e(TAG, "Failed to create offline share", e);
            return null;
        }
    }

    @Override
    public PasswordItem receiveOfflineShare(String qrContent) {
        try {
            // 使用OfflineShareUtils解析离线分享
            PasswordItem item = com.ttt.safevault.utils.OfflineShareUtils.parseOfflineShare(
                qrContent
            );

            if (item == null) {
                Log.e(TAG, "Failed to parse offline share");
                return null;
            }

            Log.d(TAG, "Offline share received successfully");
            return item;

        } catch (Exception e) {
            Log.e(TAG, "Failed to receive offline share", e);
            return null;
        }
    }

    // ========== 云端分享接口实现 ==========

    @Override
    public com.ttt.safevault.dto.response.AuthResponse register(String username, String password, String displayName) {
        try {
            // 获取设备ID和公钥
            String deviceId = com.ttt.safevault.security.KeyManager.getInstance(context).getDeviceId();
            String publicKey = com.ttt.safevault.security.KeyManager.getInstance(context).getPublicKey();

            com.ttt.safevault.dto.request.RegisterRequest request = new com.ttt.safevault.dto.request.RegisterRequest(
                deviceId, username, displayName, publicKey
            );

            com.ttt.safevault.dto.response.AuthResponse response = retrofitClient.getAuthServiceApi()
                .register(request)
                .blockingFirst();

            if (response != null) {
                tokenManager.saveTokens(response);
                Log.d(TAG, "User registered successfully: " + response.getUserId());
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register", e);
            return null;
        }
    }

    @Override
    public com.ttt.safevault.dto.response.AuthResponse login(String username, String password) {
        try {
            // 获取保存的userId和设备ID
            String userId = tokenManager.getUserId();
            String deviceId = com.ttt.safevault.security.KeyManager.getInstance(context).getDeviceId();

            if (userId == null) {
                Log.e(TAG, "No userId found, please register first");
                return null;
            }

            // 生成时间戳和签名
            long timestamp = System.currentTimeMillis();
            String signature = generateSignature(userId, deviceId, timestamp);

            com.ttt.safevault.dto.request.LoginRequest request = new com.ttt.safevault.dto.request.LoginRequest(
                userId, deviceId, signature, timestamp
            );

            com.ttt.safevault.dto.response.AuthResponse response = retrofitClient.getAuthServiceApi()
                .login(request)
                .blockingFirst();

            if (response != null) {
                tokenManager.saveTokens(response);
                Log.d(TAG, "User logged in successfully: " + response.getUserId());
            }

            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to login", e);
            return null;
        }
    }

    /**
     * 生成签名（简化版本）
     */
    private String generateSignature(String userId, String deviceId, long timestamp) {
        try {
            String data = userId + deviceId + timestamp;
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate signature", e);
            return "";
        }
    }

    @Override
    public com.ttt.safevault.dto.response.AuthResponse refreshToken(String refreshToken) {
        try {
            com.ttt.safevault.dto.response.AuthResponse response = retrofitClient.getAuthServiceApi()
                .refreshToken("Bearer " + refreshToken)
                .blockingFirst();
            
            if (response != null) {
                tokenManager.saveTokens(response);
                Log.d(TAG, "Token refreshed successfully");
            }
            
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh token", e);
            return null;
        }
    }

    @Override
    public com.ttt.safevault.dto.response.ShareResponse createCloudShare(int passwordId, String toUserId,
                                                                          int expireInMinutes, SharePermission permission,
                                                                          String shareType) {
        try {
            // 获取密码数据
            PasswordItem item = decryptItem(passwordId);
            if (item == null) {
                Log.e(TAG, "Password not found: " + passwordId);
                return null;
            }
            
            // 构建请求
            com.ttt.safevault.dto.request.CreateShareRequest request = new com.ttt.safevault.dto.request.CreateShareRequest();
            request.setPasswordId(String.valueOf(passwordId));
            request.setTitle(item.getTitle());
            request.setUsername(item.getUsername());
            request.setEncryptedPassword(item.getPassword()); // 密码已加密
            request.setUrl(item.getUrl());
            request.setNotes(item.getNotes());
            request.setToUserId(toUserId);
            request.setExpireInMinutes(expireInMinutes);
            request.setPermission(permission);
            request.setShareType(shareType);
            
            // 调用API
            com.ttt.safevault.dto.response.ShareResponse response = retrofitClient.getShareServiceApi()
                .createShare(request)
                .blockingFirst();
            
            Log.d(TAG, "Cloud share created: " + response.getShareId());
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create cloud share", e);
            return null;
        }
    }

    @Override
    public com.ttt.safevault.dto.response.ReceivedShareResponse receiveCloudShare(String shareId) {
        try {
            com.ttt.safevault.dto.response.ReceivedShareResponse response = retrofitClient.getShareServiceApi()
                .receiveShare(shareId)
                .blockingFirst();
            
            Log.d(TAG, "Cloud share received: " + shareId);
            return response;
        } catch (Exception e) {
            Log.e(TAG, "Failed to receive cloud share", e);
            return null;
        }
    }

    @Override
    public void revokeCloudShare(String shareId) {
        try {
            retrofitClient.getShareServiceApi()
                .revokeShare(shareId)
                .blockingSubscribe();
            
            Log.d(TAG, "Cloud share revoked: " + shareId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to revoke cloud share", e);
        }
    }

    @Override
    public void saveCloudShare(String shareId) {
        try {
            // 先获取分享数据
            com.ttt.safevault.dto.response.ReceivedShareResponse response =
                retrofitClient.getShareServiceApi()
                    .receiveShare(shareId)
                    .blockingFirst();

            // 告知后端已保存
            retrofitClient.getShareServiceApi()
                .saveSharedPassword(shareId)
                .blockingSubscribe();

            // 将密码数据保存到本地
            if (response != null && response.getPasswordData() != null) {
                com.ttt.safevault.dto.PasswordData passwordData = response.getPasswordData();

                // 创建 PasswordItem 并保存到本地
                PasswordItem item = new PasswordItem();
                item.setTitle(passwordData.getTitle() != null ? passwordData.getTitle() : "未命名密码");
                item.setUsername(passwordData.getUsername());
                item.setPassword(passwordData.getPassword());
                item.setUrl(passwordData.getUrl());
                item.setNotes(passwordData.getNotes());

                saveItem(item);

                Log.d(TAG, "Cloud share saved to local: " + shareId);
            } else {
                Log.e(TAG, "Failed to save cloud share: invalid response");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save cloud share", e);
            throw new RuntimeException("保存云端分享失败: " + e.getMessage());
        }
    }

    @Override
    public java.util.List<com.ttt.safevault.dto.response.ReceivedShareResponse> getMyCloudShares() {
        try {
            return retrofitClient.getShareServiceApi()
                .getMyShares()
                .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get my cloud shares", e);
            return new ArrayList<>();
        }
    }

    @Override
    public java.util.List<com.ttt.safevault.dto.response.ReceivedShareResponse> getReceivedCloudShares() {
        try {
            return retrofitClient.getShareServiceApi()
                .getReceivedShares()
                .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get received cloud shares", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void registerLocation(double latitude, double longitude, double radius) {
        try {
            com.ttt.safevault.dto.request.RegisterLocationRequest request = 
                new com.ttt.safevault.dto.request.RegisterLocationRequest(latitude, longitude, radius);
            
            retrofitClient.getDiscoveryServiceApi()
                .registerLocation(request)
                .blockingSubscribe();
            
            Log.d(TAG, "Location registered");
        } catch (Exception e) {
            Log.e(TAG, "Failed to register location", e);
        }
    }

    @Override
    public java.util.List<com.ttt.safevault.dto.response.NearbyUserResponse> getNearbyUsers(double latitude, double longitude, double radius) {
        try {
            return retrofitClient.getDiscoveryServiceApi()
                .getNearbyUsers(latitude, longitude, radius)
                .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get nearby users", e);
            return new ArrayList<>();
        }
    }

    @Override
    public void sendHeartbeat() {
        try {
            retrofitClient.getDiscoveryServiceApi()
                .sendHeartbeat()
                .blockingSubscribe();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }

    @Override
    public boolean isCloudLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    @Override
    public void logoutCloud() {
        try {
            String userId = tokenManager.getUserId();
            String deviceId = com.ttt.safevault.security.KeyManager.getInstance(context).getDeviceId();

            if (userId != null && !userId.isEmpty()) {
                // 构建注销请求
                com.ttt.safevault.dto.request.LogoutRequest request =
                    com.ttt.safevault.dto.request.LogoutRequest.builder()
                        .deviceId(deviceId)
                        .timestamp(System.currentTimeMillis())
                        .build();

                // 调用后端注销 API
                retrofitClient.getAuthServiceApi()
                    .logout(userId, request)
                    .blockingSubscribe();

                Log.d(TAG, "Cloud logout successful");
            }

            // 清除令牌
            tokenManager.clearTokens();
            Log.d(TAG, "Logged out from cloud");
        } catch (Exception e) {
            Log.e(TAG, "Failed to logout from cloud", e);
            // 即使 API 调用失败，也清除本地令牌
            tokenManager.clearTokens();
            throw new RuntimeException("注销失败: " + e.getMessage());
        }
    }

    @Override
    public com.ttt.safevault.dto.response.CompleteRegistrationResponse completeRegistration(
            String email, String username, String masterPassword) {
        try {
            com.ttt.safevault.security.KeyManager keyManager =
                    com.ttt.safevault.security.KeyManager.getInstance(context);

            // 1. 生成盐值
            String salt = keyManager.generateSaltForUser(email);
            keyManager.saveUserSalt(email, salt);

            // 2. 派生密钥并生成密码验证器
            javax.crypto.SecretKey derivedKey = keyManager.deriveKeyFromMasterPassword(masterPassword, salt);
            String passwordVerifier = java.util.Base64.getEncoder().encodeToString(derivedKey.getEncoded());

            // 3. 生成 RSA 密钥对
            String publicKey = keyManager.getPublicKey();
            java.security.PrivateKey privateKey = keyManager.getPrivateKey();

            if (privateKey == null) {
                throw new RuntimeException("无法生成私钥");
            }

            // 4. 使用主密码加密私钥
            com.ttt.safevault.security.KeyManager.EncryptedPrivateKey encryptedKey =
                    keyManager.encryptPrivateKey(privateKey, masterPassword, email);

            // 5. 获取设备ID
            String deviceId = keyManager.getDeviceId();

            // 6. 构建完成注册请求
            com.ttt.safevault.dto.request.CompleteRegistrationRequest request =
                    com.ttt.safevault.dto.request.CompleteRegistrationRequest.builder()
                            .email(email)
                            .username(username)
                            .passwordVerifier(passwordVerifier)
                            .salt(salt)
                            .publicKey(publicKey)
                            .encryptedPrivateKey(encryptedKey.getEncryptedData())
                            .privateKeyIv(encryptedKey.getIv())
                            .deviceId(deviceId)
                            .build();

            // 7. 调用后端完成注册 API
            com.ttt.safevault.dto.response.CompleteRegistrationResponse response =
                    retrofitClient.getAuthServiceApi()
                            .completeRegistration(request)
                            .blockingFirst();

            if (response != null && response.getSuccess()) {
                // 8. 保存令牌
                tokenManager.saveTokens(response.getUserId(), response.getAccessToken(), response.getRefreshToken());

                // 9. 初始化 CryptoManager
                if (!cryptoManager.isInitialized()) {
                    cryptoManager.initialize(masterPassword);
                }

                Log.d(TAG, "Registration completed successfully for user: " + username);
            }

            return response;

        } catch (Exception e) {
            Log.e(TAG, "Failed to complete registration", e);
            throw new RuntimeException("注册完成失败: " + e.getMessage());
        }
    }

    // ========== 新增：统一邮箱认证加密数据接口实现 ==========

    @Override
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

    @Override
    public com.ttt.safevault.security.KeyManager.EncryptedPrivateKey downloadEncryptedPrivateKey() {
        try {
            String userId = tokenManager.getUserId();
            if (userId == null) {
                Log.e(TAG, "No user ID found for private key download");
                return null;
            }

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
                return new com.ttt.safevault.security.KeyManager.EncryptedPrivateKey(
                    response.getEncryptedPrivateKey(),
                    response.getIv(),
                    response.getSalt()
                );
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to download encrypted private key", e);
            return null;
        }
    }

    @Override
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

    @Override
    public EncryptedVaultData downloadEncryptedVaultData() {
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
                return new EncryptedVaultData(
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

    // ========== 私钥版本管理辅助方法 ==========

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
}