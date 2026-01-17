package com.ttt.safevault.dto;

import com.google.gson.annotations.SerializedName;
import java.time.LocalDateTime;

/**
 * 设备信息
 */
public class DeviceInfo {

    @SerializedName("deviceId")
    private String deviceId;

    @SerializedName("deviceName")
    private String deviceName;

    @SerializedName("deviceType")
    private String deviceType;

    @SerializedName("osVersion")
    private String osVersion;

    @SerializedName("lastActiveAt")
    private String lastActiveAt;

    @SerializedName("createdAt")
    private String createdAt;

    @SerializedName("isCurrentDevice")
    private boolean isCurrentDevice;

    public DeviceInfo() {
    }

    public DeviceInfo(String deviceId, String deviceName, String deviceType, String osVersion,
                      String lastActiveAt, String createdAt, boolean isCurrentDevice) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.deviceType = deviceType;
        this.osVersion = osVersion;
        this.lastActiveAt = lastActiveAt;
        this.createdAt = createdAt;
        this.isCurrentDevice = isCurrentDevice;
    }

    // Getters and Setters
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(String lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isCurrentDevice() {
        return isCurrentDevice;
    }

    public void setCurrentDevice(boolean currentDevice) {
        isCurrentDevice = currentDevice;
    }
}
