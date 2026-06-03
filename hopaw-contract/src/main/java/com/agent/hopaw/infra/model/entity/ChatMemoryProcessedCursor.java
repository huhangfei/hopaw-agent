package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 记忆整理进度游标。
 * <p>
 * 记录某个 (session_id, user_id) 下，长时记忆整理任务上一次处理到的
 * chat_memory 与 chat_memory_obsolete 的最大 id。
 * 下次执行时仅查询 id 大于该游标值的增量数据，避免重复整理。
 * </p>
 */
public class ChatMemoryProcessedCursor {

    private String sessionId;
    private String userId;
    /**
     * 已处理过的 chat_memory 中的最大 id。
     */
    private Long lastChatMemoryId;
    /**
     * 已处理过的 chat_memory_obsolete 中的最大 id。
     */
    private Long lastObsoleteMemoryId;
    private LocalDateTime updateTime;

    public ChatMemoryProcessedCursor() {
    }

    public ChatMemoryProcessedCursor(String sessionId, String userId,
                                     Long lastChatMemoryId, Long lastObsoleteMemoryId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.lastChatMemoryId = lastChatMemoryId;
        this.lastObsoleteMemoryId = lastObsoleteMemoryId;
        this.updateTime = LocalDateTime.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getLastChatMemoryId() {
        return lastChatMemoryId;
    }

    public void setLastChatMemoryId(Long lastChatMemoryId) {
        this.lastChatMemoryId = lastChatMemoryId;
    }

    public Long getLastObsoleteMemoryId() {
        return lastObsoleteMemoryId;
    }

    public void setLastObsoleteMemoryId(Long lastObsoleteMemoryId) {
        this.lastObsoleteMemoryId = lastObsoleteMemoryId;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
