package com.agent.hopaw.infra.constant;

/**
 * 任务评论类型枚举
 */
public enum TaskCommentTypeEnum {
    DEFAULT("default", "普通"),
    SUMMARY("summary", "总结"),
    ;

    private final String code;
    private final String description;

    TaskCommentTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSummary() {
        return this == SUMMARY;
    }

    /** 按存储值解析枚举，未匹配（含 null 旧数据）返回 DEFAULT */
    public static TaskCommentTypeEnum fromCode(String code) {
        if (code != null) {
            for (TaskCommentTypeEnum type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
        }
        return DEFAULT;
    }
}
