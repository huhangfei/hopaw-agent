package com.agent.hopaw.infra.constant;

/**
 * 智能体执行业务类型枚举
 */
public enum AgentExecutorBizTypeEnum {
    Chat("chat", "聊天", AiModelCallSourceEnum.Chat),
    WorkflowTaskChat("workflowTaskChat", "工作流任务", AiModelCallSourceEnum.WorkflowTaskChat);
    private String value;
    private String description;
    private AiModelCallSourceEnum aiModelCallSourceEnum;
    AgentExecutorBizTypeEnum(String value, String description, AiModelCallSourceEnum aiModelCallSourceEnum) {
        this.value = value;
        this.description = description;
        this.aiModelCallSourceEnum = aiModelCallSourceEnum;
    }

    public String getValue() {
        return value;
    }
    public String getDescription() {
        return description;
    }
    public AiModelCallSourceEnum getAiModelCallSourceEnum() {
        return aiModelCallSourceEnum;
    }

}
