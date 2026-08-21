package com.agent.hopaw.infra.constant;

/**
 * 任务评论处理状态枚举
 */
public enum TaskCommentStatusEnum {
    PENDING("pending", "待处理"),
    PROCESSED("processed", "已处理"),
    ;

    private final String code;
    private final String description;

    TaskCommentStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 按存储值解析枚举，未匹配返回 null */
    public static TaskCommentStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TaskCommentStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
