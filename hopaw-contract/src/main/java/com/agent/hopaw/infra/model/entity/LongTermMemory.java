package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * @author hhf
 */
public class LongTermMemory {
    private Long id;
    /**
     * 记忆类型
     * LongTermMemoryTypeEnum
     */
    private String memoryType;
    private String summary;
    private String memory;
    private String memoryHash;
    private Long parentId;
    private String userId;
    private String sessionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String embeddingId;
    public LongTermMemory() {}

    public LongTermMemory(String sessionId,String userId,String memoryType,String summary,String memory, Long parentId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.memoryType = memoryType;
        this.summary = summary;
        this.memory = memory;
        this.memoryHash = String.valueOf(memory.hashCode());
        this.parentId = parentId;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = memory;
    }

    public String getMemoryHash() {
        return memoryHash;
    }

    public void setMemoryHash(String memoryHash) {
        this.memoryHash = memoryHash;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEmbeddingId() {
        return embeddingId;
    }

    public void setEmbeddingId(String embeddingId) {
        this.embeddingId = embeddingId;
    }
}
