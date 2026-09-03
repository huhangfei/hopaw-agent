package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.model.entity.McpServerConfig;
import dev.langchain4j.data.message.Content;

import java.util.List;
import java.util.Map;

/**
 * @author hhf
 */
public class AgentExecutorParams {
    private Long agentId;
    private String userId;
    private String sessionId;
    private String requestId;
    private Long aiModelId;
    private Integer maxMemoryRecords;
    private Integer maxToolInvocations;
    private Boolean enableThinking;
    private Boolean vectorToolSearch;
    private Integer vectorToolSearchMaxResults;
    private Map<String,Object> extParams;
    /**
     * 工具执行权限
     * user_control 用户控制
     * smart_call 智能调用
     * auto 完全自动
     */
    private String toolCallPermission;
    private List<ToolSetInfo> toolSets;
    private List<String> skillNames;
    private List<McpServerConfig> mcpServerConfigs;
    /**
     * 业务类型：task 表示任务会话，null 表示普通对话会话
     * 由 saveChatSession 持久化到 chat_sessions.biz_type
     */
    private AgentExecutorBizTypeEnum bizType;
    /**
     * 会话标题：传入时直接作为会话标题，不再从用户输入中分析。
     * 任务/项目场景分别传任务名称、项目名称；为空时回退到用户意图分析
     */
    private String sessionTitle;

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
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

    public Map<String,Object> getExtParams() {
        return extParams;
    }

    public void setExtParams(Map<String,Object> extParams) {
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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    public List<McpServerConfig> getMcpServerConfigs() {
        return mcpServerConfigs;
    }

    public void setMcpServerConfigs(List<McpServerConfig> mcpServerConfigs) {
        this.mcpServerConfigs = mcpServerConfigs;
    }

    public AgentExecutorBizTypeEnum getBizType() {
        return bizType;
    }

    public void setBizType(AgentExecutorBizTypeEnum bizType) {
        this.bizType = bizType;
    }
}
