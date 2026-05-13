package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.dto.response.ReceivedShareResponse;
import com.ttt.safevault.model.BackendService;
import com.ttt.safevault.model.PasswordShare;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 分享历史页面的ViewModel
 * 负责管理分享历史列表和撤销操作
 */
public class ShareHistoryViewModel extends AndroidViewModel {
    private static final String TAG = "ShareHistoryViewModel";

    private final BackendService backendService;
    private final ExecutorService executor;
    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // LiveData用于UI状态管理
    private final MutableLiveData<List<PasswordShare>> _myShares = new MutableLiveData<>();
    private final MutableLiveData<List<PasswordShare>> _receivedShares = new MutableLiveData<>();
    private final MutableLiveData<List<ReceivedShareResponse>> _cloudMyShares = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ReceivedShareResponse>> _cloudReceivedShares = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> _errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _operationSuccess = new MutableLiveData<>(false);

    public LiveData<List<PasswordShare>> myShares = _myShares;
    public LiveData<List<PasswordShare>> receivedShares = _receivedShares;
    public LiveData<List<ReceivedShareResponse>> cloudMyShares = _cloudMyShares;
    public LiveData<List<ReceivedShareResponse>> cloudReceivedShares = _cloudReceivedShares;
    public LiveData<Boolean> isLoading = _isLoading;
    public LiveData<String> errorMessage = _errorMessage;
    public LiveData<Boolean> operationSuccess = _operationSuccess;
    public LiveData<Boolean> revokeSuccess = _operationSuccess; // 别名，为了兼容

    public ShareHistoryViewModel(@NonNull Application application, BackendService backendService) {
        super(application);
        this.backendService = backendService;
        this.executor = Executors.newSingleThreadExecutor();
        this.retrofitClient = RetrofitClient.getInstance(application);
        this.tokenManager = retrofitClient.getTokenManager();
    }

    /**
     * 加载我创建的分享列表
     */
    public void loadMyShares() {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<PasswordShare> shares = backendService.getMyShares();
                _myShares.postValue(shares);
            } catch (Exception e) {
                _errorMessage.postValue("加载分享列表失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 加载我接收的分享列表
     */
    public void loadReceivedShares() {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<PasswordShare> shares = backendService.getReceivedShares();
                _receivedShares.postValue(shares);
            } catch (Exception e) {
                _errorMessage.postValue("加载分享列表失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 撤销分享
     * @param shareId 分享ID
     */
    public void revokeShare(String shareId) {
        _isLoading.setValue(true);
        _errorMessage.setValue(null);
        _operationSuccess.setValue(false);

        executor.execute(() -> {
            try {
                boolean success = backendService.revokePasswordShare(shareId);
                
                if (success) {
                    _operationSuccess.postValue(true);
                    // 重新加载列表
                    loadMyShares();
                } else {
                    _errorMessage.postValue("撤销分享失败");
                }
            } catch (Exception e) {
                _errorMessage.postValue("撤销分享失败: " + e.getMessage());
            } finally {
                _isLoading.postValue(false);
            }
        });
    }

    /**
     * 获取分享详情
     * @param shareId 分享ID
     */
    public void getShareDetails(String shareId, ShareDetailsCallback callback) {
        executor.execute(() -> {
            try {
                PasswordShare share = backendService.getShareDetails(shareId);
                if (callback != null) {
                    callback.onResult(share, null);
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onResult(null, e);
                }
            }
        });
    }

    /**
     * 刷新所有数据
     */
    public void refreshAll() {
        loadMyShares();
        loadReceivedShares();
    }

    /**
     * 清除错误信息
     */
    public void clearError() {
        _errorMessage.setValue(null);
    }

    /**
     * 清除操作成功状态
     */
    public void clearOperationSuccess() {
        _operationSuccess.setValue(false);
    }

    /**
     * 加载云端分享列表（我创建的）
     */
    public void loadCloudMyShares() {
        if (!tokenManager.isLoggedIn()) {
            _errorMessage.setValue("请先登录云端服务");
            return;
        }

        _isLoading.setValue(true);
        
        Disposable disposable = Observable.fromCallable(() -> 
            backendService.getMyCloudShares()
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            shares -> {
                _isLoading.setValue(false);
                _cloudMyShares.setValue(shares);
                Log.d(TAG, "Loaded " + shares.size() + " cloud shares");
            },
            error -> {
                _isLoading.setValue(false);
                _errorMessage.setValue("加载云端分享失败: " + error.getMessage());
                Log.e(TAG, "Failed to load cloud shares", error);
            }
        );
        
        disposables.add(disposable);
    }

    /**
     * 加载云端分享列表（我接收的）
     */
    public void loadCloudReceivedShares() {
        if (!tokenManager.isLoggedIn()) {
            _errorMessage.setValue("请先登录云端服务");
            return;
        }

        _isLoading.setValue(true);
        
        Disposable disposable = Observable.fromCallable(() -> 
            backendService.getReceivedCloudShares()
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            shares -> {
                _isLoading.setValue(false);
                _cloudReceivedShares.setValue(shares);
                Log.d(TAG, "Loaded " + shares.size() + " received cloud shares");
            },
            error -> {
                _isLoading.setValue(false);
                _errorMessage.setValue("加载云端分享失败: " + error.getMessage());
                Log.e(TAG, "Failed to load received cloud shares", error);
            }
        );
        
        disposables.add(disposable);
    }

    /**
     * 静默加载我创建的分享列表（不显示加载动画）
     */
    public void loadMySharesSilently() {
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<PasswordShare> shares = backendService.getMyShares();
                _myShares.postValue(shares);
            } catch (Exception e) {
                _errorMessage.postValue("加载分享列表失败: " + e.getMessage());
            }
        });
    }

    /**
     * 静默加载我接收的分享列表（不显示加载动画）
     */
    public void loadReceivedSharesSilently() {
        _errorMessage.setValue(null);

        executor.execute(() -> {
            try {
                List<PasswordShare> shares = backendService.getReceivedShares();
                _receivedShares.postValue(shares);
            } catch (Exception e) {
                _errorMessage.postValue("加载分享列表失败: " + e.getMessage());
            }
        });
    }

    /**
     * 静默加载云端分享列表（我创建的，不显示加载动画）
     */
    public void loadCloudMySharesSilently() {
        if (!tokenManager.isLoggedIn()) {
            _errorMessage.setValue("请先登录云端服务");
            return;
        }

        Disposable disposable = Observable.fromCallable(() ->
            backendService.getMyCloudShares()
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            shares -> {
                _cloudMyShares.setValue(shares);
                Log.d(TAG, "Loaded " + shares.size() + " cloud shares");
            },
            error -> {
                _errorMessage.setValue("加载云端分享失败: " + error.getMessage());
                Log.e(TAG, "Failed to load cloud shares", error);
            }
        );

        disposables.add(disposable);
    }

    /**
     * 静默加载云端分享列表（我接收的，不显示加载动画）
     */
    public void loadCloudReceivedSharesSilently() {
        if (!tokenManager.isLoggedIn()) {
            _errorMessage.setValue("请先登录云端服务");
            return;
        }

        Disposable disposable = Observable.fromCallable(() ->
            backendService.getReceivedCloudShares()
        )
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            shares -> {
                _cloudReceivedShares.setValue(shares);
                Log.d(TAG, "Loaded " + shares.size() + " received cloud shares");
            },
            error -> {
                _errorMessage.setValue("加载云端分享失败: " + error.getMessage());
                Log.e(TAG, "Failed to load received cloud shares", error);
            }
        );

        disposables.add(disposable);
    }

    /**
     * 撤销云端分享
     */
    public void revokeCloudShare(String shareId) {
        _isLoading.setValue(true);
        
        Disposable disposable = Observable.fromCallable(() -> {
            backendService.revokeCloudShare(shareId);
            return true;
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            success -> {
                _isLoading.setValue(false);
                _operationSuccess.setValue(true);
                loadCloudMyShares(); // 重新加载
                Log.d(TAG, "Cloud share revoked");
            },
            error -> {
                _isLoading.setValue(false);
                _errorMessage.setValue("撤销分享失败: " + error.getMessage());
                Log.e(TAG, "Failed to revoke cloud share", error);
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

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdown();
        disposables.clear();
    }

    /**
     * 分享详情回调接口
     */
    public interface ShareDetailsCallback {
        void onResult(PasswordShare share, Exception error);
    }
}
