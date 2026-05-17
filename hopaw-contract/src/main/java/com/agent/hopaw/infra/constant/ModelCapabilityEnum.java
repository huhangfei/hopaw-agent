package com.agent.hopaw.infra.constant;

public enum ModelCapabilityEnum {
    TEXT("text", "文本"),
    IMAGE("image", "图片"),
    AUDIO("audio", "音频"),
    VIDEO("video", "视频"),
    DOCUMENT("document", "文档");

    private final String code;
    private final String name;

    ModelCapabilityEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static ModelCapabilityEnum fromCode(String code) {
        for (ModelCapabilityEnum capability : values()) {
            if (capability.code.equals(code)) {
                return capability;
            }
        }
        return null;
    }
}
