package com.ttt.safevault.network;

/**
 * API常量定义
 */
public class ApiConstants {
    // 基础URL - 根据实际部署修改
    // public static final String BASE_URL = "http://10.0.2.2:8080/api/";  // Android模拟器访问本地服务器
    // public static final String BASE_URL = "http://172.17.176.22:8080/api/";  // 真机访问局域网服务器
     public static final String BASE_URL = "https://frp-ski.com:41751/api/";  // 生产环境
    
    // WebSocket URL
    // 注意：后端context-path是 /api，所以完整路径是 /api/ws
    //public static final String WS_URL = "ws://10.0.2.2:8080/api/ws";  // Android模拟器访问本地服务器
    //public static final String WS_URL = "ws://172.17.176.22:8080/api/ws";  // 真机访问局域网服务器
    public static final String WS_URL = "wss://frp-ski.com:41751/api/ws";  // 生产环境
    
    // 认证端点
    public static final String AUTH_REGISTER = "v1/auth/register";
    public static final String AUTH_LOGIN = "v1/auth/login";
    public static final String AUTH_REFRESH = "v1/auth/refresh";
    
    // 分享端点
    public static final String SHARES_CREATE = "v1/shares";
    public static final String SHARES_RECEIVE = "v1/shares/{shareId}";
    public static final String SHARES_REVOKE = "v1/shares/{shareId}/revoke";
    public static final String SHARES_SAVE = "v1/shares/{shareId}/save";
    public static final String SHARES_CREATED = "v1/shares/created";
    public static final String SHARES_RECEIVED = "v1/shares/received";
    
    // 附近发现端点
    public static final String DISCOVERY_REGISTER = "v1/discovery/register";
    public static final String DISCOVERY_NEARBY = "v1/discovery/nearby";
    public static final String DISCOVERY_HEARTBEAT = "v1/discovery/heartbeat";
    
    // 用户端点
    public static final String USER_PROFILE = "v1/users/profile";
    public static final String USER_SEARCH = "v1/users/search";
    
    // 请求头
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    
    // 超时时间（秒）
    public static final int CONNECT_TIMEOUT = 30;
    public static final int READ_TIMEOUT = 30;
    public static final int WRITE_TIMEOUT = 30;
}

