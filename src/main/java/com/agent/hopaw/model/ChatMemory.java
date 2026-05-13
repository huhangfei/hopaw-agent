package com.agent.hopaw.model;

import java.time.LocalDateTime;

public class ChatMemory {
    private Long id;
    private Long agentId;
    private String userId;
    private String messageId;
    private String messageJson;
    /**
     * 状态 0 未清理，1 已过期等待确认是否整理的，2 主动丢弃和待整理记忆后删除，3 已确认需要整理记忆后删除
     */
    private Integer status;
    private LocalDateTime createTime;

    public ChatMemory() {}

    public ChatMemory(Long agentId, String userId, String messageId, String messageJson) {
        this.agentId = agentId;
        this.userId = userId;
        this.messageId = messageId;
        this.messageJson = messageJson;
        this.status = 0;
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
