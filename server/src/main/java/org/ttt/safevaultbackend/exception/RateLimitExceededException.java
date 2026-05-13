package org.ttt.safevaultbackend.exception;

/**
 * 速率限制异常
 * 安全加固第三阶段：当API请求超过速率限制时抛出
 */
public class RateLimitExceededException extends RuntimeException {

    private final String limitKey;
    private final int maxRequests;
    private final String window;

    public RateLimitExceededException(String message) {
        super(message);
        this.limitKey = null;
        this.maxRequests = 0;
        this.window = null;
    }

    public RateLimitExceededException(String message, String limitKey, int maxRequests, String window) {
        super(message);
        this.limitKey = limitKey;
        this.maxRequests = maxRequests;
        this.window = window;
    }

    public String getLimitKey() {
        return limitKey;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public String getWindow() {
        return window;
    }
}
