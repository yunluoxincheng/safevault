package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.dto.request.RespondFriendRequestRequest;
import com.ttt.safevault.dto.request.SendFriendRequestRequest;
import com.ttt.safevault.dto.response.FriendRequestDto;
import com.ttt.safevault.dto.response.FriendRequestResponse;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 好友请求ViewModel
 * 管理好友请求的UI状态
 */
public class FriendRequestViewModel extends AndroidViewModel {
    private static final String TAG = "FriendRequestViewModel";

    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // LiveData
    private final MutableLiveData<List<FriendRequestDto>> pendingRequests = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operationSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();

    public LiveData<List<FriendRequestDto>> getPendingRequests() {
        return pendingRequests;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getOperationSuccess() {
        return operationSuccess;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public FriendRequestViewModel(@NonNull Application application) {
        super(application);
        this.retrofitClient = RetrofitClient.getInstance(application);
        this.tokenManager = retrofitClient.getTokenManager();
    }

    /**
     * 加载待处理的好友请求列表
     */
    public void loadPendingRequests() {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);

        Disposable disposable = retrofitClient.getFriendServiceApi()
            .getPendingRequests()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                requests -> {
                    isLoading.setValue(false);
                    pendingRequests.setValue(requests);
                    Log.d(TAG, "Loaded " + requests.size() + " pending requests");
                },
                error -> {
                    isLoading.setValue(false);
                    String message = "加载失败: " + error.getMessage();
                    errorMessage.setValue(message);
                    Log.e(TAG, "Failed to load pending requests", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 响应好友请求
     *
     * @param requestId 请求ID
     * @param accept    是否接受
     * @param callback  响应回调（用于UI更新后的操作）
     */
    public void respondToRequest(String requestId, boolean accept, ResponseCallback callback) {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            if (callback != null) {
                callback.onError("请先登录");
            }
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);
        operationSuccess.setValue(false);

        RespondFriendRequestRequest request = new RespondFriendRequestRequest(accept);

        Disposable disposable = retrofitClient.getFriendServiceApi()
            .respondToFriendRequest(requestId, request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    isLoading.setValue(false);
                    operationSuccess.setValue(true);
                    String action = accept ? "接受" : "拒绝";
                    successMessage.setValue("已" + action + "好友请求");
                    Log.d(TAG, "Responded to friend request: " + requestId + ", accept: " + accept);

                    if (callback != null) {
                        callback.onSuccess(accept);
                    }
                },
                error -> {
                    isLoading.setValue(false);
                    String message = "操作失败: " + error.getMessage();
                    errorMessage.setValue(message);
                    Log.e(TAG, "Failed to respond to friend request", error);

                    if (callback != null) {
                        callback.onError(message);
                    }
                }
            );

        disposables.add(disposable);
    }

    /**
     * 发送好友请求
     *
     * @param toUserId 目标用户ID
     * @param message  请求消息（可选）
     */
    public void sendFriendRequest(String toUserId, String message) {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);
        operationSuccess.setValue(false);

        SendFriendRequestRequest request = new SendFriendRequestRequest(toUserId, message);

        Disposable disposable = retrofitClient.getFriendServiceApi()
            .sendFriendRequest(request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    isLoading.setValue(false);
                    operationSuccess.setValue(true);
                    successMessage.setValue("好友请求已发送");
                    Log.d(TAG, "Friend request sent: " + response.getRequestId());
                },
                error -> {
                    isLoading.setValue(false);
                    String message1 = "发送失败: " + error.getMessage();
                    errorMessage.setValue(message1);
                    Log.e(TAG, "Failed to send friend request", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 清除错误消息
     */
    public void clearError() {
        errorMessage.setValue(null);
    }

    /**
     * 清除成功消息
     */
    public void clearSuccess() {
        operationSuccess.setValue(false);
        successMessage.setValue(null);
    }

    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        return tokenManager.isLoggedIn();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }

    /**
     * 响应回调接口
     */
    public interface ResponseCallback {
        void onSuccess(boolean accepted);

        void onError(String message);
    }
}
