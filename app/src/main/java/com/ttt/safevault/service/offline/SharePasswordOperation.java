package com.ttt.safevault.service.offline;

import android.content.Context;
import android.util.Log;

import com.ttt.safevault.ServiceLocator;
import com.ttt.safevault.dto.request.CreateShareRequest;
import com.ttt.safevault.dto.response.ShareResponse;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.network.RetrofitClient;

import io.reactivex.rxjava3.core.Observable;

/**
 * 分享密码操作
 */
public class SharePasswordOperation extends OfflineOperation {
    private static final String TAG = "SharePasswordOperation";

    private final Context context;
    private String toUserId;
    private int passwordId;
    private int expireInMinutes;
    private String permissionJson;

    public SharePasswordOperation(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean execute() throws Exception {
        RetrofitClient retrofitClient = RetrofitClient.getInstance(context);

        try {
            // 将JSON转换为SharePermission对象
            SharePermission permission = SharePermission.fromJson(permissionJson);

            // 从ServiceLocator获取BackendService
            BackendService backendService = ServiceLocator.getInstance().getBackendService();
            if (backendService == null || !backendService.isUnlocked()) {
                Log.w(TAG, "BackendService is locked or unavailable, cannot share password");
                return false;
            }

            PasswordItem password = backendService.getPasswordById(passwordId);
            if (password == null) {
                Log.w(TAG, "Password not found: " + passwordId);
                return false;
            }

            // 构造CreateShareRequest，使用getter方法
            CreateShareRequest request = new CreateShareRequest();
            request.setPasswordId(String.valueOf(passwordId));
            request.setTitle(password.getTitle());
            request.setUsername(password.getUsername());
            request.setEncryptedPassword(password.getPassword());
            request.setUrl(password.getUrl());
            request.setNotes(password.getNotes());
            request.setToUserId(toUserId);
            request.setExpireInMinutes(expireInMinutes);
            request.setPermission(permission);

            Observable<ShareResponse> observable = retrofitClient
                .getShareServiceApi()
                .createShare(request);

            ShareResponse response = observable.blockingFirst();
            boolean success = response != null && response.getShareId() != null;

            if (success) {
                Log.i(TAG, "Password shared successfully: " + response.getShareId());
            } else {
                Log.w(TAG, "Password share failed for password: " + passwordId);
            }

            return success;
        } catch (Exception e) {
            incrementRetry();
            throw e;
        }
    }

    public String getToUserId() {
        return toUserId;
    }

    public void setToUserId(String toUserId) {
        this.toUserId = toUserId;
    }

    public int getPasswordId() {
        return passwordId;
    }

    public void setPasswordId(int passwordId) {
        this.passwordId = passwordId;
    }

    public int getExpireInMinutes() {
        return expireInMinutes;
    }

    public void setExpireInMinutes(int expireInMinutes) {
        this.expireInMinutes = expireInMinutes;
    }

    public String getPermissionJson() {
        return permissionJson;
    }

    public void setPermissionJson(String permissionJson) {
        this.permissionJson = permissionJson;
    }
}
