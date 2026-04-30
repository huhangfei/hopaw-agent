package com.agent.hopaw.service;

import com.agent.hopaw.model.AiModel;
import com.agent.hopaw.model.AiModelProvider;
import com.agent.hopaw.model.ChatModelFactory;
import com.agent.hopaw.service.LangChain4jMonitoringService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class OpenAiChatModelFactory extends  BaseChatModelFactory {

    private final LangChain4jMonitoringService monitoringService;

    public OpenAiChatModelFactory(LangChain4jMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }


    @Override
    public ChatModel createChatModel(AiModelProvider aiModelProvider, AiModel aiModel, boolean enableThinking) {
        Map<String, String> extraParams = new HashMap<>();
        var builder = OpenAiChatModel.builder()
                .sendThinking(enableThinking, super.getThinkingContentKey(aiModelProvider))
                .returnThinking(super.getReturnReasoning(aiModelProvider))
                .reasoningEffort(super.getReasoningEffort(aiModelProvider))
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModelProvider))
                .customHeaders(extraParams);

        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }

        return builder.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelProvider aiModelProvider,AiModel aiModel,boolean enableThinking) {
        Map<String, String> extraParams = new HashMap<>();
        var builder = OpenAiStreamingChatModel.builder()
                .sendThinking(enableThinking, super.getThinkingContentKey(aiModelProvider))
                .returnThinking(super.getReturnReasoning(aiModelProvider))
                .reasoningEffort(super.getReasoningEffort(aiModelProvider))
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModelProvider))
                .customHeaders(extraParams);

        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }
        return builder.build();
    }

    @Override
    public String getProviderName() {
        return "openai";
    }
}