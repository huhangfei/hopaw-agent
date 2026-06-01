package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.tool.AgentTool;
import dev.langchain4j.data.message.Content;

import java.util.List;

/**
 * @author hhf
 */
public class AgentExecutorParams {
    private Long agentId;
    private String userId;
    private String sessionId;
    private Long aiModelId;
    private Integer maxMemoryRecords;
    private Integer maxToolInvocations;
    private Boolean enableThinking;
    private Boolean vectorToolSearch;
    private Integer vectorToolSearchMaxResults;
    private String extParams;
    /**
     * 工具执行权限
     * user_control 用户控制
     * smart_call 智能调用
     * auto 完全自动
     */
    private String toolCallPermission;
    private List<ToolSetInfo> toolSets;
    private List<String> skillNames;
    private List<Content> contents;
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

    public Long getAiModelId() {
        return aiModelId;
    }

    public void setAiModelId(Long aiModelId) {
        this.aiModelId = aiModelId;
    }

    public Integer getMaxMemoryRecords() {
        return maxMemoryRecords;
    }

    public void setMaxMemoryRecords(Integer maxMemoryRecords) {
        this.maxMemoryRecords = maxMemoryRecords;
    }

    public Integer getMaxToolInvocations() {
        return maxToolInvocations;
    }

    public void setMaxToolInvocations(Integer maxToolInvocations) {
        this.maxToolInvocations = maxToolInvocations;
    }

    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }

    public Boolean getVectorToolSearch() {
        return vectorToolSearch;
    }

    public void setVectorToolSearch(Boolean vectorToolSearch) {
        this.vectorToolSearch = vectorToolSearch;
    }

    public Integer getVectorToolSearchMaxResults() {
        return vectorToolSearchMaxResults;
    }

    public void setVectorToolSearchMaxResults(Integer vectorToolSearchMaxResults) {
        this.vectorToolSearchMaxResults = vectorToolSearchMaxResults;
    }

    public String getExtParams() {
        return extParams;
    }

    public void setExtParams(String extParams) {
        this.extParams = extParams;
    }

    public String getToolCallPermission() {
        return toolCallPermission;
    }

    public void setToolCallPermission(String toolCallPermission) {
        this.toolCallPermission = toolCallPermission;
    }

    public List<String> getSkillNames() {
        return skillNames;
    }

    public void setSkillNames(List<String> skillNames) {
        this.skillNames = skillNames;
    }

    public List<ToolSetInfo> getToolSets() {
        return toolSets;
    }

    public void setToolSets(List<ToolSetInfo> toolSets) {
        this.toolSets = toolSets;
    }

    public List<Content> getContents() {
        return contents;
    }

    public void setContents(List<Content> contents) {
        this.contents = contents;
    }
}
