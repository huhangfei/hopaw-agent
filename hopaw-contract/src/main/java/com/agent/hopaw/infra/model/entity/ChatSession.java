package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

public class ChatSession {
    private Long id;
    private String sessionId;
    private Long agentId;
    private String userId;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdateTime;
    private Boolean enableThinking;
    private String skillNames;
    private Long aiModelId;
    public ChatSession() {}

    public ChatSession(String sessionId, String userId, String title) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.title = title;
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
    }
    public ChatSession(String sessionId, Long agentId, String userId, String title) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.userId = userId;
        this.title = title;
        this.createTime = LocalDateTime.now();
        this.lastUpdateTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(LocalDateTime lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }

    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    public String getSkillNames() {
        return skillNames;
    }

    public void setSkillNames(String skillNames) {
        this.skillNames = skillNames;
    }

    public Long getAiModelId() {
        return aiModelId;
    }

    public void setAiModelId(Long aiModelId) {
        this.aiModelId = aiModelId;
    }
}
