package com.ttt.safevault.exception;

/**
 * 网络异常
 * 用于网络连接、超时等错误
 */
public class NetworkException extends RuntimeException {

    private final String errorCode;

    public NetworkException(String message) {
        super(message);
        this.errorCode = "NETWORK_ERROR";
    }

    public NetworkException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "NETWORK_ERROR";
    }

    public NetworkException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
