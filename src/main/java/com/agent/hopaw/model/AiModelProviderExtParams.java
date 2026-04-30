package com.agent.hopaw.model;

public class AiModelProviderExtParams {
    private String thinkingContentKey;
    private Boolean returnReasoning;
    private String reasoningEffort;
    private Double temperature;

    public AiModelProviderExtParams() {}

    public AiModelProviderExtParams(String thinkingContentKey, Boolean returnReasoning, String reasoningEffort, Double temperature) {
        this.thinkingContentKey = thinkingContentKey;
        this.returnReasoning = returnReasoning;
        this.reasoningEffort = reasoningEffort;
        this.temperature = temperature;
    }

    public String getThinkingContentKey() {
        return thinkingContentKey;
    }

    public void setThinkingContentKey(String thinkingContentKey) {
        this.thinkingContentKey = thinkingContentKey;
    }

    public Boolean getReturnReasoning() {
        return returnReasoning;
    }

    public void setReturnReasoning(Boolean returnReasoning) {
        this.returnReasoning = returnReasoning;
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
}