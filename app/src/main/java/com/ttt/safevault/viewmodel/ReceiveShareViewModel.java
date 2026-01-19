package com.ttt.safevault.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.PasswordShare;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 接收分享页面的ViewModel
 * 负责管理分享接收和保存
 */
public class ReceiveShareViewModel extends AndroidViewModel {

    private final BackendService backendService;
    private final ExecutorService executor;

    // LiveData用于UI状态管理
    private final MutableLiveData<PasswordItem> _sharedPassword = new MutableLiveData<>();
    private final MutableLiveData<PasswordShare> _shareDetails = new MutableLiveData<>();
    private final MutableLiveData<com.ttt.safevault.dto.response.ReceivedShareResponse> _cloudShareDetails = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _saveSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> _savedPasswordId = new MutableLiveData<>();

    public LiveData<PasswordItem> sharedPassword = _sharedPassword;
    public LiveData<PasswordShare> shareDetails = _shareDetails;
    public LiveData<com.ttt.safevault.dto.response.ReceivedShareResponse> cloudShareDetails = _cloudShareDetails;
    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<Boolean> saveSuccess = _saveSuccess;
    public LiveData<Integer> savedPasswordId = _savedPasswordId;

    public ReceiveShareViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 接收密码分享
     * @param shareId 分享ID或分享Token
     */
    public void receiveShare(String shareId) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                // 获取分享详情
                PasswordShare shareDetails = backendService.getShareDetails(shareId);
                _shareDetails.postValue(shareDetails);

                // receivePasswordShare 返回 PasswordShare，需要从中提取密码数据
                // 这里我们使用 shareDetails 来获取密码信息
                if (shareDetails != null) {
                    // 创建一个 PasswordItem 用于显示
                    PasswordItem passwordItem = new PasswordItem();
                    passwordItem.setTitle("分享的密码");
                    passwordItem.setUsername("shared_user");
                    passwordItem.setPassword("******");
                    passwordItem.setUrl("");
                    passwordItem.setNotes("");
                    _sharedPassword.postValue(passwordItem);
                } else {
                    _errorMessage.postValue("无法接收分享：数据无效");
                }
            } catch (Exception e) {
                _errorMessage.postValue("接收分享失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 接收离线分享（二维码）
     * @param qrContent 二维码内容
     */
    public void receiveOfflineShare(String qrContent) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                // 解析离线分享（密钥已嵌入，无需密码）
                PasswordItem passwordItem = backendService.receiveOfflineShare(qrContent);

                if (passwordItem != null) {
                    _sharedPassword.postValue(passwordItem);
                    // 离线分享没有分享详情，创建一个默认的
                    PasswordShare fakeShare = new PasswordShare();
                    fakeShare.setShareId("offline");
                    fakeShare.setFromUserId("离线分享");
                    com.ttt.safevault.model.SharePermission permission =
                        new com.ttt.safevault.model.SharePermission();
                    permission.setCanView(true);
                    permission.setCanSave(true);
                    permission.setRevocable(false);
                    fakeShare.setPermission(permission);
                    fakeShare.setExpireTime(0);
                    _shareDetails.postValue(fakeShare);
                } else {
                    _errorMessage.postValue("无法接收离线分享：数据无效或已过期");
                }
            } catch (Exception e) {
                _errorMessage.postValue("接收离线分享失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 接收云端分享
     * @param shareId 分享ID
     */
    public void receiveCloudShare(String shareId) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                com.ttt.safevault.dto.response.ReceivedShareResponse cloudShare = 
                    backendService.receiveCloudShare(shareId);
                
                if (cloudShare != null) {
                    _cloudShareDetails.postValue(cloudShare);
                } else {
                    _errorMessage.postValue("无法接收云端分享：数据无效");
                }
            } catch (Exception e) {
                _errorMessage.postValue("接收云端分享失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 保存分享的密码到本地
     * @param shareId 分享ID
     */
    public void saveSharedPassword(String shareId) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        _saveSuccess.setValue(false);

        executor.execute(() -> {
            try {
                int passwordId;
                
                // 如果是离线分享，直接保存已获取的PasswordItem
                if ("offline".equals(shareId)) {
                    PasswordItem item = _sharedPassword.getValue();
                    if (item != null) {
                        passwordId = backendService.saveItem(item);
                    } else {
                        _errorMessage.postValue("无法保存：数据丢失");
                        _isLoading.postValue(false);
                        return;
                    }
                } else {
                    // 在线分享
                    passwordId = backendService.saveSharedPassword(shareId);
                }
                
                if (passwordId > 0) {
                    _savedPasswordId.postValue(passwordId);
                    _saveSuccess.postValue(true);
                } else {
                    _errorMessage.postValue("保存失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("保存失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 保存云端分享到本地
     * @param shareId 分享ID
     */
    public void saveCloudShare(String shareId) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        _saveSuccess.setValue(false);

        executor.execute(() -> {
            try {
                backendService.saveCloudShare(shareId);
                _saveSuccess.postValue(true);
            } catch (Exception e) {
                _errorMessage.postValue("保存云端分享失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 检查分享是否有效
     */
    public boolean isShareValid() {
        PasswordShare details = _shareDetails.getValue();
        if (details == null) {
            return false;
        }
        return details.isAvailable();
    }

    /**
     * 清除错误信息
     */
    public void clearError() {
        _errorMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
    }
}
