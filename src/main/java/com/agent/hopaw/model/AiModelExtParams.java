package com.agent.hopaw.model;

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
}