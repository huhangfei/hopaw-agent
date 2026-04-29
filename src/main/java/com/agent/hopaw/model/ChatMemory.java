package com.agent.hopaw.model;

import java.time.LocalDateTime;

public class ChatMemory {
    private Long id;
    private Long agentId;
    private String messageId;
    private String messageJson;
    private Integer cleaned;
    private LocalDateTime createTime;

    public ChatMemory() {}

    public ChatMemory(Long agentId, String messageId, String messageJson) {
        this.agentId = agentId;
        this.messageId = messageId;
        this.messageJson = messageJson;
        this.cleaned = 0;
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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getMessageJson() {
        return messageJson;
    }

    public void setMessageJson(String messageJson) {
        this.messageJson = messageJson;
    }

    public Integer getCleaned() {
        return cleaned;
    }

    public void setCleaned(Integer cleaned) {
        this.cleaned = cleaned;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
