package com.ttt.safevault.service.manager;

import android.content.Context;
import android.util.Log;

import com.ttt.safevault.crypto.CryptoManager;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.security.KeyManager;
import com.ttt.safevault.utils.BackupCryptoUtil;
import com.ttt.safevault.utils.BackupJsonUtil;
import com.ttt.safevault.utils.BackupValidationUtil;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;

/**
 * 数据导入导出管理器
 * 负责数据的备份和恢复
 */
public class DataImportExportManager {
    private static final String TAG = "DataImportExportManager";
    private static final String PREF_LAST_BACKUP = "last_backup";

    private final Context context;
    private final CryptoManager cryptoManager;
    private final PasswordManager passwordManager;
    private final android.content.SharedPreferences prefs;

    public DataImportExportManager(Context context, CryptoManager cryptoManager,
                                   PasswordManager passwordManager) {
        this.context = context.getApplicationContext();
        this.cryptoManager = cryptoManager;
        this.passwordManager = passwordManager;
        this.prefs = context.getSharedPreferences("backend_prefs", Context.MODE_PRIVATE);
    }

    /**
     * 导出数据到指定路径
     */
    public boolean exportData(String exportPath) {
        try {
            // 1. 获取所有密码数据
            List<PasswordItem> items = passwordManager.getAllItems();

            if (items.isEmpty()) {
                Log.w(TAG, "No data to export");
                return false;
            }

            // 2. 序列化为 JSON
            String jsonData = BackupJsonUtil.serializePasswordList(items);

            // 3. 使用主密码加密数据
            String masterPassword = cryptoManager.getMasterPassword();
            BackupCryptoUtil.EncryptionResult encryptionResult =
                    BackupCryptoUtil.encrypt(jsonData, masterPassword);

            // 4. 构建导出数据结构
            String deviceId = KeyManager.getInstance(context).getDeviceId();
            String appVersion = getAppVersion();

            com.ttt.safevault.dto.ExportData exportData =
                    BackupJsonUtil.buildExportData(
                            encryptionResult.getEncryptedData(),
                            encryptionResult.getIv(),
                            encryptionResult.getSalt(),
                            items.size(),
                            deviceId,
                            appVersion
                    );

            // 5. 序列化为最终 JSON
            String finalJson = BackupJsonUtil.serializeExportData(exportData);

            // 6. 写入文件
            FileWriter writer = new FileWriter(exportPath);
            writer.write(finalJson);
            writer.close();

            // 更新最后备份时间
            updateLastBackupTime();

            Log.d(TAG, "Data exported successfully to: " + exportPath);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to export data", e);
            return false;
        }
    }

    /**
     * 从指定路径导入数据
     */
    public boolean importData(String importPath) {
        try {
            // 1. 读取文件
            File file = new File(importPath);
            if (!file.exists()) {
                Log.e(TAG, "Import file does not exist: " + importPath);
                return false;
            }

            FileReader reader = new FileReader(file);
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                content.append(buffer, 0, length);
            }
            reader.close();

            // 2. 反序列化导出数据
            com.ttt.safevault.dto.ExportData exportData =
                    BackupJsonUtil.deserializeExportData(content.toString());

            // 3. 验证数据
            BackupValidationUtil.ValidationResult validationResult =
                    BackupValidationUtil.validateExportData(exportData);

            if (!validationResult.isValid()) {
                Log.e(TAG, "Invalid backup file: " + validationResult.getErrorMessage());
                return false;
            }

            // 4. 解密数据
            String masterPassword = cryptoManager.getMasterPassword();
            com.ttt.safevault.dto.ExportData.DataContainer dataContainer = exportData.getData();

            String decryptedJson = BackupCryptoUtil.decrypt(
                    dataContainer.getEncryptedData(),
                    masterPassword,
                    dataContainer.getSalt(),
                    dataContainer.getIv(),
                    dataContainer.getAuthTag()  // 添加 authTag 参数
            );

            // 5. 反序列化密码列表
            List<PasswordItem> items = BackupJsonUtil.deserializePasswordList(decryptedJson);

            // 6. 清空现有数据并导入新数据
            for (PasswordItem item : items) {
                // 使用负数ID表示新导入的条目
                item.setId(-1);
                passwordManager.saveItem(item);
            }

            Log.d(TAG, "Data imported successfully from: " + importPath + ", items: " + items.size());
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to import data", e);
            return false;
        }
    }

    /**
     * 获取应用统计信息
     */
    public BackendService.AppStats getStats() {
        try {
            List<PasswordItem> items = passwordManager.getAllItems();
            int totalItems = items.size();
            int weakPasswords = 0;
            int duplicatePasswords = 0;

            java.util.Set<String> passwordSet = new java.util.HashSet<>();
            for (PasswordItem item : items) {
                String pwd = item.getPassword();
                if (pwd != null) {
                    // 检查弱密码
                    PasswordGenerator generator = new PasswordGenerator();
                    if (generator.isWeakPassword(pwd)) {
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

            return new BackendService.AppStats(totalItems, weakPasswords, duplicatePasswords, daysSinceBackup);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get stats", e);
            return new BackendService.AppStats(0, 0, 0, -1);
        }
    }

    /**
     * 更新最后备份时间
     */
    private void updateLastBackupTime() {
        prefs.edit().putLong(PREF_LAST_BACKUP, System.currentTimeMillis()).apply();
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
}
