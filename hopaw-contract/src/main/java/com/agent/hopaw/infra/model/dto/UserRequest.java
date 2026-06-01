package com.agent.hopaw.infra.model.dto;

import java.util.List;

/**
 * @author hhf
 */
public class UserRequest {
    private String sessionId;
    private String userId;
    private Long agentId;
    private String message;
    private List<String> skillNames;
    private Long aiModelId;
    private Boolean enableThinking;
    /**
     * 工具执行权限
     * user_control 用户控制
     * smart_call 智能调用
     * auto 完全自动
     */
    private String toolCallPermission;
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getSkillNames() {
        return skillNames;
    }

    public void setSkillNames(List<String> skillNames) {
        this.skillNames = skillNames;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getAiModelId() {
        return aiModelId;
    }

    public void setAiModelId(Long aiModelId) {
        this.aiModelId = aiModelId;
    }

    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    public String getToolCallPermission() {
        return toolCallPermission;
    }

    public void setToolCallPermission(String toolCallPermission) {
        this.toolCallPermission = toolCallPermission;
    }
}
