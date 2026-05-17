package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.constant.ResultCodeEnum;

/**
 * 返回结果工具类
 */
public class ResponseBean {

    /**
     * 状态码
     */
    private Integer code;
    /**
     * 错误消息
     */
    private String msg;
    /**
     * data
     */
    private Object data;


    public ResponseBean(Integer code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功
     *
     * @param result 集合
     * @return
     */
    public static ResponseBean success(Object result) {
        return new ResponseBean(ResultCodeEnum.SUCCESS.getCode(), "success", result);
    }

    /**
     * 成功
     *
     * @return
     */
    public static ResponseBean success() {
        return new ResponseBean(ResultCodeEnum.SUCCESS.getCode(), "success", null);
    }

    /**
     * 失败
     *
     * @return
     */
    public static ResponseBean fail() {
        return new ResponseBean(ResultCodeEnum.BIZ_ERROR.getCode(), "fail", null);
    }

    /**
     * 失败返回code和msg
     *
     * @param code
     * @param msg
     * @return
     */
    public static ResponseBean fail(Integer code, String msg) {
        return new ResponseBean(code, msg, null);
    }


    /**
     * 失败,返回错误提示
     *
     * @param msg 错误提示
     * @return
     */
    public static ResponseBean fail(String msg) {
        return new ResponseBean(ResultCodeEnum.BIZ_ERROR.getCode(), msg, null);
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

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}