package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 项目实体
 */
public class Project {
    private Long id;
    private String name;
    private String description;
    /** 状态，见 ProjectStatusEnum：planning / in_progress / paused / completed / archived */
    private String status;
    private String userId;
    /** 项目空间目录（支持相对路径与绝对路径：相对路径以服务运行目录为起点，创建项目自动创建时存相对路径） */
    private String spaceDir;
    /** 项目空间目录绝对路径（非持久字段，仅展示用：相对路径存储时按运行目录解析出的绝对路径） */
    private String spaceDirAbs;
    /** 项目管理智能体编号（配置后可由调度任务自动迭代项目） */
    private Long agentId;
    /** 是否启用自动迭代：true=启用（启用后由定时任务周期性驱动项目管理智能体） */
    private Boolean autoIterate;
    /** 自动迭代要求提示词：启用自动迭代时带入项目管理智能体的额外迭代要求 */
    private String iteratePrompt;
    /** 项目管理智能体会话编号（自动迭代执行器复用该会话保留上下文） */
    private String sessionId;
    /** 创建人昵称（非持久字段，由 Controller 层填充） */
    private String creatorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSpaceDir() {
        return spaceDir;
    }

    public void setSpaceDir(String spaceDir) {
        this.spaceDir = spaceDir;
    }

    public String getSpaceDirAbs() {
        return spaceDirAbs;
    }

    public void setSpaceDirAbs(String spaceDirAbs) {
        this.spaceDirAbs = spaceDirAbs;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Boolean getAutoIterate() {
        return autoIterate;
    }

    public void setAutoIterate(Boolean autoIterate) {
        this.autoIterate = autoIterate;
    }

    public String getIteratePrompt() {
        return iteratePrompt;
    }

    public void setIteratePrompt(String iteratePrompt) {
        this.iteratePrompt = iteratePrompt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
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
