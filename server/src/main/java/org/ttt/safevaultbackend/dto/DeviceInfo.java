package org.ttt.safevaultbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 设备信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceInfo {

    private String deviceId;
    private String deviceName;
    private String deviceType;
    private String osVersion;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private boolean isCurrentDevice;

    /**
     * 从 JSON 字符串解析设备信息列表
     */
    public static java.util.List<DeviceInfo> fromJson(String devicesJson) {
        if (devicesJson == null || devicesJson.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(devicesJson,
                mapper.getTypeFactory().constructCollectionType(java.util.List.class, DeviceInfo.class));
        } catch (Exception e) {
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 将设备信息列表转换为 JSON 字符串
     */
    public static String toJson(java.util.List<DeviceInfo> devices) {
        if (devices == null || devices.isEmpty()) {
            return "[]";
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(devices);
        } catch (Exception e) {
            return "[]";
        }
    }
}
