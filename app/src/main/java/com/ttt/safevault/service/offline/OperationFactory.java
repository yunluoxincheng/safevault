package com.ttt.safevault.service.offline;

import android.content.Context;

import com.google.gson.Gson;

/**
 * 离线操作工厂类
 */
public class OperationFactory {
    private static final Gson gson = new Gson();

    /**
     * 根据数据创建操作实例
     */
    public static OfflineOperation createOperation(OfflineOperationData data) {
        switch (data.getType()) {
            case "FRIEND_REQUEST":
                return gson.fromJson(data.getData(), FriendRequestOperation.class);
            case "SHARE_PASSWORD":
                return gson.fromJson(data.getData(), SharePasswordOperation.class);
            case "RESPOND_FRIEND_REQUEST":
                return gson.fromJson(data.getData(), RespondFriendRequestOperation.class);
            default:
                return null;
        }
    }

    /**
     * 将操作转换为数据
     */
    public static OfflineOperationData toData(OfflineOperation operation) {
        String json = gson.toJson(operation);
        return new OfflineOperationData(
            operation.getType(),
            operation.getTimestamp(),
            operation.getRetryCount(),
            json
        );
    }

    /**
     * 创建好友请求操作
     */
    public static FriendRequestOperation createFriendRequest(
            Context context, String toUserId, String message) {
        FriendRequestOperation op = new FriendRequestOperation(context);
        op.setToUserId(toUserId);
        op.setMessage(message);
        op.setType("FRIEND_REQUEST");
        return op;
    }

    /**
     * 创建分享密码操作
     */
    public static SharePasswordOperation createSharePassword(
            Context context, String toUserId, int passwordId,
            int expireInMinutes, String permissionJson) {
        SharePasswordOperation op = new SharePasswordOperation(context);
        op.setToUserId(toUserId);
        op.setPasswordId(passwordId);
        op.setExpireInMinutes(expireInMinutes);
        op.setPermissionJson(permissionJson);
        op.setType("SHARE_PASSWORD");
        return op;
    }

    /**
     * 创建响应好友请求操作
     */
    public static RespondFriendRequestOperation createRespondFriendRequest(
            Context context, String requestId, boolean accept) {
        RespondFriendRequestOperation op = new RespondFriendRequestOperation(context);
        op.setRequestId(requestId);
        op.setAccept(accept);
        op.setType("RESPOND_FRIEND_REQUEST");
        return op;
    }
}
