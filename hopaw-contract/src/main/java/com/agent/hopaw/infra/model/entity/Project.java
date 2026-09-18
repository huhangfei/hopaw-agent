package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;
import java.util.List;

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
    /** 最小检测频率（分钟），自动迭代时判断距上次执行是否满足间隔，小于1视为不限制 */
    private Integer minFrequency;
    /** 上次自动迭代执行时间（自动更新，供最小频率间隔判断） */
    private LocalDateTime lastIterateTime;
    /** 项目管理智能体会话编号（自动迭代执行器复用该会话保留上下文） */
    private String sessionId;
    /** 创建人昵称（非持久字段，由 Controller 层填充） */
    private String creatorName;
    /** 通知渠道编号列表（持久字段，JSON 数组字符串，如 "[1,2]"）：事件发生时向这些渠道发送通知 */
    private String notifyChannels;
    /** 通知事项编码列表（持久字段，JSON 数组字符串，如 "[\"task_failed\"]"），见 NotifyEventEnum */
    private String notifyEvents;
    /** 通知渠道编号列表（非持久字段：由 Service 层与 notifyChannels JSON 互转，接口出入参使用） */
    private List<Long> notifyChannelIds;
    /** 通知事项编码列表（非持久字段：由 Service 层与 notifyEvents JSON 互转，接口出入参使用） */
    private List<String> notifyEventCodes;
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

    public Integer getMinFrequency() {
        return minFrequency;
    }

    public void setMinFrequency(Integer minFrequency) {
        this.minFrequency = minFrequency;
    }

    public LocalDateTime getLastIterateTime() {
        return lastIterateTime;
    }

    public void setLastIterateTime(LocalDateTime lastIterateTime) {
        this.lastIterateTime = lastIterateTime;
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

    public String getNotifyChannels() {
        return notifyChannels;
    }

    public void setNotifyChannels(String notifyChannels) {
        this.notifyChannels = notifyChannels;
    }

    public String getNotifyEvents() {
        return notifyEvents;
    }

    public void setNotifyEvents(String notifyEvents) {
        this.notifyEvents = notifyEvents;
    }

    public List<Long> getNotifyChannelIds() {
        return notifyChannelIds;
    }

    public void setNotifyChannelIds(List<Long> notifyChannelIds) {
        this.notifyChannelIds = notifyChannelIds;
    }

    public List<String> getNotifyEventCodes() {
        return notifyEventCodes;
    }

    public void setNotifyEventCodes(List<String> notifyEventCodes) {
        this.notifyEventCodes = notifyEventCodes;
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
