package com.agent.hopaw.model;

public class AiModelVO extends AiModel{
    private AiModelProvider aiModelProvider;
        public AiModelProvider getAiModelProvider() {
        return aiModelProvider;
    }

    public void setAiModelProvider(AiModelProvider aiModelProvider) {
        this.aiModelProvider = aiModelProvider;
    }
}
