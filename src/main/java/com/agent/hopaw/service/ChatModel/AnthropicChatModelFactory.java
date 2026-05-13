package com.agent.hopaw.service.ChatModel;

import com.agent.hopaw.constant.ModelProviderEnum;
import com.agent.hopaw.model.AiModelProvider;
import com.agent.hopaw.model.AiModelVO;
import com.agent.hopaw.service.LangChain4jMonitor;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnthropicChatModelFactory extends BaseChatModelFactory {

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking, LangChain4jMonitor monitoringService) {
        AiModelProvider aiModelProvider = aiModel.getAiModelProvider();
        var builder = AnthropicChatModel.builder()
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModel))
                .logRequests(super.getLogRequests(aiModel))
                .logResponses(super.getLogResponses(aiModel))
                .timeout(java.time.Duration.ofSeconds(super.getTimeoutSeconds(aiModel)))
                .returnThinking(super.getSendThinking(aiModel))
                .sendThinking(super.getSendThinking(aiModel));
        if (enableThinking) {
            builder.thinkingType("enabled");
        }
        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }
        return builder.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel, boolean enableThinking, LangChain4jMonitor monitoringService) {
        AiModelProvider aiModelProvider = aiModel.getAiModelProvider();
        var builder = AnthropicStreamingChatModel.builder()
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModel))
                .logRequests(super.getLogRequests(aiModel))
                .logResponses(super.getLogResponses(aiModel))
                .timeout(java.time.Duration.ofSeconds(super.getTimeoutSeconds(aiModel)))
                .returnThinking(super.getSendThinking(aiModel))
                .sendThinking(super.getSendThinking(aiModel));
        if (enableThinking) {
            builder.thinkingType("enabled");
        }
        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }
        return builder.build();
    }

    @Override
    public String getProviderName() {
        return ModelProviderEnum.ANTHROPIC.getSdkName();
    }
}
