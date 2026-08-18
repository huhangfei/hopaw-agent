package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 任务会话关系实体
 */
public class TaskSession {
    private Long id;
    private Long taskId;
    private String sessionId;
    private LocalDateTime createTime;
    /** 会话标题（非持久字段，由查询时 JOIN chat_sessions 填充） */
    private String title;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
