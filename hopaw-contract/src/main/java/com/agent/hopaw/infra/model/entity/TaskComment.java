package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 任务评论实体
 */
public class TaskComment {
    private Long id;
    private Long taskId;
    private String content;
    private String userId;
    /** 评论者类型：agent / user，见 TaskCommenterTypeEnum（兼容旧数据为 null 时按 user 处理） */
    private String commenterType;
    /** 评论者编号：智能体ID 或 用户ID */
    private String commenterId;
    /** 评论类型，见 TaskCommentTypeEnum：default=普通 / summary=总结 */
    private String commentType;
    private LocalDateTime createTime;
    /** 处理状态：pending=待处理 / processed=已处理，见 TaskCommentStatusEnum */
    private String status;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCommenterType() {
        return commenterType;
    }

    public void setCommenterType(String commenterType) {
        this.commenterType = commenterType;
    }

    public String getCommenterId() {
        return commenterId;
    }

    public void setCommenterId(String commenterId) {
        this.commenterId = commenterId;
    }

    public String getCommentType() {
        return commentType;
    }

    public void setCommentType(String commentType) {
        this.commentType = commentType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
