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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * WebSocket管理器 - 使用STOMP协议
 * 处理实时分享通知和在线用户更新
 * 支持心跳和自动重连机制
 */
public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final long RECONNECT_DELAY = 5000;
    private static final long HEARTBEAT_INTERVAL = 30000; // 30秒

    private WebSocket webSocket;
    private OkHttpClient client;
    private final Gson gson;
    private WebSocketEventListener eventListener;
    private boolean isConnected = false;
    private boolean isManualClose = false;
    private int reconnectAttempts = 0;
    private final Handler reconnectHandler;
    private final Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private final Context context;
    private String currentToken;

    public interface WebSocketEventListener {
        void onShareNotification(ShareNotificationMessage notification);
        void onOnlineUserUpdate(OnlineUserMessage message);
        void onConnectionOpened();
        void onConnectionClosed();
        void onError(String error);
        void onConnectionLost();
    }

    public WebSocketManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.reconnectHandler = new Handler(Looper.getMainLooper());
        this.heartbeatHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 连接WebSocket (使用STOMP协议)
     */
    public void connect(String token, WebSocketEventListener listener) {
        if (isConnected && webSocket != null) {
            Log.w(TAG, "WebSocket already connected");
            return;
        }

        this.eventListener = listener;
        this.isManualClose = false;
        this.reconnectAttempts = 0;
        this.currentToken = token;

        // 创建配置了SSL的OkHttpClient
        client = SslUtils.createOkHttpClient(0, 0);

        // 构建WebSocket请求
        Request request = new Request.Builder()
                .url(ApiConstants.WS_URL)
                .build();

        webSocket = client.newWebSocket(request, new StompWebSocketListener());
    }

    /**
     * WebSocket监听器 - 处理STOMP协议
     */
    private class StompWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
            Log.d(TAG, "WebSocket connection opened");
            // 发送STOMP CONNECT帧
            sendStompConnect();
        }

        @Override
        public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
            Log.d(TAG, "Raw message received: " + text);
            handleStompMessage(text);
        }

        @Override
        public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket closing: " + reason);
        }

        @Override
        public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
            Log.d(TAG, "WebSocket closed: " + reason);
            isConnected = false;
            stopHeartbeat();
            if (!isManualClose) {
                scheduleReconnect();
            }
            if (eventListener != null) {
                eventListener.onConnectionClosed();
            }
        }

        @Override
        public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
            Log.e(TAG, "WebSocket failure", t);
            isConnected = false;
            stopHeartbeat();
            if (!isManualClose) {
                scheduleReconnect();
            }
            if (eventListener != null) {
                eventListener.onError(t.getMessage());
            }
        }
    }

    /**
     * 发送STOMP CONNECT帧
     */
    private void sendStompConnect() {
        String connectFrame = "CONNECT\n" +
                "accept-version:1.2,1.1,1.0\n" +
                "heart-beat:30000,30000\n" +
                "Authorization:" + currentToken + "\n" +
                "\n\u0000";
        webSocket.send(connectFrame);
        Log.d(TAG, "STOMP CONNECT frame sent");
    }

    /**
     * 订阅主题
     */
    private void subscribeToTopics(String userId) {
        // 订阅用户特定的分享通知topic
        String subscribeShares = "SUBSCRIBE\n" +
                "id:sub-0\n" +
                "destination:/user/" + userId + "/queue/shares\n" +
                "\n\u0000";
        webSocket.send(subscribeShares);
        Log.d(TAG, "Subscribed to /user/" + userId + "/queue/shares");

        // 订阅用户特定的在线用户更新topic
        String subscribeOnlineUsers = "SUBSCRIBE\n" +
                "id:sub-1\n" +
                "destination:/user/" + userId + "/queue/online-users\n" +
                "\n\u0000";
        webSocket.send(subscribeOnlineUsers);
        Log.d(TAG, "Subscribed to /user/" + userId + "/queue/online-users");

        // 订阅公共的在线用户topic
        String subscribePublicOnline = "SUBSCRIBE\n" +
                "id:sub-2\n" +
                "destination:/topic/online-users\n" +
                "\n\u0000";
        webSocket.send(subscribePublicOnline);
        Log.d(TAG, "Subscribed to /topic/online-users");
    }

    /**
     * 处理STOMP消息
     */
    private void handleStompMessage(String message) {
        if (message.startsWith("CONNECTED")) {
            Log.d(TAG, "STOMP connected successfully");
            isConnected = true;
            reconnectAttempts = 0;

            // 订阅主题
            String userId = TokenManager.getInstance(context).getUserId();
            if (userId != null) {
                subscribeToTopics(userId);
            }

            // 启动心跳
            startHeartbeat();

            if (eventListener != null) {
                eventListener.onConnectionOpened();
            }
        } else if (message.startsWith("MESSAGE")) {
            handleStompDataMessage(message);
        } else if (message.startsWith("ERROR")) {
            Log.e(TAG, "STOMP error: " + message);
        }
    }

    /**
     * 处理STOMP数据消息
     */
    private void handleStompDataMessage(String message) {
        try {
            // STOMP MESSAGE格式:
            // MESSAGE
            // destination:/user/xxx/queue/shares
            // content-type:application/json
            //
            // {JSON payload}\u0000

            String payload = extractStompPayload(message);
            if (payload == null || payload.isEmpty()) {
                return;
            }

            // 根据destination判断消息类型
            if (message.contains("/queue/shares")) {
                ShareNotificationMessage notification = gson.fromJson(payload, ShareNotificationMessage.class);
                if (eventListener != null) {
                    eventListener.onShareNotification(notification);
                }
            } else if (message.contains("/queue/online-users") || message.contains("/topic/online-users")) {
                OnlineUserMessage userMessage = gson.fromJson(payload, OnlineUserMessage.class);
                if (eventListener != null) {
                    eventListener.onOnlineUserUpdate(userMessage);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse STOMP message", e);
        }
    }

    /**
     * 从STOMP消息中提取payload
     */
    private String extractStompPayload(String stompMessage) {
        // STOMP消息格式：headers空行payload\u0000
        int doubleNewlineIndex = stompMessage.indexOf("\n\n");
        if (doubleNewlineIndex == -1) {
            return null;
        }

        String payload = stompMessage.substring(doubleNewlineIndex + 2);
        // 移除结尾的\u0000
        if (payload.endsWith("\u0000")) {
            payload = payload.substring(0, payload.length() - 1);
        }
        return payload;
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
                    // STOMP心跳是一个换行符
                    webSocket.send("\n");
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
     * 安排重连
     */
    private void scheduleReconnect() {
        if (isManualClose) {
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
        Log.i(TAG, "Scheduling reconnect... attempt " + reconnectAttempts);

        reconnectHandler.postDelayed(() -> {
            String token = TokenManager.getInstance(context).getAccessToken();
            if (token != null) {
                disconnect();
                connect(token, eventListener);
            }
        }, RECONNECT_DELAY);
    }

    /**
     * 断开WebSocket连接
     */
    public void disconnect() {
        isManualClose = true;
        isConnected = false;
        stopHeartbeat();

        if (webSocket != null) {
            // 发送DISCONNECT帧
            try {
                webSocket.send("DISCONNECT\n\u0000");
            } catch (Exception e) {
                Log.e(TAG, "Error sending DISCONNECT", e);
            }
            webSocket.close(1000, "Manual close");
            webSocket = null;
        }

        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            client = null;
        }

        reconnectHandler.removeCallbacksAndMessages(null);
    }

    /**
     * 发送心跳（手动）
     */
    public void sendHeartbeat() {
        if (webSocket != null && isConnected) {
            webSocket.send("\n");
        }
    }

    public boolean isConnected() {
        return isConnected && webSocket != null;
    }
}
