package com.agent.hopaw.model;

/**
 * @author hhf
 */
public class ChatMemoryId {
    private  Long agentId;
    private  String userId;

    public ChatMemoryId(Long agentId, String userId) {
        this.agentId = agentId;
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
