package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 工作流任务实体
 */
public class WorkflowTask {
    private Long id;
    private String title;
    /** 任务内容（下发给智能体的指令） */
    private String content;
    /** 状态，见 TaskStatusEnum：pending / pending_execution / processing / pending_acceptance / failed / completed / rejected / closed */
    private String status;
    /** 关联项目ID（可选） */
    private Long projectId;
    /** 关联智能体ID */
    private Long agentId;
    /** 可选开始时间 */
    private LocalDateTime startTime;
    /** 可选执行时段（分钟） */
    private Integer executionPeriod;
    /** 驳回/失败原因 */
    private String rejectReason;
    private String userId;
    /** 创建人昵称（非持久字段，由 Controller 层填充） */
    private String creatorName;
    /** 智能体名称（非持久字段，由查询时 JOIN 填充） */
    private String agentName;
    /** 项目名称（非持久字段，由查询时 JOIN 填充） */
    private String projectName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public Integer getExecutionPeriod() {
        return executionPeriod;
    }

    public void setExecutionPeriod(Integer executionPeriod) {
        this.executionPeriod = executionPeriod;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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
