package com.agent.hopaw.avatar.model;

public class AvatarSettings {
    private boolean disabled;
    private String modelSetting;
    private String modelGroup;
    private String personaSetting;

    public AvatarSettings() {
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
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
}
