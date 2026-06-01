package com.agent.hopaw.infra.event;

import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;

public class AgentMessageEvent {

    private final String userId;
    private final AiMessageBaseInfo message;

    public AgentMessageEvent(String userId, AiMessageBaseInfo message) {
        this.userId = userId;
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public AiMessageBaseInfo getMessage() {
        return message;
    }
}