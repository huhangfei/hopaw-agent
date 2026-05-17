package com.agent.hopaw.infra.constant;

/**
 * @author hhf
 */

public enum  AiModelCallSourceEnum {
    Chat("chat", "会话"),
    ModelTEST("model-test", "模型测试"),
    MEMORYORGANIZE("memory-organize", "记忆整理"),
    AgentTask("agentTask", "智能体定时任务");

    public String getValue() {
        return value;
    }
    public String getDescription() {
        return description;
    }
    private String value;
    private String description;
    AiModelCallSourceEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }
}
