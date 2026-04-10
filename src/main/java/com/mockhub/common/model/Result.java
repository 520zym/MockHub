package com.mockhub.common.model;

/**
 * 统一响应格式
 * 成功：{"code": 0, "msg": "success", "data": {...}}
 * 失败：{"code": 40001, "msg": "用户名或密码错误", "data": null}
 */
public class Result<T> {

    private int code;
    private String msg;
    private T data;

    private Result() {
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = new Result<T>();
        result.code = 0;
        result.msg = "success";
        result.data = data;
        return result;
    }

    public static <T> Result<T> ok() {
        Result<T> result = new Result<T>();
        result.code = 0;
        result.msg = "success";
        result.data = null;
        return result;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> result = new Result<T>();
        result.code = code;
        result.msg = msg;
        result.data = null;
        return result;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
