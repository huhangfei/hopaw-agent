package com.agent.hopaw.infra.model.dto;

public class AiModelExtParams {
    private String thinkingContentKey;
    private Boolean sendThinking;
    private Boolean returnThinking;
    private String reasoningEffort;
    private Double temperature;
    /*
     * 超时时间（秒）
     */
    private Long timeoutSeconds;
    private Boolean logRequests;
    private Boolean logResponses;
    private Boolean accumulateToolCallId;
    /**
     * 是否启用严格工具 Schema（默认 true）
     */
    private Boolean strictTools;
    /**
     * 输出上限是否使用 max_completion_tokens 参数（默认 false 使用 max_tokens）。
     * OpenAI o 系列推理模型/gpt-5 要求 max_completion_tokens；DeepSeek 等端点仅支持 max_tokens
     */
    private Boolean useMaxCompletionTokens;
    /**
     * 是否允许并行工具调用（默认 true）。
     * OpenAI 系列为 parallelToolCalls；Anthropic 为反向语义 disableParallelToolUse
     */
    private Boolean parallelToolCalls;
    /**
     * 是否启用思考模式（默认 true）
     */
    private Boolean enableThinking;
    public AiModelExtParams() {}


    public AiModelExtParams(String thinkingContentKey, Boolean sendThinking, Boolean returnThinking, String reasoningEffort, Double temperature, Long timeoutSeconds, Boolean logRequests, Boolean logResponses, Boolean accumulateToolCallId) {
        this.thinkingContentKey = thinkingContentKey;
        this.sendThinking = sendThinking;
        this.returnThinking = returnThinking;
        this.reasoningEffort = reasoningEffort;
        this.temperature = temperature;
        this.timeoutSeconds = timeoutSeconds;
        this.logRequests = logRequests;
        this.logResponses = logResponses;
        this.accumulateToolCallId = accumulateToolCallId;
    }


    public String getThinkingContentKey() {
        return thinkingContentKey;
    }

    public void setThinkingContentKey(String thinkingContentKey) {
        this.thinkingContentKey = thinkingContentKey;
    }

    public Boolean getSendThinking() {
        return sendThinking;
    }

    public void setSendThinking(Boolean sendThinking) {
        this.sendThinking = sendThinking;
    }
    public Boolean getReturnThinking() {
        return returnThinking;
    }

    public void setReturnThinking(Boolean returnThinking) {
        this.returnThinking = returnThinking;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Long getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getLogRequests() {
        return logRequests;
    }

    public void setLogRequests(Boolean logRequests) {
        this.logRequests = logRequests;
    }

    public Boolean getLogResponses() {
        return logResponses;
    }

    public void setLogResponses(Boolean logResponses) {
        this.logResponses = logResponses;
    }

    public Boolean getAccumulateToolCallId() {
        return accumulateToolCallId;
    }

    public void setAccumulateToolCallId(Boolean accumulateToolCallId) {
        this.accumulateToolCallId = accumulateToolCallId;
    }

    public Boolean getStrictTools() {
        return strictTools;
    }

    public void setStrictTools(Boolean strictTools) {
        this.strictTools = strictTools;
    }

    public Boolean getUseMaxCompletionTokens() {
        return useMaxCompletionTokens;
    }

    public void setUseMaxCompletionTokens(Boolean useMaxCompletionTokens) {
        this.useMaxCompletionTokens = useMaxCompletionTokens;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public void setParallelToolCalls(Boolean parallelToolCalls) {
        this.parallelToolCalls = parallelToolCalls;
    }

    public Boolean getEnableThinking() {
        return enableThinking;
    }

    public void setEnableThinking(Boolean enableThinking) {
        this.enableThinking = enableThinking;
    }
}