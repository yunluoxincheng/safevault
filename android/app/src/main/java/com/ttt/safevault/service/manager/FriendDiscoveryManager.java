package com.ttt.safevault.service.manager;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ttt.safevault.dto.request.SendFriendRequestRequest;
import com.ttt.safevault.dto.response.UserSearchResult;
import com.ttt.safevault.network.RetrofitClient;

import java.util.List;

/**
 * Encapsulates cloud user discovery and friend request operations.
 */
public class FriendDiscoveryManager {

    private final RetrofitClient retrofitClient;

    public FriendDiscoveryManager(@NonNull Context context) {
        this.retrofitClient = RetrofitClient.getInstance(context.getApplicationContext());
    }

    @NonNull
    public List<UserSearchResult> searchUsers(@NonNull String query) {
        return retrofitClient.getFriendServiceApi()
            .searchUsers(query)
            .blockingFirst();
    }

    public void sendFriendRequest(@NonNull String toUserId, String message) {
        SendFriendRequestRequest request = new SendFriendRequestRequest(toUserId, message);
        retrofitClient.getFriendServiceApi()
            .sendFriendRequest(request)
            .blockingFirst();
    }
}
