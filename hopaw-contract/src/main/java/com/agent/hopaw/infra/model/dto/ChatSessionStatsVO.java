package com.agent.hopaw.infra.model.dto;

import java.time.LocalDateTime;

/**
 * 会话清理设置页的会话统计行：基础信息 + 消息记录数量
 */
public class ChatSessionStatsVO {
    private Long id;
    private String sessionId;
    private String title;
    /** 业务类型：chat(空) / task / project */
    private String bizType;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdateTime;
    /** 消息记录数量 */
    private long messageCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(LocalDateTime lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    public long getMessageCount() { return messageCount; }
    public void setMessageCount(long messageCount) { this.messageCount = messageCount; }
}
