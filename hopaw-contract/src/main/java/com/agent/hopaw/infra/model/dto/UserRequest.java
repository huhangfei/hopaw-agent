package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.model.entity.Agent;

import java.util.List;

public class UserRequest {
    private String userId;
    private Long agentId;
    private String message;
    private String sessionId;
    private Long aiModelId;
    private Boolean enableThinking;
    private List<String> skillNames;
    private Agent agent;

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

    public List<String> getSkillNames() {
        return skillNames;
    }

    public void setSkillNames(List<String> skillNames) {
        this.skillNames = skillNames;
    }

    public Agent getAgent() {
        return agent;
    }

    public void setAgent(Agent agent) {
        this.agent = agent;
    }
}
