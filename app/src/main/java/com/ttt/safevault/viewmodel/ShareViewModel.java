package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.dto.response.ShareResponse;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordItem;
import com.ttt.safevault.model.SharePermission;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 密码分享页面的ViewModel
 * 负责管理分享配置和创建分享
 */
public class ShareViewModel extends AndroidViewModel {
    private static final String TAG = "ShareViewModel";

    private final BackendService backendService;
    private final ExecutorService executor;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // LiveData用于UI状态管理
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> _shareResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _shareSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<PasswordItem> _passwordItem = new MutableLiveData<>();
    private final MutableLiveData<String> _sharePassword = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isOfflineShare = new MutableLiveData<>(false);
    private final MutableLiveData<ShareResponse> _cloudShareResponse = new MutableLiveData<>();

    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<String> shareResult = _shareResult;
    public LiveData<Boolean> shareSuccess = _shareSuccess;
    public LiveData<PasswordItem> passwordItem = _passwordItem;
    public LiveData<String> sharePassword = _sharePassword;
    public LiveData<Boolean> isOfflineShare = _isOfflineShare;
    public LiveData<ShareResponse> cloudShareResponse = _cloudShareResponse;

    public ShareViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
        this.executor = Executors.newSingleThreadExecutor();
        this.retrofitClient = RetrofitClient.getInstance(application);
        this.tokenManager = retrofitClient.getTokenManager();
    }

    /**
     * 加载要分享的密码条目
     */
    public void loadPasswordItem(int passwordId) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                PasswordItem item = backendService.decryptItem(passwordId);
                _passwordItem.postValue(item);
            } catch (Exception e) {
                _errorMessage.postValue("加载密码失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 创建直接分享（无需好友）
     * @param passwordId 密码ID
     * @param expireInMinutes 过期时间（分钟）
     * @param permission 分享权限
     */
    public void createDirectShare(int passwordId, int expireInMinutes, 
                                 SharePermission permission) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        _shareSuccess.setValue(false);

        executor.execute(() -> {
            try {
                String shareToken = backendService.createDirectPasswordShare(
                    passwordId, expireInMinutes, permission
                );
                
                if (shareToken != null && !shareToken.isEmpty()) {
                    _shareResult.postValue(shareToken);
                    _shareSuccess.postValue(true);
                } else {
                    _errorMessage.postValue("创建分享失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("创建分享失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 创建离线分享（二维码传输，版本2：扫码直接访问）
     * @param passwordId 密码ID
     * @param expireInMinutes 过期时间（分钟）
     * @param permission 分享权限
     */
    public void createOfflineShare(int passwordId, int expireInMinutes,
                                  SharePermission permission) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        _shareSuccess.setValue(false);
        _isOfflineShare.setValue(true);

        executor.execute(() -> {
            try {
                // 创建离线分享（版本2：密钥已嵌入，无需密码）
                String qrContent = backendService.createOfflineShare(
                    passwordId, expireInMinutes, permission
                );

                if (qrContent != null && !qrContent.isEmpty()) {
                    _shareResult.postValue(qrContent);
                    _shareSuccess.postValue(true);
                    // 版本2不需要分享密码
                    _sharePassword.postValue(null);
                } else {
                    _errorMessage.postValue("创建离线分享失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("创建离线分享失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 清除错误信息
     */
    public void clearError() {
        _errorMessage.setValue(null);
    }

    /**
     * 清除分享结果
     */
    public void clearShareResult() {
        _shareResult.setValue(null);
        _shareSuccess.setValue(false);
    }

    /**
     * 创建云端分享（三种类型）
     * @param passwordId 密码ID
     * @param toUserId 接收方用户ID（DIRECT时为null）
     * @param expireInMinutes 过期时间
     * @param permission 分享权限
     * @param shareType 分享类型: DIRECT, USER_TO_USER, NEARBY
     */
    public void createCloudShare(int passwordId, String toUserId, int expireInMinutes,
                                SharePermission permission, String shareType) {
        if (!tokenManager.isLoggedIn()) {
            _errorMessage.setValue("请先登录云端服务");
            return;
        }

        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        _shareSuccess.setValue(false);

        Disposable disposable = io.reactivex.rxjava3.core.Observable.fromCallable(() -> 
            backendService.createCloudShare(passwordId, toUserId, expireInMinutes, permission, shareType)
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            response -> {
                _isLoading.setValue(false);
                if (response != null) {
                    _cloudShareResponse.setValue(response);
                    _shareResult.setValue(response.getShareToken());
                    _shareSuccess.setValue(true);
                    Log.d(TAG, "Cloud share created: " + response.getShareId());
                } else {
                    _errorMessage.setValue("创建云端分享失败");
                }
            },
            error -> {
                _isLoading.setValue(false);
                _errorMessage.setValue("创建云端分享失败: " + error.getMessage());
                Log.e(TAG, "Failed to create cloud share", error);
            }
        );

        disposables.add(disposable);
    }

    /**
     * 检查是否已登录云端
     */
    public boolean isCloudLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    /**
     * 根据ID获取密码（用于分享界面）
     * @param passwordId 密码ID
     * @return LiveData观察密码数据
     */
    public LiveData<PasswordItem> getPasswordById(int passwordId) {
        MutableLiveData<PasswordItem> result = new MutableLiveData<>();
        executor.execute(() -> {
            try {
                PasswordItem item = backendService.decryptItem(passwordId);
                result.postValue(item);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get password by id", e);
                result.postValue(null);
            }
        });
        return result;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
        disposables.clear();
    }
}
