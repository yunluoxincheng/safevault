package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.dto.response.FriendDto;
import com.ttt.safevault.dto.response.UserSearchResult;
import com.ttt.safevault.exception.AuthenticationException;
import com.ttt.safevault.exception.TokenExpiredException;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.service.ContactSyncManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.HttpException;

/**
 * 好友列表ViewModel
 * 管理好友列表的UI状态
 */
public class FriendListViewModel extends AndroidViewModel {
    private static final String TAG = "FriendListViewModel";

    private final RetrofitClient retrofitClient;
    private final TokenManager tokenManager;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final ContactSyncManager contactSyncManager;

    // LiveData
    private final MutableLiveData<List<FriendDto>> friends = new MutableLiveData<>();
    private final MutableLiveData<List<UserSearchResult>> searchResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operationSuccess = new MutableLiveData<>(false);
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> tokenExpired = new MutableLiveData<>(false);

    public LiveData<List<FriendDto>> getFriends() {
        return friends;
    }

    public LiveData<List<UserSearchResult>> getSearchResults() {
        return searchResults;
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

    public LiveData<Boolean> getTokenExpired() {
        return tokenExpired;
    }

    public FriendListViewModel(@NonNull Application application) {
        super(application);
        this.retrofitClient = RetrofitClient.getInstance(application);
        this.tokenManager = retrofitClient.getTokenManager();
        this.contactSyncManager = new ContactSyncManager(application);
    }

    /**
     * 加载好友列表
     */
    public void loadFriendList() {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);
        tokenExpired.setValue(false);

        Disposable disposable = retrofitClient.getFriendServiceApi()
            .getFriendList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                friendList -> {
                    isLoading.setValue(false);
                    friends.setValue(friendList);
                    Log.d(TAG, "Loaded " + friendList.size() + " friends");

                    // 触发后台同步到本地数据库
                    contactSyncManager.syncContacts()
                        .subscribe(
                            result -> Log.d(TAG, "Contacts synced: add=" + result.getToAdd().size()),
                            error -> {
                                Log.e(TAG, "Contact sync failed", error);
                                Throwable mappedError = mapApiError(error);
                                handleSyncError(mappedError);
                            }
                        );
                },
                error -> {
                    isLoading.setValue(false);
                    Throwable mappedError = mapApiError(error);
                    handleLoadError(mappedError);
                    Log.e(TAG, "Failed to load friend list", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 将 API 错误映射到自定义异常类型
     */
    private Throwable mapApiError(Throwable error) {
        // 如果已经是自定义异常，直接返回
        if (error instanceof TokenExpiredException ||
            error instanceof AuthenticationException) {
            return error;
        }

        // 检查是否是 HttpException
        if (error instanceof HttpException) {
            HttpException httpException = (HttpException) error;
            int code = httpException.code();

            if (code == 401) {
                return new TokenExpiredException("Authentication failed", error);
            } else if (code >= 400 && code < 500) {
                return new AuthenticationException("AUTH_ERROR", "Client error: " + code, error);
            } else if (code >= 500) {
                return new com.ttt.safevault.exception.NetworkException("SERVER_ERROR", "Server error: " + code, error);
            }
        }

        // 检查是否是网络错误
        if (error instanceof java.io.IOException) {
            return new com.ttt.safevault.exception.NetworkException("NETWORK_ERROR", "Network connection failed", error);
        }

        // 其他错误保持原样
        return error;
    }

    /**
     * 处理同步错误
     */
    private void handleSyncError(Throwable error) {
        if (error instanceof TokenExpiredException) {
            tokenExpired.setValue(true);
            errorMessage.setValue("登录已过期，请重新登录");
        } else if (error instanceof AuthenticationException) {
            errorMessage.setValue("认证失败: " + error.getMessage());
        } else {
            errorMessage.setValue("同步失败: " + error.getMessage());
        }
    }

    /**
     * 处理加载错误
     */
    private void handleLoadError(Throwable error) {
        if (error instanceof TokenExpiredException) {
            tokenExpired.setValue(true);
            errorMessage.setValue("登录已过期，请重新登录");
        } else if (error instanceof AuthenticationException) {
            errorMessage.setValue("认证失败: " + error.getMessage());
        } else {
            String message = "加载好友列表失败: " + error.getMessage();
            errorMessage.setValue(message);
        }
    }

    /**
     * 搜索用户
     *
     * @param query 搜索关键词（用户名或邮箱）
     */
    public void searchUsers(String query) {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        if (query == null || query.trim().isEmpty()) {
            errorMessage.setValue("请输入搜索关键词");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);
        searchResults.setValue(new ArrayList<>());

        Disposable disposable = retrofitClient.getFriendServiceApi()
            .searchUsers(query.trim())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                results -> {
                    isLoading.setValue(false);
                    searchResults.setValue(results);
                    Log.d(TAG, "Found " + results.size() + " users for query: " + query);
                },
                error -> {
                    isLoading.setValue(false);
                    String message = "搜索失败: " + error.getMessage();
                    errorMessage.setValue(message);
                    Log.e(TAG, "Failed to search users", error);
                }
            );

        disposables.add(disposable);
    }

    /**
     * 删除好友
     *
     * @param friendUserId 要删除的好友用户ID
     */
    public void deleteFriend(String friendUserId) {
        if (!tokenManager.isLoggedIn()) {
            errorMessage.setValue("请先登录");
            return;
        }

        isLoading.setValue(true);
        errorMessage.setValue(null);
        operationSuccess.setValue(false);

        Disposable disposable = retrofitClient.getFriendServiceApi()
            .deleteFriend(friendUserId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                response -> {
                    isLoading.setValue(false);
                    operationSuccess.setValue(true);
                    successMessage.setValue("已删除好友");
                    Log.d(TAG, "Deleted friend: " + friendUserId);

                    // 重新加载好友列表
                    loadFriendList();
                },
                error -> {
                    isLoading.setValue(false);
                    String message = "删除失败: " + error.getMessage();
                    errorMessage.setValue(message);
                    Log.e(TAG, "Failed to delete friend", error);
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
     * 清除搜索结果
     */
    public void clearSearchResults() {
        searchResults.setValue(new ArrayList<>());
    }

    /**
     * 刷新好友列表
     */
    public void refresh() {
        loadFriendList();
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
}
