package org.ttt.safevaultbackend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 连接管理器
 */
@Slf4j
@Component
public class WebSocketConnectionManager {

    // 用户ID到会话ID的映射
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

    // 会话ID到用户ID的映射
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();

    /**
     * 添加连接
     */
    public void addConnection(String userId, String sessionId) {
        userSessionMap.put(userId, sessionId);
        sessionUserMap.put(sessionId, userId);
        log.info("WebSocket connection added: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 移除连接
     */
    public void removeConnection(String sessionId) {
        String userId = sessionUserMap.remove(sessionId);
        if (userId != null) {
            userSessionMap.remove(userId);
            log.info("WebSocket connection removed: userId={}, sessionId={}", userId, sessionId);
        }
    }

    /**
     * 获取用户会话ID
     */
    public String getSessionId(String userId) {
        return userSessionMap.get(userId);
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String userId) {
        return userSessionMap.containsKey(userId);
    }

    /**
     * 获取所有在线用户
     */
    public Map<String, String> getAllOnlineUsers() {
        return new ConcurrentHashMap<>(userSessionMap);
    }
}
