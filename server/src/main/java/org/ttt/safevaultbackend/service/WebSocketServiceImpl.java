package org.ttt.safevaultbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.ttt.safevaultbackend.dto.FriendNotificationMessage;
import org.ttt.safevaultbackend.dto.ShareNotificationMessage;
import org.ttt.safevaultbackend.websocket.WebSocketConnectionManager;

/**
 * WebSocket 服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketServiceImpl implements WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketConnectionManager connectionManager;

    @Override
    public void sendShareNotification(String userId, ShareNotificationMessage notification) {
        String destination = "/user/queue/shares";
        log.info("Sending share notification to userId={}: {}", userId, notification);
        messagingTemplate.convertAndSendToUser(userId, destination, notification);
    }

    @Override
    public void broadcastShareNotification(ShareNotificationMessage notification) {
        // 获取所有在线用户
        var onlineUsers = connectionManager.getAllOnlineUsers();

        // 遍历所有在线用户（排除发送者自己）
        for (String userId : onlineUsers.keySet()) {
            // 跳过发送者自己
            if (userId.equals(notification.getFromUserId())) {
                continue;
            }
            sendShareNotification(userId, notification);
        }

        log.info("Broadcasting share notification to {} online users: {}",
                onlineUsers.size() - 1, notification);
    }

    @Override
    public void sendFriendNotification(String userId, FriendNotificationMessage notification) {
        String destination = "/user/queue/friends";
        log.info("Sending friend notification to userId={}: {}", userId, notification);
        messagingTemplate.convertAndSendToUser(userId, destination, notification);
    }

    @Override
    public void handleConnect(String userId, String sessionId) {
        connectionManager.addConnection(userId, sessionId);
        log.info("User connected: userId={}, sessionId={}", userId, sessionId);
    }

    @Override
    public void handleDisconnect(String userId, String sessionId) {
        connectionManager.removeConnection(sessionId);
        log.info("User disconnected: userId={}, sessionId={}", userId, sessionId);
    }

    @Override
    public void handleHeartbeat(String userId) {
        if (connectionManager.isUserOnline(userId)) {
            log.debug("Heartbeat received from userId={}", userId);
        } else {
            log.warn("Heartbeat from offline user: userId={}", userId);
        }
    }
}
