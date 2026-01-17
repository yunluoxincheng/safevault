package com.ttt.safevault.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.dto.DeviceInfo;
import com.ttt.safevault.network.RetrofitClient;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 设备管理器
 * 负责设备的注册、列表获取、移除等功能
 */
public class DeviceManager {
    private static final String TAG = "DeviceManager";
    private static final String PREFS_NAME = "device_prefs";
    private static final String KEY_CURRENT_DEVICE_ID = "current_device_id";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_IS_NEW_DEVICE = "is_new_device";

    private static volatile DeviceManager INSTANCE;
    private final Context context;
    private final SharedPreferences prefs;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private DeviceInfo currentDevice;
    private List<DeviceInfo> deviceList = new ArrayList<>();

    private DeviceManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadCurrentDeviceInfo();
    }

    public static DeviceManager getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (DeviceManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DeviceManager(context);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 加载当前设备信息
     */
    private void loadCurrentDeviceInfo() {
        String deviceId = getCurrentDeviceId();
        String deviceName = prefs.getString(KEY_DEVICE_NAME, null);

        if (deviceId != null && deviceName != null) {
            currentDevice = new DeviceInfo(
                    deviceId,
                    deviceName,
                    "android",
                    "Android " + Build.VERSION.RELEASE,
                    null,
                    null,
                    true
            );
            Log.d(TAG, "Loaded current device info: " + currentDevice);
        } else {
            // 生成新设备信息
            currentDevice = generateCurrentDeviceInfo();
            saveCurrentDeviceInfo();
            Log.d(TAG, "Generated new device info: " + currentDevice);
        }
    }

    /**
     * 生成当前设备信息
     */
    private DeviceInfo generateCurrentDeviceInfo() {
        String deviceId = getCurrentDeviceId();
        String deviceName = generateDeviceName();
        String deviceType = "android";
        String osVersion = "Android " + Build.VERSION.RELEASE;

        return new DeviceInfo(deviceId, deviceName, deviceType, osVersion, null, null, true);
    }

    /**
     * 保存当前设备信息
     */
    private void saveCurrentDeviceInfo() {
        if (currentDevice != null) {
            prefs.edit()
                    .putString(KEY_DEVICE_NAME, currentDevice.getDeviceName())
                    .apply();
        }
    }

    /**
     * 获取当前设备ID
     */
    public String getCurrentDeviceId() {
        return prefs.getString(KEY_CURRENT_DEVICE_ID, null);
    }

    /**
     * 生成设备名称
     */
    private String generateDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * 获取当前设备信息
     */
    public DeviceInfo getCurrentDevice() {
        return currentDevice;
    }

    /**
     * 标记为新设备
     */
    public void markAsNewDevice(boolean isNew) {
        prefs.edit().putBoolean(KEY_IS_NEW_DEVICE, isNew).apply();
    }

    /**
     * 检查是否为新设备
     */
    public boolean isNewDevice() {
        return prefs.getBoolean(KEY_IS_NEW_DEVICE, false);
    }

    /**
     * 清除新设备标记
     */
    public void clearNewDeviceFlag() {
        prefs.edit().remove(KEY_IS_NEW_DEVICE).apply();
    }

    /**
     * 从服务器获取设备列表
     *
     * @param callback 回调接口
     */
    public void fetchDeviceList(DeviceListCallback callback) {
        try {
            String userId = com.ttt.safevault.network.RetrofitClient.getInstance(context)
                .getTokenManager().getUserId();

            if (userId == null) {
                Log.e(TAG, "No user ID found for fetching device list");
                callback.onError("未登录");
                return;
            }

            Log.d(TAG, "Fetching device list from server...");

            disposables.add(
                RetrofitClient.getInstance(context)
                    .getAuthServiceApi()
                    .getDevices(userId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        response -> {
                            if (response != null && response.getDevices() != null) {
                                this.deviceList = response.getDevices();
                                callback.onSuccess(response.getDevices());
                                Log.d(TAG, "Device list fetched successfully: " + response.getDevices().size() + " devices");
                            } else {
                                callback.onError("获取设备列表失败");
                                Log.e(TAG, "Failed to fetch device list: null response");
                            }
                        },
                        error -> {
                            Log.e(TAG, "Failed to fetch device list", error);
                            callback.onError("网络错误: " + error.getMessage());
                        }
                    )
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch device list", e);
            callback.onError("获取设备列表失败");
        }
    }

    /**
     * 移除设备
     *
     * @param deviceId 要移除的设备ID
     * @param callback 回调接口
     */
    public void removeDevice(String deviceId, DeviceOperationCallback callback) {
        try {
            String userId = com.ttt.safevault.network.RetrofitClient.getInstance(context)
                .getTokenManager().getUserId();

            if (userId == null) {
                Log.e(TAG, "No user ID found for removing device");
                callback.onError("未登录");
                return;
            }

            Log.d(TAG, "Removing device: " + deviceId);

            disposables.add(
                RetrofitClient.getInstance(context)
                    .getAuthServiceApi()
                    .removeDevice(userId, deviceId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        response -> {
                            if (response != null && response.isSuccess()) {
                                // 从本地列表移除设备
                                boolean removed = false;
                                for (int i = 0; i < deviceList.size(); i++) {
                                    if (deviceId.equals(deviceList.get(i).getDeviceId())) {
                                        deviceList.remove(i);
                                        removed = true;
                                        break;
                                    }
                                }
                                if (removed) {
                                    callback.onSuccess();
                                    Log.d(TAG, "Device removed successfully: " + deviceId);
                                } else {
                                    callback.onError("设备未找到");
                                }
                            } else {
                                String errorMsg = response != null ? response.getMessage() : "移除设备失败";
                                callback.onError(errorMsg);
                                Log.e(TAG, "Failed to remove device: " + errorMsg);
                            }
                        },
                        error -> {
                            Log.e(TAG, "Failed to remove device", error);
                            // 处理特定错误
                            String errorMessage = error.getMessage();
                            if (errorMessage != null && errorMessage.contains("CANNOT_REMOVE_CURRENT_DEVICE")) {
                                callback.onError("无法移除当前正在使用的设备");
                            } else {
                                callback.onError("网络错误: " + errorMessage);
                            }
                        }
                    )
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove device", e);
            callback.onError("移除设备失败");
        }
    }

    /**
     * 更新设备列表
     *
     * @param devices 设备列表
     */
    public void updateDeviceList(List<DeviceInfo> devices) {
        this.deviceList = devices != null ? devices : new ArrayList<>();
        Log.d(TAG, "Device list updated: " + deviceList.size() + " devices");
    }

    /**
     * 获取设备列表
     *
     * @return 设备列表
     */
    public List<DeviceInfo> getDeviceList() {
        return new ArrayList<>(deviceList);
    }

    /**
     * 检查设备是否已注册
     *
     * @param deviceId 设备ID
     * @return 是否已注册
     */
    public boolean isDeviceRegistered(String deviceId) {
        for (DeviceInfo device : deviceList) {
            if (deviceId.equals(device.getDeviceId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前设备在列表中的索引
     *
     * @return 索引，如果未找到返回 -1
     */
    public int getCurrentDeviceIndex() {
        if (currentDevice == null) {
            return -1;
        }

        for (int i = 0; i < deviceList.size(); i++) {
            if (currentDevice.getDeviceId().equals(deviceList.get(i).getDeviceId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 清除所有设备数据
     */
    public void clearAll() {
        prefs.edit()
                .remove(KEY_CURRENT_DEVICE_ID)
                .remove(KEY_DEVICE_NAME)
                .remove(KEY_IS_NEW_DEVICE)
                .apply();
        currentDevice = null;
        deviceList.clear();
        Log.d(TAG, "All device data cleared");
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        disposables.clear();
    }

    // ========== 回调接口 ==========

    /**
     * 设备列表回调接口
     */
    public interface DeviceListCallback {
        void onSuccess(List<DeviceInfo> devices);
        void onError(String error);
    }

    /**
     * 设备操作回调接口
     */
    public interface DeviceOperationCallback {
        void onSuccess();
        void onError(String error);
    }
}
