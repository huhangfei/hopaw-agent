package com.agent.hopaw.infra.model.dto;

public class ThinkingInfo {
    public static final String TYPE_THINKING = "thinking";

    private String type;
    private String status;
    private String content;
    private String responseId;

    public ThinkingInfo() {
        this.type = TYPE_THINKING;
    }

    public static ThinkingInfo partial(String content, String responseId) {
        ThinkingInfo info = new ThinkingInfo();
        info.status = "partial";
        info.content = content;
        info.responseId = responseId;
        return info;
    }

    public static ThinkingInfo done(String content, String responseId) {
        ThinkingInfo info = new ThinkingInfo();
        info.status = "done";
        info.content = content;
        info.responseId = responseId;
        return info;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getResponseId() {
        return responseId;
    }

    public void setResponseId(String responseId) {
        this.responseId = responseId;
    }
}