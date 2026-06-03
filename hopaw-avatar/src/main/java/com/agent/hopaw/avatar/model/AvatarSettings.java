package com.agent.hopaw.avatar.model;

public class AvatarSettings {
    private boolean disabled;
    private boolean soundEnabled;
    private String modelSetting;
    private String modelGroup;
    private String personaSetting;
    private String avatarAiPrompt;
    /**
     * 主动消息回忆窗口（分钟）。
     */
    private Integer memoryWindowMinutes;
    /**
     * 主动消息回忆最大记录数。
     */
    private Integer memoryMaxRecords;

    public AvatarSettings() {
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
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

    public Integer getMemoryWindowMinutes() {
        return memoryWindowMinutes;
    }

    public void setMemoryWindowMinutes(Integer memoryWindowMinutes) {
        this.memoryWindowMinutes = memoryWindowMinutes;
    }

    public Integer getMemoryMaxRecords() {
        return memoryMaxRecords;
    }

    public void setMemoryMaxRecords(Integer memoryMaxRecords) {
        this.memoryMaxRecords = memoryMaxRecords;
    }
}
