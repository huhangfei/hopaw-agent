package com.agent.hopaw.model;

import java.time.LocalDateTime;

public class ScheduledTask {
    private Long id;
    private String taskName;
    private String taskType;
    private String cronExpression;
    private Integer enabled;
    private String description;
    private String extParams;
    /**
     * 用户ID
     */
    private String userId;
    /**
     * 智能体ID
     */
    private String agentId;
    /**
     * 是否是内置任务
     * 0: 否
     * 1: 内置
     */
    private Integer builtin;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public ScheduledTask() {}

    public ScheduledTask(String taskName, String taskType, String cronExpression, Integer enabled, String description) {
        this.taskName = taskName;
        this.taskType = taskType;
        this.cronExpression = cronExpression;
        this.enabled = enabled;
        this.description = description;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getCronExpression() { return cronExpression; }
    public void setCronExpression(String cronExpression) { this.cronExpression = cronExpression; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExtParams() { return extParams; }
    public void setExtParams(String extParams) { this.extParams = extParams; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public Integer getBuiltin() { return builtin; }
    public void setBuiltin(Integer builtin) { this.builtin = builtin; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
