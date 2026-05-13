package org.ttt.safevaultbackend.service;

import org.ttt.safevaultbackend.dto.FriendNotificationMessage;
import org.ttt.safevaultbackend.dto.ShareNotificationMessage;

/**
 * WebSocket 服务接口
 */
public interface WebSocketService {

    /**
     * 发送分享通知给指定用户
     */
    void sendShareNotification(String userId, ShareNotificationMessage notification);

    /**
     * 广播分享通知给所有用户
     */
    void broadcastShareNotification(ShareNotificationMessage notification);

    /**
     * 发送好友通知给指定用户
     */
    void sendFriendNotification(String userId, FriendNotificationMessage notification);

    /**
     * 处理连接
     */
    void handleConnect(String userId, String sessionId);

    /**
     * 处理断开
     */
    void handleDisconnect(String userId, String sessionId);

    /**
     * 处理心跳
     */
    void handleHeartbeat(String userId);
}
