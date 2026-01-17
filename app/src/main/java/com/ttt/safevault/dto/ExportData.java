package com.ttt.safevault.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 导出数据模型
 * 用于加密导出密码数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportData {

    private String format;           // 格式标识: "SAFEVAULT_BACKUP"
    private String version;          // 版本号: "1.0"
    private Metadata metadata;       // 元数据
    private DataContainer data;      // 加密数据容器

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private long exportTime;     // 导出时间戳
        private int itemCount;       // 条目数量
        private String deviceId;     // 设备ID
        private String appVersion;   // 应用版本
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataContainer {
        private String encryptedKey;  // 加密的密钥（可选，用于PIN码保护）
        private String iv;            // 初始化向量
        private String salt;          // 盐值
        private String encryptedData; // 加密的JSON数据
        private String authTag;       // 认证标签（GCM模式）
    }
}
