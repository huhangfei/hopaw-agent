package com.agent.hopaw.service.ChatModel;

import com.agent.hopaw.constant.ModelProviderEnum;
import com.agent.hopaw.model.AiModelProvider;
import com.agent.hopaw.model.AiModelVO;
import com.agent.hopaw.service.LangChain4jMonitoringService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAiChatModelFactory extends  BaseChatModelFactory {

    private final LangChain4jMonitoringService monitoringService;

    public OpenAiChatModelFactory(LangChain4jMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking) {
        AiModelProvider aiModelProvider=aiModel.getAiModelProvider();
        Map<String, String> extraParams = new HashMap<>();
        var builder = OpenAiChatModel.builder()
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModelProvider))
                .customHeaders(extraParams);

        if(enableThinking){
            builder.sendThinking(true, super.getThinkingContentKey(aiModelProvider))
                    .returnThinking(super.getReturnReasoning(aiModelProvider))
                    .reasoningEffort(super.getReasoningEffort(aiModelProvider));
        }
        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }

        return builder.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel,boolean enableThinking) {
        AiModelProvider aiModelProvider=aiModel.getAiModelProvider();
        Map<String, String> extraParams = new HashMap<>();
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModelProvider))
                .customHeaders(extraParams);

        if(enableThinking){
            builder.sendThinking(true, super.getThinkingContentKey(aiModelProvider))
                    .returnThinking(super.getReturnReasoning(aiModelProvider))
                    .reasoningEffort(super.getReasoningEffort(aiModelProvider));
        }

        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }
        return builder.build();
    }

    @Override
    public String getProviderName() {
        return ModelProviderEnum.OPENAI.getSdkName();
    }
}