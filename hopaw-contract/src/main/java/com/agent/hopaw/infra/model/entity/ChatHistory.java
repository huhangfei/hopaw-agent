package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

public class ChatHistory {
    private Long id;
    private Long agentId;
    private String sessionId;
    private String role;
    private String messageType;
    private String content;
    private String toolCallId;
    private String toolName;
    private String toolArguments;
    private String toolCallStatus;
    private Long toolExecutionTime;
    private String userId;
    private LocalDateTime createTime;

    public ChatHistory() {}

    public ChatHistory(Long agentId, String role, String messageType) {
        this.agentId = agentId;
        this.role = role;
        this.messageType = messageType;
        this.createTime = LocalDateTime.now();
    }
    public ChatHistory(Long agentId, String role, String messageType, String content) {
        this.agentId = agentId;
        this.role = role;
        this.messageType = messageType;
        this.content = content;
        this.createTime = LocalDateTime.now();
    }

    public ChatHistory(Long agentId, String role, String messageType, String toolCallId,
                       String toolName, String toolArguments, String content) {
        this.agentId = agentId;
        this.role = role;
        this.messageType = messageType;
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.toolArguments = toolArguments;
        this.content = content;
        this.createTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolArguments() {
        return toolArguments;
    }

    public void setToolArguments(String toolArguments) {
        this.toolArguments = toolArguments;
    }

    public String getToolCallStatus() {
        return toolCallStatus;
    }

    public void setToolCallStatus(String toolCallStatus) {
        this.toolCallStatus = toolCallStatus;
    }

    public Long getToolExecutionTime() {
        return toolExecutionTime;
    }

    public void setToolExecutionTime(Long toolExecutionTime) {
        this.toolExecutionTime = toolExecutionTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
