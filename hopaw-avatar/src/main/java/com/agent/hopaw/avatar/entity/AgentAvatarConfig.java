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
    private Long totalTokens;
    private Long lastProcessedChatId;
    private Boolean soundEnabled;
    /**
     * 最后一次主动问候时间（yyyy-MM-dd HH:mm:ss）。
     * 由 AvatarProactiveTool 在调用时更新；定时任务据此判断是否需要发送 wave 提示。
     */
    private String lastProactiveGreetingTime;
    /** TTS 配置主键（关联 tts_config 表） */
    private Long ttsConfigId;
    /** TTS 音色编号 */
    private String ttsVoiceId;
    /** TTS 音色支持的情感列表（逗号分隔，选择音色后自动填入） */
    private String ttsEmotions;
    /** TTS 是否启用 */
    private Boolean ttsEnabled;
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

    public String getLastProactiveGreetingTime() {
        return lastProactiveGreetingTime;
    }

    public void setLastProactiveGreetingTime(String lastProactiveGreetingTime) {
        this.lastProactiveGreetingTime = lastProactiveGreetingTime;
    }

    public Long getTtsConfigId() {
        return ttsConfigId;
    }

    public void setTtsConfigId(Long ttsConfigId) {
        this.ttsConfigId = ttsConfigId;
    }

    public String getTtsVoiceId() {
        return ttsVoiceId;
    }

    public void setTtsVoiceId(String ttsVoiceId) {
        this.ttsVoiceId = ttsVoiceId;
    }

    public String getTtsEmotions() {
        return ttsEmotions;
    }

    public void setTtsEmotions(String ttsEmotions) {
        this.ttsEmotions = ttsEmotions;
    }

    public Boolean getTtsEnabled() {
        return ttsEnabled;
    }

    public void setTtsEnabled(Boolean ttsEnabled) {
        this.ttsEnabled = ttsEnabled;
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
