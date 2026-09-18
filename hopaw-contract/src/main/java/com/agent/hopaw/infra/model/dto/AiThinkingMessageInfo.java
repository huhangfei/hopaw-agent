package com.agent.hopaw.infra.model.dto;

public class AiThinkingMessageInfo extends AiMessageBaseInfo{
    public static final String TYPE_THINKING = "thinking";
    private String status;
    public AiThinkingMessageInfo() {
        super(TYPE_THINKING);
    }

    public static AiThinkingMessageInfo partial(String sessionId, String requestId, String content) {
        AiThinkingMessageInfo info = new AiThinkingMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus("partial");
        info.setContent(content);
        return info;
    }

    public static AiThinkingMessageInfo done(String sessionId, String requestId, String content) {
        AiThinkingMessageInfo info = new AiThinkingMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setStatus("done");
        info.setContent(content);
        return info;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}