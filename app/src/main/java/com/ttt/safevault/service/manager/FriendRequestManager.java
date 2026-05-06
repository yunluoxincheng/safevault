package com.ttt.safevault.service.manager;

import android.content.Context;

import androidx.annotation.NonNull;

import com.ttt.safevault.data.AppDatabase;
import com.ttt.safevault.data.FriendRequest;
import com.ttt.safevault.data.FriendRequestDao;
import com.ttt.safevault.dto.request.RespondFriendRequestRequest;
import com.ttt.safevault.dto.response.FriendRequestDto;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.service.ContactSyncManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates friend request cloud/local orchestration.
 */
public class FriendRequestManager {

    private final RetrofitClient retrofitClient;
    private final ContactSyncManager contactSyncManager;
    private final FriendRequestDao friendRequestDao;

    public FriendRequestManager(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.retrofitClient = RetrofitClient.getInstance(appContext);
        this.contactSyncManager = new ContactSyncManager(appContext);
        this.friendRequestDao = AppDatabase.getInstance(appContext).friendRequestDao();
    }

    @NonNull
    public List<FriendRequest> fetchAndCachePendingRequests() {
        List<FriendRequestDto> dtos = retrofitClient.getFriendServiceApi()
            .getPendingRequests()
            .blockingFirst();
        return mapAndPersistRequests(dtos);
    }

    @NonNull
    public List<FriendRequest> getPendingRequestsFromLocal() {
        return friendRequestDao.getPendingRequests();
    }

    public void respondToRequest(@NonNull String requestId, boolean accept) {
        RespondFriendRequestRequest req = new RespondFriendRequestRequest();
        req.setAccept(accept);
        retrofitClient.getFriendServiceApi()
            .respondToFriendRequest(requestId, req)
            .blockingSubscribe();

        friendRequestDao.updateRequestStatus(
            requestId,
            accept ? "ACCEPTED" : "REJECTED",
            System.currentTimeMillis()
        );
    }

    @NonNull
    public ContactSyncManager.SyncResult syncContactsAfterAccept() {
        return contactSyncManager.syncContacts().blockingFirst();
    }

    @NonNull
    private List<FriendRequest> mapAndPersistRequests(@NonNull List<FriendRequestDto> dtos) {
        List<FriendRequest> requests = new ArrayList<>();
        for (FriendRequestDto dto : dtos) {
            FriendRequest request = new FriendRequest();
            request.requestId = dto.getRequestId();
            request.fromUserId = dto.getFromUserId() == null ? "" : dto.getFromUserId();
            request.fromUsername = dto.getFromUsername() == null ? "" : dto.getFromUsername();
            request.fromDisplayName = dto.getFromDisplayName() == null ? "" : dto.getFromDisplayName();
            request.message = dto.getMessage();
            request.status = dto.getStatus() == null ? "PENDING" : dto.getStatus();
            request.createdAt = dto.getCreatedAt() == null ? System.currentTimeMillis() : dto.getCreatedAt();
            request.respondedAt = dto.getRespondedAt() == null ? 0L : dto.getRespondedAt();
            request.fromPublicKey = "";
            requests.add(request);
            friendRequestDao.insertFriendRequest(request);
        }
        return requests;
    }
}
