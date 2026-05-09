package com.agent.hopaw.constant;

/**
 * 长期记忆类型分类枚举
 * 用户画像 userProfile：姓名、昵称、年龄、地域、职业、收入、常用设备信息、喜好、交流风格、偏好与厌恶、经常提的要求规则
 * 任务记录 taskRecords：正在做的什么事情（开始时间、任务说明、任务过程主要节点、结果，结束时间）
 * 扩展知识 expandKnowledge：用户或智能体写入的知识资料
 * 全量历史 allHistory：全量对话历史、归档的行为画像、归档的任务记录
 */
public enum LongTermMemoryTypeEnum {
    userProfile("userProfile", "用户画像"),
    taskRecords("taskRecords", "任务记录"),
    expandKnowledge("expandKnowledge", "扩展知识"),
    allHistory("allHistory", "全量历史");

    private final String code;
    private final String name;

    LongTermMemoryTypeEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static LongTermMemoryTypeEnum fromCode(String code) {
        for (LongTermMemoryTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
