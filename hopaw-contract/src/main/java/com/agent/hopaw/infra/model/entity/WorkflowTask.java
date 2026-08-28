package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;
import java.util.List;

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
    /** 可选执行时段（HH:mm-HH:mm，如 09:00-18:00）：限制调度拉起任务的每日时间窗口，为空不限制 */
    private String executionPeriod;
    /** 驳回/失败原因 */
    private String rejectReason;
    private String userId;
    /** 创建者类型：user=用户创建，agent=智能体创建 */
    private String creatorType;
    /** 创建者智能体编号（creatorType=agent 时有值） */
    private Long creatorAgentId;
    /** 创建人昵称（非持久字段，由 Controller 层填充） */
    private String creatorName;
    /** 创建者智能体名称（非持久字段，由查询时 JOIN 填充） */
    private String creatorAgentName;
    /** 智能体名称（非持久字段，由查询时 JOIN 填充） */
    private String agentName;
    /** 项目名称（非持久字段，由查询时 JOIN 填充） */
    private String projectName;
    /** 前置条件列表（非持久字段：创建/更新时由前端提交，详情查询时填充） */
    private List<WorkflowTaskPrecondition> preconditions;
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

    public String getExecutionPeriod() {
        return executionPeriod;
    }

    public void setExecutionPeriod(String executionPeriod) {
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

    public String getCreatorType() {
        return creatorType;
    }

    public void setCreatorType(String creatorType) {
        this.creatorType = creatorType;
    }

    public Long getCreatorAgentId() {
        return creatorAgentId;
    }

    public void setCreatorAgentId(Long creatorAgentId) {
        this.creatorAgentId = creatorAgentId;
    }

    public String getCreatorAgentName() {
        return creatorAgentName;
    }

    public void setCreatorAgentName(String creatorAgentName) {
        this.creatorAgentName = creatorAgentName;
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

    public List<WorkflowTaskPrecondition> getPreconditions() {
        return preconditions;
    }

    public void setPreconditions(List<WorkflowTaskPrecondition> preconditions) {
        this.preconditions = preconditions;
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
