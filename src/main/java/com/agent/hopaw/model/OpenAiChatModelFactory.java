package com.agent.hopaw.model;

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
public class OpenAiChatModelFactory implements ChatModelFactory {

    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final Double temperature;
    private final boolean enableThinking;
    private final boolean returnReasoning;
    private final LangChain4jMonitoringService monitoringService;

    public OpenAiChatModelFactory(
            @Value("${openai.api.key:}") String apiKey,
            @Value("${openai.base.url:}") String baseUrl,
            @Value("${openai.model.name:gpt-3.5-turbo}") String modelName,
            @Value("${openai.temperature:0.7}") Double temperature,
            @Value("${openai.enable-thinking:true}") boolean enableThinking,
            @Value("${openai.return-reasoning:true}") boolean returnReasoning,
            LangChain4jMonitoringService monitoringService) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.temperature = temperature;
        this.enableThinking = enableThinking;
        this.returnReasoning = returnReasoning;
        this.monitoringService = monitoringService;
    }

    @Override
    public ChatModel createChatModel() {
        Map<String, String> extraParams = new HashMap<>();
        var builder = OpenAiChatModel.builder()
                .sendThinking(enableThinking,"reasoning_content")
                .returnThinking(returnReasoning)
                .reasoningEffort("high/max")
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .customHeaders(extraParams);
        return builder.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel() {
        var builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature);

        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }
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