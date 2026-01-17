package com.ttt.safevault.utils;

import android.util.Log;
import android.util.Patterns;

import com.ttt.safevault.dto.ExportData;

import java.util.regex.Pattern;

/**
 * 备份文件验证工具
 */
public class BackupValidationUtil {

    private static final String TAG = "BackupValidationUtil";
    private static final String EXPECTED_FORMAT = "SAFEVAULT_BACKUP";
    private static final String EXPECTED_VERSION = "1.0";
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "safevault_backup_[0-9]{8}_[0-9]{6}\\.vault$"
    );

    /**
     * 验证导出数据结构
     *
     * @param exportData 导出数据
     * @return 验证结果
     */
    public static ValidationResult validateExportData(ExportData exportData) {
        try {
            // 检查基本字段
            if (exportData == null) {
                return ValidationResult.failure("导出数据为空");
            }

            // 检查格式标识
            if (!EXPECTED_FORMAT.equals(exportData.getFormat())) {
                return ValidationResult.failure("不支持的文件格式: " + exportData.getFormat());
            }

            // 检查版本
            if (!EXPECTED_VERSION.equals(exportData.getVersion())) {
                return ValidationResult.failure("不支持的文件版本: " + exportData.getVersion());
            }

            // 检查元数据
            ExportData.Metadata metadata = exportData.getMetadata();
            if (metadata == null) {
                return ValidationResult.failure("缺少元数据");
            }

            if (metadata.getExportTime() <= 0) {
                return ValidationResult.failure("无效的导出时间");
            }

            if (metadata.getItemCount() < 0) {
                return ValidationResult.failure("无效的条目数量");
            }

            // 检查数据容器
            ExportData.DataContainer data = exportData.getData();
            if (data == null) {
                return ValidationResult.failure("缺少数据容器");
            }

            if (data.getEncryptedData() == null || data.getEncryptedData().isEmpty()) {
                return ValidationResult.failure("加密数据为空");
            }

            if (data.getSalt() == null || data.getSalt().isEmpty()) {
                return ValidationResult.failure("盐值为空");
            }

            if (data.getIv() == null || data.getIv().isEmpty()) {
                return ValidationResult.failure("初始化向量为空");
            }

            return ValidationResult.success();

        } catch (Exception e) {
            Log.e(TAG, "Validation error", e);
            return ValidationResult.failure("验证失败: " + e.getMessage());
        }
    }

    /**
     * 验证文件名格式
     *
     * @param filename 文件名
     * @return 验证结果
     */
    public static ValidationResult validateFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return ValidationResult.failure("文件名为空");
        }

        if (!FILENAME_PATTERN.matcher(filename).matches()) {
            return ValidationResult.failure("文件名格式不正确");
        }

        return ValidationResult.success();
    }

    /**
     * 验证文件扩展名
     *
     * @param filename 文件名
     * @return 是否为有效的备份文件
     */
    public static boolean isValidBackupFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            return false;
        }
        return filename.endsWith(".vault");
    }

    /**
     * 生成备份文件名
     *
     * @return 文件名
     */
    public static String generateBackupFilename() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                java.util.Locale.getDefault());
        String timestamp = sdf.format(new java.util.Date());
        return "safevault_backup_" + timestamp + ".vault";
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
