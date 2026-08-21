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
    /** 项目空间目录（项目工作空间的绝对路径，创建项目时根据项目编号自动生成） */
    private String spaceDir;
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
