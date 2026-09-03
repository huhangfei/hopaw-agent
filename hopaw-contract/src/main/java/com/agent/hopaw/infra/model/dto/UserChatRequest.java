package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;

import java.util.List;

/**
 * 用户聊天请求
 * @author hhf
 */
public class UserChatRequest {
    private String sessionId;
    private String requestId;
    private String userId;
    private Long agentId;
    private String message;
    private List<String> skillNames;
    private Long aiModelId;
    private Boolean enableThinking;
    /**
     * 工具执行权限
     * user_control 用户控制
     * smart_call 智能调用
     * auto 完全自动
     */
    private String toolCallPermission;
    private List<AttachmentFile> files;
    /**
     * 会话类型
     */
    private AgentExecutorBizTypeEnum sessionBizType;
    public String getRequestId() {
        return requestId;
    }
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getSkillNames() {
        return skillNames;
    }

    public void setSkillNames(List<String> skillNames) {
        this.skillNames = skillNames;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getAiModelId() {
        return aiModelId;
    }

    public void setAiModelId(Long aiModelId) {
        this.aiModelId = aiModelId;
    }

    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    public String getToolCallPermission() {
        return toolCallPermission;
    }

    public void setToolCallPermission(String toolCallPermission) {
        this.toolCallPermission = toolCallPermission;
    }

    public List<AttachmentFile> getFiles() {
        return files;
    }

    public void setFiles(List<AttachmentFile> files) {
        this.files = files;
    }

    public AgentExecutorBizTypeEnum getSessionBizType() {
        return sessionBizType;
    }

    public void setSessionBizType(AgentExecutorBizTypeEnum sessionBizType) {
        this.sessionBizType = sessionBizType;
    }
}
