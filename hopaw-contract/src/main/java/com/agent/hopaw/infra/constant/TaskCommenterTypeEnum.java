package com.agent.hopaw.infra.constant;

/**
 * 任务评论者类型枚举
 */
public enum TaskCommenterTypeEnum {
    USER("user", "用户"),
    AGENT("agent", "智能体"),
    ;

    private final String code;
    private final String description;

    TaskCommenterTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /** 是否为智能体评论（兼容旧数据为 null 时按用户处理） */
    public static boolean isAgent(String commenterType) {
        return AGENT.code.equals(commenterType);
    }

    /** 按存储值解析枚举，未匹配（含 null 旧数据）返回 USER */
    public static TaskCommenterTypeEnum fromCode(String code) {
        if (code != null) {
            for (TaskCommenterTypeEnum type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
        }
        return USER;
    }
}
