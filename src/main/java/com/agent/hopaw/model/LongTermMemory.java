package com.agent.hopaw.model;

import java.time.LocalDateTime;

public class LongTermMemory {
    private Long id;
    private String agentId;
    private String memory;
    private String memoryHash;
    private Long parentId;
    private String userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public LongTermMemory() {}

    public LongTermMemory(String agentId, String memory, Long parentId) {
        this.agentId = agentId;
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

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
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
}
