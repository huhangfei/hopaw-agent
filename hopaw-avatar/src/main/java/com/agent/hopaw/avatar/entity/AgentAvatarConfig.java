package com.agent.hopaw.avatar.entity;

/**
 * 虚拟人配置（按 用户+智能体 隔离）。
 * <p>原 avatar_config 表（按 user 隔离）已升级为 agent_avatar_config（按 user_id + agent_id 复合隔离）。</p>
 * <p>字段与原 AvatarConfig 一致，并新增 agentId。</p>
 */
public class AgentAvatarConfig {
    private Long id;
    private String userId;
    private Long agentId;
    private Boolean disabled;
    private String modelSetting;
    private String modelGroup;
    private String personaSetting;
    private String avatarAiPrompt;
    private Long totalTokens;
    private Long lastProcessedChatId;
    private Boolean soundEnabled;
    private String createTime;
    private String updateTime;

    public AgentAvatarConfig() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public String getModelSetting() {
        return modelSetting;
    }

    public void setModelSetting(String modelSetting) {
        this.modelSetting = modelSetting;
    }

    public String getModelGroup() {
        return modelGroup;
    }

    public void setModelGroup(String modelGroup) {
        this.modelGroup = modelGroup;
    }

    public String getPersonaSetting() {
        return personaSetting;
    }

    public void setPersonaSetting(String personaSetting) {
        this.personaSetting = personaSetting;
    }

    public String getAvatarAiPrompt() {
        return avatarAiPrompt;
    }

    public void setAvatarAiPrompt(String avatarAiPrompt) {
        this.avatarAiPrompt = avatarAiPrompt;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Long getLastProcessedChatId() {
        return lastProcessedChatId;
    }

    public void setLastProcessedChatId(Long lastProcessedChatId) {
        this.lastProcessedChatId = lastProcessedChatId;
    }

    public Boolean getSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(Boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
