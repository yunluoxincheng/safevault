package com.ttt.safevault.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;
import com.ttt.safevault.R;
import com.ttt.safevault.dto.ShareNotificationMessage;
import com.ttt.safevault.dto.response.FriendDto;
import com.ttt.safevault.network.RetrofitClient;
import com.ttt.safevault.network.TokenManager;
import com.ttt.safevault.network.WebSocketManager;
import com.ttt.safevault.network.api.FriendServiceApi;
import com.ttt.safevault.ui.friend.FriendRequestListActivity;
import com.ttt.safevault.ui.share.ReceiveShareActivity;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * 分享通知服务
 * 维护 WebSocket 连接并处理实时分享通知
 */
public class ShareNotificationService extends Service {

    private static final String TAG = "ShareNotificationService";
    private static final String CHANNEL_ID = "share_notification_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int FRIEND_REQUEST_NOTIFICATION_ID = 1002;
    private static final int FRIEND_ACCEPTED_NOTIFICATION_ID = 1003;
    private static final int FRIEND_DELETED_NOTIFICATION_ID = 1004;
    private static final int SERVICE_NOTIFICATION_ID = 2001;

    private WebSocketManager webSocketManager;
    private TokenManager tokenManager;
    private FriendServiceApi friendServiceApi;
    private Gson gson;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        tokenManager = TokenManager.getInstance(this);
        gson = new Gson();
        webSocketManager = new WebSocketManager(this);
        friendServiceApi = RetrofitClient.getInstance(this).getFriendServiceApi();

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service start command");

        if (!tokenManager.isLoggedIn()) {
            Log.d(TAG, "User not logged in, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (isRunning.compareAndSet(false, true)) {
            // 启动前台服务
            startForeground(SERVICE_NOTIFICATION_ID, createForegroundNotification());

            // 连接 WebSocket
            connectWebSocket();
        }

        return START_STICKY;
    }

    private void connectWebSocket() {
        String token = tokenManager.getAccessToken();
        if (token == null || token.isEmpty()) {
            Log.e(TAG, "No access token available");
            stopSelf();
            return;
        }

        webSocketManager.connect(token, new WebSocketManager.WebSocketEventListener() {
            @Override
            public void onShareNotification(ShareNotificationMessage notification) {
                handleShareNotification(notification);
            }

            @Override
            public void onOnlineUserUpdate(com.ttt.safevault.dto.OnlineUserMessage message) {
                Log.d(TAG, "Online user update: " + message);
            }

            @Override
            public void onConnectionOpened() {
                Log.d(TAG, "WebSocket connection opened");
            }

            @Override
            public void onConnectionClosed() {
                Log.d(TAG, "WebSocket connection closed");
                // WebSocketManager内部会自动重连，不需要手动处理
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "WebSocket error: " + error);
            }

            @Override
            public void onConnectionLost() {
                Log.e(TAG, "WebSocket connection lost after max retries");
                // 最大重连次数后仍然失败，停止服务
                stopSelf();
            }
        });
    }

    private void handleShareNotification(ShareNotificationMessage notification) {
        Log.d(TAG, "Received notification: " + notification.getType());

        String type = notification.getType();

        // 处理好友请求通知
        if ("FRIEND_REQUEST".equals(type)) {
            showFriendRequestNotification(notification);
        } else if ("FRIEND_REQUEST_ACCEPTED".equals(type)) {
            showFriendAcceptedNotification(notification);
            autoSaveContact(notification);
        } else if ("FRIEND_DELETED".equals(type)) {
            showFriendDeletedNotification(notification);
        } else {
            // 处理密码分享通知
            showShareNotification(notification);
        }
    }

    private void showShareNotification(ShareNotificationMessage notification) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 创建点击通知的 Intent
        Intent intent = new Intent(this, ReceiveShareActivity.class);
        intent.putExtra("SHARE_ID", notification.getShareId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 构建通知
        String fromUser = notification.getFromDisplayName() != null ?
            notification.getFromDisplayName() : notification.getFromUserId();
        String contentText = fromUser + " 与您分享了一个密码";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_share)
            .setContentTitle("收到新的密码分享")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);

        // 显示通知
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }

    private Notification createForegroundNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeVault")
            .setContentText("正在接收分享通知...")
            .setSmallIcon(R.drawable.ic_share)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);

        return builder.build();
    }

    /**
     * 显示好友请求通知
     */
    private void showFriendRequestNotification(ShareNotificationMessage notification) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 创建点击跳转到好友请求列表的 Intent
        Intent intent = new Intent(this, FriendRequestListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // 使用 requestId 的 hashCode 作为请求码，避免冲突
        int requestCode = notification.getRequestId() != null ?
            notification.getRequestId().hashCode() : 0;

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 构建通知
        String fromUser = notification.getFromDisplayName() != null ?
            notification.getFromDisplayName() : notification.getFromUserId();
        String contentText = notification.getMessage() != null ?
            notification.getMessage() : (fromUser + " 请求添加你为好友");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_share)
            .setContentTitle("新的好友请求")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        notificationManager.notify(FRIEND_REQUEST_NOTIFICATION_ID, builder.build());
    }

    /**
     * 显示好友请求已接受通知
     */
    private void showFriendAcceptedNotification(ShareNotificationMessage notification) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 点击后打开主界面
        Intent intent = new Intent(this, FriendRequestListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = notification.getRequestId() != null ?
            notification.getRequestId().hashCode() : 0;

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String fromUser = notification.getFromDisplayName() != null ?
            notification.getFromDisplayName() : notification.getFromUserId();
        String contentText = notification.getMessage() != null ?
            notification.getMessage() : (fromUser + " 接受了你的好友请求");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_share)
            .setContentTitle("好友请求已接受")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(FRIEND_ACCEPTED_NOTIFICATION_ID, builder.build());
    }

    /**
     * 显示好友删除通知
     */
    private void showFriendDeletedNotification(ShareNotificationMessage notification) {
        NotificationManager notificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // 点击后打开主界面
        Intent intent = new Intent(this, FriendRequestListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int requestCode = notification.getRequestId() != null ?
            notification.getRequestId().hashCode() : 0;

        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String fromUser = notification.getFromDisplayName() != null ?
            notification.getFromDisplayName() : notification.getFromUserId();
        String contentText = notification.getMessage() != null ?
            notification.getMessage() : (fromUser + " 删除了好友关系");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_share)
            .setContentTitle("好友关系已解除")
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(FRIEND_DELETED_NOTIFICATION_ID, builder.build());
    }

    /**
     * 自动保存联系人到本地
     */
    private void autoSaveContact(ShareNotificationMessage notification) {
        String userId = notification.getFromUserId();

        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "Cannot auto-save contact: userId is null");
            return;
        }

        // 调用后端 API 获取对方的公钥信息
        friendServiceApi.getFriendList()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                friends -> {
                    for (FriendDto friend : friends) {
                        if (friend.getUserId().equals(userId)) {
                            // 保存到本地数据库
                            saveContactToLocal(friend);
                            Log.d(TAG, "Auto-saved contact: " + friend.getDisplayName());
                            break;
                        }
                    }
                },
                error -> {
                    Log.e(TAG, "Failed to fetch friend list for auto-save", error);
                }
            );
    }

    /**
     * 保存联系人到本地数据库
     */
    private void saveContactToLocal(FriendDto friend) {
        // TODO: 实现本地数据库保存逻辑
        // 这里需要使用 BackendService 或 Repository 来保存联系人
        // 示例代码：
        // backendService.saveContact(
        //     friend.getUserId(),
        //     friend.getDisplayName(),
        //     friend.getPublicKey()
        // );

        Log.d(TAG, "Saving contact to local: " + friend.getDisplayName() +
                   " (userId: " + friend.getUserId() + ")");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "分享和好友通知",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("接收新的密码分享通知和好友请求通知");
            channel.enableVibration(true);
            channel.setShowBadge(true);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        isRunning.set(false);

        if (webSocketManager != null) {
            webSocketManager.disconnect();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
