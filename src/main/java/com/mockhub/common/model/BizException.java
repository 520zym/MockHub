package com.mockhub.common.model;

/**
 * 业务异常，由全局异常处理器捕获并转为 Result.error(code, msg)
 */
public class BizException extends RuntimeException {

    private final int code;
    private final String msg;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
