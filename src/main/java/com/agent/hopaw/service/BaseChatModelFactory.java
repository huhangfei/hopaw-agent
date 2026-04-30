package com.agent.hopaw.service;

import com.agent.hopaw.model.AiModelProvider;
import com.agent.hopaw.model.ChatModelFactory;

public abstract class BaseChatModelFactory implements ChatModelFactory {

    public String getThinkingContentKey(AiModelProvider aiModelProvider){
        if (aiModelProvider.getExtParamsObj() == null || aiModelProvider.getExtParamsObj().getThinkingContentKey() == null) {
            return "reasoning_content";
        }
        return aiModelProvider.getExtParamsObj().getThinkingContentKey();
    }

    public Boolean getReturnReasoning(AiModelProvider aiModelProvider){
        if (aiModelProvider.getExtParamsObj() == null || aiModelProvider.getExtParamsObj().getReturnReasoning() == null) {
            return false;
        }
        return aiModelProvider.getExtParamsObj().getReturnReasoning();
    }

    public String getReasoningEffort(AiModelProvider aiModelProvider){
        if (aiModelProvider.getExtParamsObj() == null || aiModelProvider.getExtParamsObj().getReasoningEffort() == null) {
            return "high";
        }
        return aiModelProvider.getExtParamsObj().getReasoningEffort();
    }

    public Double getTemperature(AiModelProvider aiModelProvider){
        if (aiModelProvider.getExtParamsObj() == null || aiModelProvider.getExtParamsObj().getTemperature() == null) {
            return 0.7;
        }
        return aiModelProvider.getExtParamsObj().getTemperature();
    }

}
