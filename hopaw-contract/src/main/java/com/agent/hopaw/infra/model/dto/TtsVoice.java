package com.agent.hopaw.infra.model.dto;

import java.util.List;

public class TtsVoice {
    private String voiceId;
    private String voiceName;
    private String language;
    private String gender;
    private String description;
    private List<String> emotions;

    public TtsVoice() {
    }

    public TtsVoice(String voiceId, String voiceName) {
        this.voiceId = voiceId;
        this.voiceName = voiceName;
    }

    public String getVoiceId() {
        return voiceId;
    }

    public void setVoiceId(String voiceId) {
        this.voiceId = voiceId;
    }

    public String getVoiceName() {
        return voiceName;
    }

    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getEmotions() {
        return emotions;
    }

    public void setEmotions(List<String> emotions) {
        this.emotions = emotions;
    }
}