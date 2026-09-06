package com.agent.hopaw.infra.model.entity;

public class Agent {
    /** 窗口记忆 Token 上限默认值（20K，1024 进制） */
    public static final int DEFAULT_MAX_MEMORY_TOKENS = 20 * 1024;

    private Long id;
    private String name;
    private String description;
    private String tools;
    /**
     * 窗口记忆 Token 上限：超出后从最早消息开始淘汰
     */
    private Integer maxMemoryTokens;
    private Integer maxToolInvocations;
    private Long aiModelId;
    private Boolean enableThinking;
    private Boolean vectorToolSearch;
    private Integer vectorToolSearchMaxResults;
    private Boolean enableAllTools;
    private String extParams;
    private String userId;


    public Agent() {}

    public Agent(String name, String description, String tools) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryTokens = DEFAULT_MAX_MEMORY_TOKENS;
        this.maxToolInvocations = 10;
        this.vectorToolSearch = true;
        this.vectorToolSearchMaxResults = 5;
    }

    public Agent(String name, String description, String tools, Integer maxMemoryTokens) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryTokens = maxMemoryTokens != null ? maxMemoryTokens : DEFAULT_MAX_MEMORY_TOKENS;
        this.maxToolInvocations = 10;
        this.vectorToolSearch = true;
        this.vectorToolSearchMaxResults = 5;
    }

    public Agent(String name, String description, String tools, Integer maxMemoryTokens, Integer maxToolInvocations,Boolean enableThinking) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryTokens = maxMemoryTokens != null ? maxMemoryTokens : DEFAULT_MAX_MEMORY_TOKENS;
        this.maxToolInvocations = maxToolInvocations != null ? maxToolInvocations : 10;
        this.enableThinking = enableThinking != null ? enableThinking : false;
        this.vectorToolSearch = true;
        this.vectorToolSearchMaxResults = 5;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTools() {
        return tools;
    }

    public void setTools(String tools) {
        this.tools = tools;
    }

    public String[] getToolsArray() {
        if (tools == null || tools.isEmpty()) {
            return new String[0];
        }
        return tools.split(",");
    }

    public Integer getMaxMemoryTokens() {
        return maxMemoryTokens;
    }

    public void setMaxMemoryTokens(Integer maxMemoryTokens) {
        this.maxMemoryTokens = maxMemoryTokens;
    }

    public Integer getMaxToolInvocations() {
        return maxToolInvocations;
    }

    public void setMaxToolInvocations(Integer maxToolInvocations) {
        this.maxToolInvocations = maxToolInvocations;
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

    public Boolean getEnableAllTools() {
        return enableAllTools;
    }

    public void setEnableAllTools(Boolean enableAllTools) {
        this.enableAllTools = enableAllTools;
    }

    public String getExtParams() {
        return extParams;
    }

    public void setExtParams(String extParams) {
        this.extParams = extParams;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
