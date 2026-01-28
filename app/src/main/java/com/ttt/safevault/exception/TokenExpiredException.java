package com.ttt.safevault.exception;

/**
 * Token 过期异常
 * 当 access token 或 refresh token 过期时抛出
 */
public class TokenExpiredException extends AuthenticationException {

    public TokenExpiredException() {
        super("TOKEN_EXPIRED", "Access token has expired. Please refresh or login again.");
    }

    public TokenExpiredException(String message) {
        super("TOKEN_EXPIRED", message);
    }

    public TokenExpiredException(String message, Throwable cause) {
        super("TOKEN_EXPIRED", message, cause);
    }

    public TokenExpiredException(Throwable cause) {
        super("TOKEN_EXPIRED", "Access token has expired. Please refresh or login again.", cause);
    }
}
