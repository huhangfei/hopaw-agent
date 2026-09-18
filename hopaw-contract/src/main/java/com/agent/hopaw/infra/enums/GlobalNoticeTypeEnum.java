package com.agent.hopaw.infra.enums;

/**
 * 全局通知消息类型枚举
 */
public enum GlobalNoticeTypeEnum {

    /** 项目消息 */
    PROJECT("project"),
    /** 任务消息 */
    TASK("task");

    private final String code;

    GlobalNoticeTypeEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static GlobalNoticeTypeEnum fromCode(String code) {
        for (GlobalNoticeTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
