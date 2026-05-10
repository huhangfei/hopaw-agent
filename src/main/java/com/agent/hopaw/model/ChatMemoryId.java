package com.agent.hopaw.model;

/**
 * @author hhf
 */
public class ChatMemoryId {
    public ChatMemoryId(Long agentId, String userId, String requestId) {
        this.agentId = agentId;
        this.userId = userId;
        this.requestId = requestId;
    }
    private  Long agentId;
    private  String userId;


    private String requestId;

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
