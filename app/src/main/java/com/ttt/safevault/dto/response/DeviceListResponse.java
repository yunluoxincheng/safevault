package com.ttt.safevault.dto.response;

import com.ttt.safevault.dto.DeviceInfo;

import java.util.List;

/**
 * 设备列表响应
 */
public class DeviceListResponse {

    private List<DeviceInfo> devices;
    private int totalDevices;

    public DeviceListResponse() {
    }

    public List<DeviceInfo> getDevices() {
        return devices;
    }

    public void setDevices(List<DeviceInfo> devices) {
        this.devices = devices;
    }

    public int getTotalDevices() {
        return totalDevices;
    }

    public void setTotalDevices(int totalDevices) {
        this.totalDevices = totalDevices;
    }
}
