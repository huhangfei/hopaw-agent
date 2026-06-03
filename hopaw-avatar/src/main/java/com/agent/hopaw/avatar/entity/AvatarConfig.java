package com.agent.hopaw.avatar.entity;

public class AvatarConfig {
    private Long id;
    private String userId;
    private Boolean disabled;
    private String modelSetting;
    private String modelGroup;
    private String personaSetting;
    private Long avatarAiModelId;
    private String avatarAiPrompt;
    private Long totalTokens;
    private Long lastProcessedChatId;
    private Boolean soundEnabled;
    private String createTime;
    private String updateTime;

    public AvatarConfig() {
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

    public Long getAvatarAiModelId() {
        return avatarAiModelId;
    }

    public void setAvatarAiModelId(Long avatarAiModelId) {
        this.avatarAiModelId = avatarAiModelId;
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
