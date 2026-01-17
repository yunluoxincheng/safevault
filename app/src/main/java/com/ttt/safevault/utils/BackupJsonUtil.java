package com.ttt.safevault.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ttt.safevault.dto.ExportData;
import com.ttt.safevault.model.PasswordItem;

import java.util.List;

/**
 * 备份 JSON 序列化工具
 */
public class BackupJsonUtil {

    private static final String TAG = "BackupJsonUtil";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    /**
     * 将密码列表序列化为 JSON 字符串
     *
     * @param items 密码列表
     * @return JSON 字符串
     */
    public static String serializePasswordList(List<PasswordItem> items) {
        try {
            return GSON.toJson(items);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize password list", e);
            throw new RuntimeException("序列化失败", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化密码列表
     *
     * @param json JSON 字符串
     * @return 密码列表
     */
    public static List<PasswordItem> deserializePasswordList(String json) {
        try {
            PasswordItem[] items = GSON.fromJson(json, PasswordItem[].class);
            return java.util.Arrays.asList(items);
        } catch (Exception e) {
            Log.e(TAG, "Failed to deserialize password list", e);
            throw new RuntimeException("反序列化失败", e);
        }
    }

    /**
     * 构建导出数据对象
     *
     * @param encryptedData 加密的数据
     * @param iv           IV
     * @param salt         盐值
     * @param itemCount    条目数量
     * @param deviceId     设备ID
     * @param appVersion   应用版本
     * @return ExportData 对象
     */
    public static ExportData buildExportData(String encryptedData, String iv, String salt,
                                              int itemCount, String deviceId, String appVersion) {
        return ExportData.builder()
                .format("SAFEVAULT_BACKUP")
                .version("1.0")
                .metadata(ExportData.Metadata.builder()
                        .exportTime(System.currentTimeMillis())
                        .itemCount(itemCount)
                        .deviceId(deviceId)
                        .appVersion(appVersion)
                        .build())
                .data(ExportData.DataContainer.builder()
                        .encryptedData(encryptedData)
                        .iv(iv)
                        .salt(salt)
                        .build())
                .build();
    }

    /**
     * 将导出数据序列化为 JSON 字符串
     *
     * @param exportData 导出数据对象
     * @return JSON 字符串
     */
    public static String serializeExportData(ExportData exportData) {
        try {
            return GSON.toJson(exportData);
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize export data", e);
            throw new RuntimeException("序列化导出数据失败", e);
        }
    }

    /**
     * 从 JSON 字符串反序列化导出数据
     *
     * @param json JSON 字符串
     * @return ExportData 对象
     */
    public static ExportData deserializeExportData(String json) {
        try {
            return GSON.fromJson(json, ExportData.class);
        } catch (Exception e) {
            Log.e(TAG, "Failed to deserialize export data", e);
            throw new RuntimeException("反序列化导出数据失败", e);
        }
    }
}
