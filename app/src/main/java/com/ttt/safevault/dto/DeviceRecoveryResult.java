package com.ttt.safevault.dto;

import androidx.annotation.Nullable;

/**
 * 设备数据恢复结果类
 */
public class DeviceRecoveryResult {
    private final boolean success;
    private final String message;
    private final Stage completedStage;

    public enum Stage {
        DOWNLOAD_KEY,      // 下载加密私钥
        DECRYPT_KEY,       // 解密私钥
        IMPORT_KEY,        // 导入私钥
        DOWNLOAD_VAULT,    // 下载密码库数据
        COMPLETE           // 完成
    }

    private DeviceRecoveryResult(boolean success, String message, Stage completedStage) {
        this.success = success;
        this.message = message;
        this.completedStage = completedStage;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Stage getCompletedStage() {
        return completedStage;
    }

    /**
     * 创建成功结果
     */
    public static DeviceRecoveryResult success(Stage completedStage) {
        return new DeviceRecoveryResult(true, "数据恢复成功", completedStage);
    }

    /**
     * 创建失败结果
     */
    public static DeviceRecoveryResult failure(String message, @Nullable Stage failedStage) {
        Stage stage = failedStage != null ? failedStage : Stage.DOWNLOAD_KEY;
        return new DeviceRecoveryResult(false, message, stage);
    }

    /**
     * 网络错误
     */
    public static DeviceRecoveryResult networkError(String message) {
        return failure("网络错误：" + message, Stage.DOWNLOAD_KEY);
    }

    /**
     * 解密失败
     */
    public static DeviceRecoveryResult decryptError(String message) {
        return failure("解密失败：" + message, Stage.DECRYPT_KEY);
    }

    /**
     * 导入失败
     */
    public static DeviceRecoveryResult importError(String message) {
        return failure("导入失败：" + message, Stage.IMPORT_KEY);
    }
}
