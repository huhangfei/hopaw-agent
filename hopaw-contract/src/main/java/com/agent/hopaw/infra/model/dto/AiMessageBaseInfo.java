package com.agent.hopaw.infra.model.dto;




public class AiMessageBaseInfo {
    private String type;
    private String sessionId;
    private String requestId;
    private String content;

    public AiMessageBaseInfo(String type) {
        this.type = type;
    }

    public AiMessageBaseInfo type(String type) {
        this.setType(type);
        return this;
    }
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    public AiMessageBaseInfo sessionId(String sessionId) {
        this.setSessionId(sessionId);
        return this;
    }
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AiMessageBaseInfo requestId(String requestId) {
        this.setRequestId(requestId);
        return this;
    }
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public AiMessageBaseInfo content(String content) {
        this.setContent(content);
        return this;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }


    public static AiMessageBaseInfo build(String type, String sessionId, String requestId) {
        AiMessageBaseInfo aiMessageBaseInfo = new AiMessageBaseInfo(type)
                .sessionId(sessionId)
                .requestId(requestId);
        return aiMessageBaseInfo;
    }
    public static AiMessageBaseInfo done(String sessionId, String requestId) {
        return AiMessageBaseInfo.build("done", sessionId, requestId);
    }
    public static AiMessageBaseInfo taskDone(String sessionId, String requestId) {
        return AiMessageBaseInfo.build("task-done", sessionId, requestId);
    }
    public static AiMessageBaseInfo error(String sessionId, String requestId,String content) {
        return AiMessageBaseInfo.build("error", sessionId, requestId).content(content);
    }
    public static AiMessageBaseInfo warn(String sessionId, String requestId,String content) {
        return AiMessageBaseInfo.build("warn", sessionId, requestId).content(content);
    }
    public static AiMessageBaseInfo chunk(String sessionId, String requestId,String content) {
        return AiMessageBaseInfo.build("chunk", sessionId, requestId).content(content);
    }
    public static AiMessageBaseInfo sessionTitle(String sessionId, String requestId, String content) {
        return AiMessageBaseInfo.build("session-title", sessionId, requestId).content(content);
    }
}
