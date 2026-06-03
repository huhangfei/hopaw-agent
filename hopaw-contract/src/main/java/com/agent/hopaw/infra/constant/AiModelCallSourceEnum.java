package com.agent.hopaw.infra.constant;

/**
 * @author hhf
 */

public enum  AiModelCallSourceEnum {
    Chat("chat", "会话"),
    ChatToolCallCheck("chat-tool-call-check", "会话工具调用检测"),
    ChatAnalyzeUserIntent("chat-analyze-user-intent", "会话分析用户意图"),
    AvatarTask("avatar-task", "虚拟人任务"),
    ModelTest("model-test", "模型测试"),
    MemoryOrganize("memory-organize", "记忆整理"),
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
