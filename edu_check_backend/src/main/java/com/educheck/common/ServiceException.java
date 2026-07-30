package com.educheck.common;

/**
 * 业务异常 — 在 Service/Controller 层主动抛出，由 GlobalExceptionHandler 统一处理
 * 携带 code 状态码，支持返回 400/403/404/500 等 HTTP 语义
 */
public class ServiceException extends RuntimeException {
    private final int code;

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }

    public int getCode() {
        return code;
    }
}
