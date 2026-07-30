package com.educheck.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应结果封装 — 所有 Controller 通过此类返回数据
 * 前端 request.js 解析 { code, message, data } 结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 状态码: 200成功 4xx客户端错误 5xx服务端错误 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 泛型响应数据 */
    private T data;

    /** 成功响应（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }

    /** 空成功响应（仅提示信息） */
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }

    /** 成功响应（自定义消息 + 数据） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 失败响应（自定义状态码和消息） */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败响应（默认500） */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }

    /** 401未登录 */
    public static <T> Result<T> unauthorized(String message) {
        return new Result<>(401, message, null);
    }

    /** 403无权限 */
    public static <T> Result<T> forbidden(String message) {
        return new Result<>(403, message, null);
    }
}
