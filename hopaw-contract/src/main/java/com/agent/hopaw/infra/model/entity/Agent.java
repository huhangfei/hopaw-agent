package com.agent.hopaw.infra.model.entity;

public class Agent {
    /** 窗口记忆 Token 上限默认值（20K，1024 进制） */
    public static final int DEFAULT_MAX_MEMORY_TOKENS = 20 * 1024;

    /** 工具调用最大轮次默认值：500 轮 */
    public static final int DEFAULT_MAX_TOOL_INVOCATIONS = 500;

    /** 工具调用无限制哨兵值：配置为任意负数（含 -1）即表示不限轮次 */
    public static final int UNLIMITED_MAX_TOOL_INVOCATIONS = -1;

    /**
     * 解析工具调用最大轮次：未配置（null）取默认值 {@link #DEFAULT_MAX_TOOL_INVOCATIONS}，
     * 已配置则原样返回（负数 = 无限制）。
     */
    public static int resolveMaxToolInvocations(Integer maxToolInvocations) {
        return maxToolInvocations != null ? maxToolInvocations : DEFAULT_MAX_TOOL_INVOCATIONS;
    }

    /**
     * 是否不限制工具调用轮次：负数（含 {@link #UNLIMITED_MAX_TOOL_INVOCATIONS}）为显式无限制；
     * 0 亦按无限制处理（与前端「上限为 0 显示 ∞」的历史口径一致）。
     */
    public static boolean isToolInvocationsUnlimited(Integer maxToolInvocations) {
        return resolveMaxToolInvocations(maxToolInvocations) <= 0;
    }

    /**
     * 换算为 langchain4j {@code AiServices.maxToolCallingRoundTrips(int)} 的入参。
     * <p>
     * SDK 以「剩余轮次递减到 0 即抛异常」实现限制：传 0 会在首次工具调用时直接终止，
     * 传负数虽也不会命中 0 判断，但依赖计数器溢出，语义隐晦。
     * 因此「无限制」统一换算为 {@link Integer#MAX_VALUE}（递减到 0 需 20 亿轮，实际不可达）。
     */
    public static int toRoundTripsLimit(Integer maxToolInvocations) {
        int configured = resolveMaxToolInvocations(maxToolInvocations);
        return configured <= 0 ? Integer.MAX_VALUE : configured;
    }

    private Long id;
    private String name;
    private String description;
    private String tools;
    /**
     * 窗口记忆 Token 上限：超出后从最早消息开始淘汰
     */
    private Integer maxMemoryTokens;
    /**
     * 工具调用最大轮次：>0 表示具体上限；0 或负数（推荐 {@link #UNLIMITED_MAX_TOOL_INVOCATIONS}）表示不限制。
     * 未配置（null）时取 {@link #DEFAULT_MAX_TOOL_INVOCATIONS}。
     */
    private Integer maxToolInvocations;
    private Long aiModelId;
    private Boolean enableThinking;
    /**
     * 模型创造力参数
     */
    private Double temperature;
    /**
     * 思考努力程度参数
     */
    private String reasoningEffort;
    private Boolean vectorToolSearch;
    private Integer vectorToolSearchMaxResults;
    private Boolean enableAllTools;
    private String extParams;
    private String userId;
    private String avatar;


    public Agent() {}

    public Agent(String name, String description, String tools) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryTokens = DEFAULT_MAX_MEMORY_TOKENS;
        this.maxToolInvocations = DEFAULT_MAX_TOOL_INVOCATIONS;
        this.vectorToolSearch = true;
        this.vectorToolSearchMaxResults = 5;
    }

    public Agent(String name, String description, String tools, Integer maxMemoryTokens) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryTokens = maxMemoryTokens != null ? maxMemoryTokens : DEFAULT_MAX_MEMORY_TOKENS;
        this.maxToolInvocations = DEFAULT_MAX_TOOL_INVOCATIONS;
        this.vectorToolSearch = true;
        this.vectorToolSearchMaxResults = 5;
    }

    public Agent(String name, String description, String tools, Integer maxMemoryTokens, Integer maxToolInvocations,Boolean enableThinking) {
        this.name = name;
        this.description = description;
        this.tools = tools;
        this.maxMemoryTokens = maxMemoryTokens != null ? maxMemoryTokens : DEFAULT_MAX_MEMORY_TOKENS;
        this.maxToolInvocations = maxToolInvocations != null ? maxToolInvocations : DEFAULT_MAX_TOOL_INVOCATIONS;
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

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
