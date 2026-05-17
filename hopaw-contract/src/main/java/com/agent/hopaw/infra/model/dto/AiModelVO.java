package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.model.entity.AiModelProvider;

public class AiModelVO extends AiModel{
    private AiModelProvider aiModelProvider;
        public AiModelProvider getAiModelProvider() {
        return aiModelProvider;
    }

    public void setAiModelProvider(AiModelProvider aiModelProvider) {
        this.aiModelProvider = aiModelProvider;
    }
}
