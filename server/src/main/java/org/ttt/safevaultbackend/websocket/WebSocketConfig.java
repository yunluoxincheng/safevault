package org.ttt.safevaultbackend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * WebSocket允许的域名白名单
     * 安全加固：仅允许特定域名连接，防止恶意WebSocket连接
     */
    private static final String[] ALLOWED_ORIGINS = {
            "https://safevaultapp.top",  // 生产环境域名
            "https://server.safevaultapp.top",  // 后端生产域名
            "safevault://"               // Deep link scheme（用于应用内跳转）
    };

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单消息代理
        config.enableSimpleBroker("/topic", "/queue");
        // 设置应用目标前缀
        config.setApplicationDestinationPrefixes("/app");
        // 设置用户目标前缀
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 纯WebSocket端点（不使用SockJS）- 用于原生WebSocket客户端（如Android OkHttp）
        // 安全加固：限制允许的来源域名
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ALLOWED_ORIGINS);

        // SockJS端点（用于降级支持）- 用于浏览器等需要降级的客户端
        // 安全加固：限制允许的来源域名
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(ALLOWED_ORIGINS)
                .withSockJS();
    }
}
