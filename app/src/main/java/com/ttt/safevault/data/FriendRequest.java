package com.ttt.safevault.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 好友请求实体
 */
@Entity(tableName = "friend_requests")
public class FriendRequest {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "request_id")
    public String requestId;

    @ColumnInfo(name = "from_user_id")
    @NonNull
    public String fromUserId;

    @ColumnInfo(name = "from_username")
    @NonNull
    public String fromUsername;

    @ColumnInfo(name = "from_display_name")
    @NonNull
    public String fromDisplayName;

    @ColumnInfo(name = "from_public_key")
    @NonNull
    public String fromPublicKey;

    @ColumnInfo(name = "message")
    public String message;

    @ColumnInfo(name = "status")
    @NonNull
    public String status;  // PENDING, ACCEPTED, REJECTED

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @ColumnInfo(name = "responded_at")
    public long respondedAt;

    public FriendRequest() {
        this.createdAt = System.currentTimeMillis();
        this.respondedAt = 0;
        this.status = "PENDING";
    }

    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public void setFromUserId(String fromUserId) {
        this.fromUserId = fromUserId;
    }

    public String getFromUsername() {
        return fromUsername;
    }

    public void setFromUsername(String fromUsername) {
        this.fromUsername = fromUsername;
    }

    public String getFromDisplayName() {
        return fromDisplayName;
    }

    public void setFromDisplayName(String fromDisplayName) {
        this.fromDisplayName = fromDisplayName;
    }

    public String getFromPublicKey() {
        return fromPublicKey;
    }

    public void setFromPublicKey(String fromPublicKey) {
        this.fromPublicKey = fromPublicKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(long respondedAt) {
        this.respondedAt = respondedAt;
    }
}
