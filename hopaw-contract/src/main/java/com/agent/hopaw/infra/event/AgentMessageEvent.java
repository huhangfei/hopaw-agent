package com.agent.hopaw.infra.event;

import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;

public class AgentMessageEvent {

    private final String userId;
    private final Long agentId;
    private final AiMessageBaseInfo message;

    public AgentMessageEvent(String userId, Long agentId, AiMessageBaseInfo message) {
        this.userId = userId;
        this.agentId = agentId;
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public AiMessageBaseInfo getMessage() {
        return message;
    }
}
