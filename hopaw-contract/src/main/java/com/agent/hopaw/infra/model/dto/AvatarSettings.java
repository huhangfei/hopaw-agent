package com.agent.hopaw.infra.model.dto;

public class AvatarSettings {
    private boolean disabled;
    private boolean soundEnabled;
    private String modelSetting;
    private String modelGroup;
    private String personaSetting;
    /** TTS 厂商编号 */
    private String ttsVendorCode;
    /** TTS 音色编号 */
    private String ttsVoiceId;
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

    public String getTtsVendorCode() {
        return ttsVendorCode;
    }

    public void setTtsVendorCode(String ttsVendorCode) {
        this.ttsVendorCode = ttsVendorCode;
    }

    public String getTtsVoiceId() {
        return ttsVoiceId;
    }

    public void setTtsVoiceId(String ttsVoiceId) {
        this.ttsVoiceId = ttsVoiceId;
    }

    public boolean isTtsEnabled() {
        return ttsEnabled;
    }

    public void setTtsEnabled(boolean ttsEnabled) {
        this.ttsEnabled = ttsEnabled;
    }
}
