package com.ttt.safevault.service.offline;

import android.content.Context;
import android.util.Log;

import com.ttt.safevault.dto.request.SendFriendRequestRequest;
import com.ttt.safevault.dto.response.FriendRequestResponse;
import com.ttt.safevault.network.RetrofitClient;

import io.reactivex.rxjava3.core.Observable;

/**
 * 好友请求操作
 */
public class FriendRequestOperation extends OfflineOperation {
    private static final String TAG = "FriendRequestOperation";

    private final Context context;
    private String toUserId;
    private String message;

    public FriendRequestOperation(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean execute() throws Exception {
        RetrofitClient retrofitClient = RetrofitClient.getInstance(context);

        SendFriendRequestRequest request = new SendFriendRequestRequest();
        request.setToUserId(toUserId);
        request.setMessage(message);

        try {
            Observable<FriendRequestResponse> observable = retrofitClient
                .getFriendServiceApi()
                .sendFriendRequest(request);

            FriendRequestResponse response = observable.blockingFirst();
            boolean success = response != null && response.getRequestId() != null;

            if (success) {
                Log.i(TAG, "Friend request sent successfully to: " + toUserId);
            } else {
                Log.w(TAG, "Friend request failed to: " + toUserId);
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
