package com.agent.hopaw.model;

import java.time.LocalDateTime;

public class ChatHistory {
    private Long id;
    private Long agentId;
    private String role;
    private String content;
    private LocalDateTime createTime;

    public ChatHistory() {}

    public ChatHistory(Long agentId, String role, String content) {
        this.agentId = agentId;
        this.role = role;
        this.content = content;
        this.createTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
