package com.agent.hopaw.infra.constant;

/**
 * 项目操作日志类型枚举（独立于任务评论类型）
 */
public enum ProjectLogTypeEnum {
    DEFAULT("default", "默认"),
    IMPORTANT("important", "重点"),
    ;

    private final String code;
    private final String description;

    ProjectLogTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isImportant() {
        return this == IMPORTANT;
    }

    /** 按存储值解析枚举，未匹配（含 null 旧数据）返回 DEFAULT */
    public static ProjectLogTypeEnum fromCode(String code) {
        if (code != null) {
            for (ProjectLogTypeEnum type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
        }
        return DEFAULT;
    }
}
