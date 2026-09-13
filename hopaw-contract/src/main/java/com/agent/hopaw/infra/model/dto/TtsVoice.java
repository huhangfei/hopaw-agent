package com.agent.hopaw.infra.model.dto;

import java.util.List;

public class TtsVoice {
    /** 主键：存储到 tts_voice 表时的记录 id，内存内构建（厂商默认音色）时为 null */
    private Long id;
    /** 所属 TTS 配置主键（tts_config.id） */
    private Long configId;
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
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