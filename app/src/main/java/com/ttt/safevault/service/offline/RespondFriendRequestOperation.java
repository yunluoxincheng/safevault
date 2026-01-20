package com.ttt.safevault.service.offline;

import android.content.Context;
import android.util.Log;

import com.ttt.safevault.dto.request.RespondFriendRequestRequest;
import com.ttt.safevault.network.RetrofitClient;

import io.reactivex.rxjava3.core.Observable;

/**
 * 响应好友请求操作
 */
public class RespondFriendRequestOperation extends OfflineOperation {
    private static final String TAG = "RespondFriendRequestOperation";

    private final Context context;
    private String requestId;
    private boolean accept;

    public RespondFriendRequestOperation(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean execute() throws Exception {
        RetrofitClient retrofitClient = RetrofitClient.getInstance(context);

        RespondFriendRequestRequest request = new RespondFriendRequestRequest();
        request.setAccept(accept);

        try {
            Observable<Void> observable = retrofitClient
                .getFriendServiceApi()
                .respondToFriendRequest(requestId, request);

            observable.blockingFirst();
            Log.i(TAG, "Friend request responded: " + requestId + ", accept=" + accept);
            return true;
        } catch (Exception e) {
            incrementRetry();
            Log.e(TAG, "Failed to respond to friend request: " + requestId, e);
            throw e;
        }
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public boolean isAccept() {
        return accept;
    }

    public void setAccept(boolean accept) {
        this.accept = accept;
    }
}
