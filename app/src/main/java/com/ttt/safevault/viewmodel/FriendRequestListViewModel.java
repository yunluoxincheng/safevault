package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.data.FriendRequest;
import com.ttt.safevault.dto.response.FriendDto;
import com.ttt.safevault.exception.AuthenticationException;
import com.ttt.safevault.exception.TokenExpiredException;
import com.ttt.safevault.service.manager.FriendRequestManager;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.HttpException;

/**
 * ViewModel for friend request list orchestration.
 */
public class FriendRequestListViewModel extends AndroidViewModel {
    private static final String TAG = "FriendReqListVM";

    private final FriendRequestManager friendRequestManager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<List<FriendRequest>> pendingRequests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> successMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> requireRelogin = new MutableLiveData<>(false);
    private final MutableLiveData<List<FriendDto>> newlyAddedContacts = new MutableLiveData<>();

    public FriendRequestListViewModel(@NonNull Application application) {
        super(application);
        friendRequestManager = new FriendRequestManager(application);
    }

    public LiveData<List<FriendRequest>> getPendingRequests() {
        return pendingRequests;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    public LiveData<Boolean> getRequireRelogin() {
        return requireRelogin;
    }

    public LiveData<List<FriendDto>> getNewlyAddedContacts() {
        return newlyAddedContacts;
    }

    public void loadPendingRequests() {
        loading.setValue(true);
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(friendRequestManager::fetchAndCachePendingRequests)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    requests -> {
                        loading.setValue(false);
                        pendingRequests.setValue(requests);
                    },
                    this::handleLoadError
                )
        );
    }

    public void respondToRequest(@NonNull FriendRequest request, boolean accept) {
        loading.setValue(true);
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(() -> {
                friendRequestManager.respondToRequest(request.requestId, accept);
                return true;
            })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ignored -> {
                        if (accept) {
                            syncContactsAfterAccept();
                            return;
                        }
                        loading.setValue(false);
                        successMessage.setValue("宸叉嫆缁濆ソ鍙嬭姹?");
                        loadPendingRequests();
                    },
                    error -> {
                        loading.setValue(false);
                        handleAuthError(error);
                        if (!isAuthError(error)) {
                            errorMessage.setValue("澶勭悊濂藉弸璇锋眰澶辫触锛岃閲嶈瘯");
                        }
                    }
                )
        );
    }

    public void consumeNewlyAddedContacts() {
        newlyAddedContacts.setValue(null);
    }

    public void clearMessages() {
        errorMessage.setValue(null);
        successMessage.setValue(null);
        requireRelogin.setValue(false);
    }

    private void handleLoadError(Throwable error) {
        loading.setValue(false);
        if (handleAuthError(error)) {
            return;
        }
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(friendRequestManager::getPendingRequestsFromLocal)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    localRequests -> {
                        pendingRequests.setValue(localRequests);
                        errorMessage.setValue("鏃犳硶浠庢湇鍔″櫒鍔犺浇濂藉弸璇锋眰锛屽凡鏄剧ず鏈湴缂撳瓨");
                    },
                    localError -> {
                        Log.e(TAG, "Failed to load local requests", localError);
                        errorMessage.setValue("鍔犺浇濂藉弸璇锋眰澶辫触");
                    }
                )
        );
    }

    private void syncContactsAfterAccept() {
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(friendRequestManager::syncContactsAfterAccept)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    syncResult -> {
                        loading.setValue(false);
                        successMessage.setValue("宸插悓鎰忓ソ鍙嬭姹?");
                        if (!syncResult.getToAdd().isEmpty()) {
                            newlyAddedContacts.setValue(syncResult.getToAdd());
                        }
                        loadPendingRequests();
                    },
                    error -> {
                        loading.setValue(false);
                        handleAuthError(error);
                        if (!isAuthError(error)) {
                            Log.e(TAG, "Contact sync failed after accept", error);
                            loadPendingRequests();
                        }
                    }
                )
        );
    }

    private boolean handleAuthError(Throwable error) {
        if (isAuthError(error)) {
            requireRelogin.setValue(true);
            return true;
        }
        return false;
    }

    private boolean isAuthError(Throwable error) {
        if (error instanceof TokenExpiredException || error instanceof AuthenticationException) {
            return true;
        }
        return (error instanceof HttpException) && ((HttpException) error).code() == 401;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
