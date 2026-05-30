package com.agent.hopaw.infra.constant;

/**
 * 用户记忆类型枚举
 * @author hhf
 */
public enum UserMemoryTypeEnum {
    /**
     * 任务记录
     */
    TASK_RECORDS("taskRecords", "任务记录"),
    /**
     * 经验知识
     */
    EMPIRICAL_KNOWLEDGE("empiricalKnowledge", "经验知识"),
    /**
     * 用户画像
     */
    USER_PROFILE("userProfile", "用户画像"),
    /**
     * 聊天历史
     */
    CHAT_HISTORY("chatHistory", "聊天历史");

    private final String code;
    private final String name;
    UserMemoryTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static UserMemoryTypeEnum fromCode(String code) {
        for (UserMemoryTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
