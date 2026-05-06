package com.ttt.safevault.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ttt.safevault.data.Contact;
import com.ttt.safevault.dto.response.UserSearchResult;
import com.ttt.safevault.service.manager.ContactManager;
import com.ttt.safevault.service.manager.FriendDiscoveryManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * ViewModel for friend search and request sending flow.
 */
public class ContactSearchViewModel extends AndroidViewModel {
    private static final String TAG = "ContactSearchVM";

    private final FriendDiscoveryManager friendDiscoveryManager;
    private final ContactManager contactManager;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Set<String> friendCloudUserIds = new HashSet<>();

    private final MutableLiveData<List<UserSearchResult>> searchResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> searching = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> requestSending = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> requestMessage = new MutableLiveData<>();

    public ContactSearchViewModel(@NonNull Application application) {
        super(application);
        friendDiscoveryManager = new FriendDiscoveryManager(application);
        contactManager = new ContactManager(application);
    }

    public LiveData<List<UserSearchResult>> getSearchResults() {
        return searchResults;
    }

    public LiveData<Boolean> getSearching() {
        return searching;
    }

    public LiveData<Boolean> getRequestSending() {
        return requestSending;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<String> getRequestMessage() {
        return requestMessage;
    }

    public void loadFriendFilter() {
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(contactManager::getAllContacts)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    contacts -> {
                        friendCloudUserIds.clear();
                        for (Contact contact : contacts) {
                            if (contact.cloudUserId != null && !contact.cloudUserId.isEmpty()) {
                                friendCloudUserIds.add(contact.cloudUserId);
                            }
                        }
                    },
                    error -> Log.e(TAG, "Failed to load friend filter", error)
                )
        );
    }

    public void searchUsers(@NonNull String query) {
        if (query.length() < 2) {
            errorMessage.setValue("璇疯緭鍏ヨ嚦灏?2 涓瓧绗?");
            return;
        }

        searching.setValue(true);
        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(() -> friendDiscoveryManager.searchUsers(query))
                .subscribeOn(Schedulers.io())
                .map(results -> {
                    List<UserSearchResult> filtered = new ArrayList<>();
                    for (UserSearchResult result : results) {
                        if (!friendCloudUserIds.contains(result.getUserId())) {
                            filtered.add(result);
                        }
                    }
                    return filtered;
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    results -> {
                        searching.setValue(false);
                        searchResults.setValue(results);
                    },
                    error -> {
                        searching.setValue(false);
                        searchResults.setValue(new ArrayList<>());
                        String message = error.getMessage() != null ? error.getMessage() : "鎼滅储澶辫触";
                        errorMessage.setValue("鎼滅储澶辫触: " + message);
                    }
                )
        );
    }

    public void clearSearchResults() {
        searchResults.setValue(new ArrayList<>());
    }

    public void sendFriendRequest(@NonNull String toUserId, String message, @NonNull String displayText) {
        requestSending.setValue(true);

        disposables.add(
            io.reactivex.rxjava3.core.Observable.fromCallable(() -> {
                friendDiscoveryManager.sendFriendRequest(toUserId, message);
                return true;
            })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    ignored -> {
                        requestSending.setValue(false);
                        requestMessage.setValue("宸插悜 " + displayText + " 鍙戦€佸ソ鍙嬭姹?");
                    },
                    error -> {
                        requestSending.setValue(false);
                        String message1 = error.getMessage() != null ? error.getMessage() : "鍙戦€佸け璐?";
                        requestMessage.setValue("鍙戦€佸ソ鍙嬭姹傚け璐? " + message1);
                    }
                )
        );
    }

    public void clearTransientMessages() {
        errorMessage.setValue(null);
        requestMessage.setValue(null);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
