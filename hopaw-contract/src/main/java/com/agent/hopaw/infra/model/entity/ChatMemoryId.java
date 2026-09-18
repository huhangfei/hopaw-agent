package com.agent.hopaw.infra.model.entity;

/**
 * @author hhf
 */
public class ChatMemoryId {
    private  Long agentId;
    private  String userId;
    private String sessionId;
    private String requestId;
    public ChatMemoryId(String sessionId, String requestId,Long agentId, String userId) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.userId = userId;
        this.requestId = requestId;
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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
