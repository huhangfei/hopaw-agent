package com.agent.hopaw.infra.constant;

/**
 * 返回状态码定义
 */
public enum ResultCodeEnum {

    SUCCESS(200, "success"),
    ERROR(500, "error"),
    BIZ_ERROR(800, "业务异常");

    private Integer code;

    private String msg;

    ResultCodeEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
