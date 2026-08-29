package com.agent.hopaw.infra.constant;

/**
 * 通知渠道类型枚举：同一类型可配置多个渠道实例。
 */
public enum NotifyChannelTypeEnum {
    DINGTALK("dingtalk", "钉钉群"),
    FEISHU("feishu", "飞书"),
    EMAIL("email", "邮件"),
    WEBHOOK("webhook", "Webhook");

    private final String code;
    private final String description;

    NotifyChannelTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 按编码解析，未匹配返回 null */
    public static NotifyChannelTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (NotifyChannelTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
