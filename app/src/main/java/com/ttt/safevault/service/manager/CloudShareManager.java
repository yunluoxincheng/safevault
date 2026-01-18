package com.ttt.safevault.service.manager;

import android.util.Log;

import androidx.annotation.NonNull;

import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.SharePermission;

import java.util.ArrayList;
import java.util.List;

/**
 * 云端分享管理器
 * 负责云端密码分享、接收、撤销等功能
 */
public class CloudShareManager {
    private static final String TAG = "CloudShareManager";

    private final PasswordManager passwordManager;
    private final com.ttt.safevault.network.RetrofitClient retrofitClient;

    public CloudShareManager(@NonNull PasswordManager passwordManager,
                            @NonNull com.ttt.safevault.network.RetrofitClient retrofitClient) {
        this.passwordManager = passwordManager;
        this.retrofitClient = retrofitClient;
    }

    /**
     * 创建云端分享
     */
    public com.ttt.safevault.dto.response.ShareResponse createCloudShare(int passwordId, String toUserId,
                                                                          int expireInMinutes, SharePermission permission,
                                                                          String shareType) {
        try {
            // 获取密码数据
            PasswordItem item = passwordManager.decryptItem(passwordId);
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

    /**
     * 接收云端分享
     */
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

    /**
     * 撤销云端分享
     */
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

    /**
     * 保存云端分享到本地
     */
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

                passwordManager.saveItem(item);

                Log.d(TAG, "Cloud share saved to local: " + shareId);
            } else {
                Log.e(TAG, "Failed to save cloud share: invalid response");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save cloud share", e);
            throw new RuntimeException("保存云端分享失败: " + e.getMessage());
        }
    }

    /**
     * 获取我创建的云端分享列表
     */
    public List<com.ttt.safevault.dto.response.ReceivedShareResponse> getMyCloudShares() {
        try {
            return retrofitClient.getShareServiceApi()
                .getMyShares()
                .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get my cloud shares", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取接收到的云端分享列表
     */
    public List<com.ttt.safevault.dto.response.ReceivedShareResponse> getReceivedCloudShares() {
        try {
            return retrofitClient.getShareServiceApi()
                .getReceivedShares()
                .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get received cloud shares", e);
            return new ArrayList<>();
        }
    }

    /**
     * 注册位置
     */
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

    /**
     * 获取附近用户
     */
    public List<com.ttt.safevault.dto.response.NearbyUserResponse> getNearbyUsers(double latitude, double longitude, double radius) {
        try {
            return retrofitClient.getDiscoveryServiceApi()
                .getNearbyUsers(latitude, longitude, radius)
                .blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get nearby users", e);
            return new ArrayList<>();
        }
    }

    /**
     * 发送心跳
     */
    public void sendHeartbeat() {
        try {
            retrofitClient.getDiscoveryServiceApi()
                .sendHeartbeat()
                .blockingSubscribe();
        } catch (Exception e) {
            Log.e(TAG, "Failed to send heartbeat", e);
        }
    }
}
