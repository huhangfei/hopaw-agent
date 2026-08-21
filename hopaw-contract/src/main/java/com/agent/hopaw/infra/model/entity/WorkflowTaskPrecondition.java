package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 工作流任务前置条件实体：任务可配置多个前置任务，每个前置任务可配置多个要求状态
 * （逗号分隔存储，见 TaskStatusEnum；前置任务当前状态命中任意要求状态即视为满足）
 */
public class WorkflowTaskPrecondition {
    private Long id;
    /** 当前任务ID */
    private Long taskId;
    /** 前置任务ID */
    private Long preTaskId;
    /** 要求状态（多选，逗号分隔，如 "pending_acceptance,completed,failed"） */
    private String requiredStatus;
    private LocalDateTime createTime;

    /** 前置任务标题（非持久字段，查询时 JOIN 填充） */
    private String preTaskTitle;
    /** 前置任务当前状态（非持久字段，查询时 JOIN 填充） */
    private String preTaskStatus;

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

    public Long getPreTaskId() {
        return preTaskId;
    }

    public void setPreTaskId(Long preTaskId) {
        this.preTaskId = preTaskId;
    }

    public String getRequiredStatus() {
        return requiredStatus;
    }

    public void setRequiredStatus(String requiredStatus) {
        this.requiredStatus = requiredStatus;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getPreTaskTitle() {
        return preTaskTitle;
    }

    public void setPreTaskTitle(String preTaskTitle) {
        this.preTaskTitle = preTaskTitle;
    }

    public String getPreTaskStatus() {
        return preTaskStatus;
    }

    public void setPreTaskStatus(String preTaskStatus) {
        this.preTaskStatus = preTaskStatus;
    }
}
