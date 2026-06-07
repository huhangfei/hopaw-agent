package com.agent.hopaw.infra.model.dto;

public class AvatarSettings {
    private boolean disabled;
    private boolean soundEnabled;
    private String modelSetting;
    private String modelGroup;
    private String personaSetting;
    /** TTS 配置主键（关联 tts_config 表） */
    private Long ttsConfigId;
    /** TTS 音色编号 */
    private String ttsVoiceId;
    /** TTS 音色支持的情感列表（逗号分隔，选择音色后自动填入） */
    private String ttsEmotions;
    /** TTS 是否启用 */
    private boolean ttsEnabled;

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

    public boolean isTtsEnabled() {
        return ttsEnabled;
    }

    public void setTtsEnabled(boolean ttsEnabled) {
        this.ttsEnabled = ttsEnabled;
    }
}
