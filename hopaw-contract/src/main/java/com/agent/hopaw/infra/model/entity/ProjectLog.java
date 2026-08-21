package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 项目操作日志实体
 */
public class ProjectLog {
    private Long id;
    private Long projectId;
    /** 操作者用户ID */
    private String operatorId;
    /** 操作者昵称（冗余存储，便于历史展示） */
    private String operatorName;
    /** 动作类型：create / update / status_change / delete / attachment_upload / attachment_delete / task_bind / task_unbind / task_status / task_comment */
    private String action;
    /** 日志类型，见 ProjectLogTypeEnum：default=默认 / important=重点 */
    private String logType;
    /** 操作内容描述 */
    private String detail;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getLogType() {
        return logType;
    }

    public void setLogType(String logType) {
        this.logType = logType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
