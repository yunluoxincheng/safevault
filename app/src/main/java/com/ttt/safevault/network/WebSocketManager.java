package com.ttt.safevault.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.ttt.safevault.dto.OnlineUserMessage;
import com.ttt.safevault.dto.ShareNotificationMessage;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket管理器
 * 处理实时分享通知和在线用户更新
 * 支持心跳和自动重连机制
 */
public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static final long HEARTBEAT_INTERVAL = 30000; // 30秒心跳
    private static final int MAX_RECONNECT_ATTEMPTS = 5; // 最大重连次数
    private static final long RECONNECT_DELAY = 5000; // 重连延迟5秒

    private WebSocket webSocket;
    private OkHttpClient client;
    private final Gson gson;
    private WebSocketEventListener eventListener;
    private boolean isConnected = false;
    private boolean isManualClose = false;
    private int reconnectAttempts = 0;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private final Context context;

    public interface WebSocketEventListener {
        void onShareNotification(ShareNotificationMessage notification);
        void onOnlineUserUpdate(OnlineUserMessage message);
        void onConnectionOpened();
        void onConnectionClosed();
        void onError(String error);
        void onConnectionLost(); // 新增：连接丢失回调
    }

    public WebSocketManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.heartbeatHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 连接WebSocket
     * @param token 认证Token
     * @param listener 事件监听器
     */
    public void connect(String token, WebSocketEventListener listener) {
        if (isConnected && webSocket != null) {
            Log.w(TAG, "WebSocket already connected");
            return;
        }

        this.eventListener = listener;
        this.isManualClose = false;
        this.reconnectAttempts = 0;

        // 创建客户端，配置心跳
        client = new OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS)
            .build();

        Request request = new Request.Builder()
            .url(ApiConstants.WS_URL + "?token=" + token)
            .build();

        webSocket = client.newWebSocket(request, new ReconnectWebSocketListener());

        startHeartbeat();
    }

    /**
     * 启动心跳
     */
    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (webSocket != null && isConnected) {
                    // 发送心跳包
                    webSocket.send("{\"type\":\"ping\"}");
                    Log.d(TAG, "Heartbeat sent");
                }
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
    }

    /**
     * 停止心跳
     */
    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
        }
    }

    /**
     * 重连WebSocket
     */
    private void reconnect() {
        if (isManualClose) {
            Log.i(TAG, "Manual close, skip reconnect");
            return;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached");
            if (eventListener != null) {
                eventListener.onConnectionLost();
            }
            return;
        }

        reconnectAttempts++;
        Log.i(TAG, "Reconnecting... attempt " + reconnectAttempts);

        heartbeatHandler.postDelayed(() -> {
            String token = TokenManager.getInstance(context).getAccessToken();
            if (token != null) {
                connect(token, eventListener);
            }
        }, RECONNECT_DELAY);
    }

    /**
     * 断开WebSocket连接
     */
    public void disconnect() {
        isManualClose = true;
        stopHeartbeat();
        if (webSocket != null) {
            webSocket.close(1000, "Manual close");
            webSocket = null;
            isConnected = false;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            client = null;
        }
    }

    /**
     * 发送心跳（手动）
     */
    public void sendHeartbeat() {
        if (webSocket != null && isConnected) {
            webSocket.send("{\"type\":\"ping\"}");
        }
    }

    /**
     * 重连监听器
     */
    private class ReconnectWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            isConnected = true;
            reconnectAttempts = 0; // 重置重连次数
            Log.d(TAG, "WebSocket connected");
            if (eventListener != null) {
                eventListener.onConnectionOpened();
            }
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            Log.d(TAG, "WebSocket message received: " + text);
            handleMessage(text);
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket closing: " + reason);
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            isConnected = false;
            Log.d(TAG, "WebSocket closed: " + reason);
            stopHeartbeat();

            if (!isManualClose) {
                reconnect();
            }

            if (eventListener != null) {
                eventListener.onConnectionClosed();
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
            isConnected = false;
            Log.e(TAG, "WebSocket failure", t);
            stopHeartbeat();

            if (!isManualClose) {
                reconnect();
            }

            if (eventListener != null) {
                eventListener.onError(t.getMessage());
            }
        }
    }

    /**
     * 处理收到的消息
     */
    private void handleMessage(String message) {
        try {
            // 简单解析消息类型
            if (message.contains("\"type\":\"SHARE_NOTIFICATION\"") ||
                message.contains("\"type\":\"NEW_SHARE\"") ||
                message.contains("\"type\":\"NEW_DIRECT_SHARE\"") ||
                message.contains("\"type\":\"SHARE_REVOKED\"") ||
                message.contains("\"type\":\"FRIEND_REQUEST\"") ||
                message.contains("\"type\":\"FRIEND_REQUEST_ACCEPTED\"") ||
                message.contains("\"type\":\"FRIEND_DELETED\"")) {
                ShareNotificationMessage notification = gson.fromJson(message, ShareNotificationMessage.class);
                if (eventListener != null) {
                    eventListener.onShareNotification(notification);
                }
            } else if (message.contains("\"type\":\"ONLINE_USER\"")) {
                OnlineUserMessage userMessage = gson.fromJson(message, OnlineUserMessage.class);
                if (eventListener != null) {
                    eventListener.onOnlineUserUpdate(userMessage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse WebSocket message", e);
        }
    }

    public boolean isConnected() {
        return isConnected;
    }
}
