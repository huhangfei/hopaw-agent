package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import com.agent.hopaw.infra.model.dto.AiModelVO;
import com.agent.hopaw.infra.monitor.LangChain4jMonitor;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnthropicChatModelFactory extends BaseChatModelFactory {

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking, ChatModelListener monitoringService) {
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
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel, boolean enableThinking, ChatModelListener monitoringService) {
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
