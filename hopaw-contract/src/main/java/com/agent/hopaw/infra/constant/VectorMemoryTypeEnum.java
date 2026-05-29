package com.agent.hopaw.infra.constant;

/**
 * 向量记忆类型枚举
 * @author hhf
 */
public enum VectorMemoryTypeEnum {
    /**
     * 任务记录:区分智能体
     */
    TASK_RECORDS("taskRecords", "任务记录"),
    /**
     * 聊天历史:区分智能体
     */
    CHAT_HISTORY("chatHistory", "聊天历史"),
    /**
     * 经验知识
     */
    EMPIRICAL_KNOWLEDGE("empiricalKnowledge", "经验知识"),
    /**
     * 用户画像
     */
    USER_PROFILE("userProfile", "用户画像");

    private final String code;
    private final String name;
    VectorMemoryTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }
    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static VectorMemoryTypeEnum fromCode(String code) {
        for (VectorMemoryTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
