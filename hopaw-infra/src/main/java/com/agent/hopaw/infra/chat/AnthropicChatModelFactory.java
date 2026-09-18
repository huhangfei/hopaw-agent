package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import com.agent.hopaw.infra.model.dto.AiModelVO;
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
    public ChatModel createChatModel(AiModelVO aiModel) {
        return createChatModel(aiModel, null, null, null);
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, ChatModelListener langChain4JMonitor) {
        return createChatModel(aiModel, null, null, langChain4JMonitor);
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, Boolean enableThinking, String reasoningEffort, ChatModelListener monitoringService) {
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
                .sendThinking(super.getSendThinking(aiModel))
                .strictTools(super.getStrictTools(aiModel))
                .maxTokens(super.getOutputMaxTokens(aiModel))
                .disableParallelToolUse(!super.getParallelToolCalls(aiModel));
        if(enableThinking==null){
            enableThinking=super.getEnableThinking(aiModel);
        }
        // 模型级硬约束：supportThinking=false 时强制关闭思考
        enableThinking = super.constrainEnableThinking(aiModel, enableThinking);
        if (enableThinking) {
            builder.thinkingType("enabled").thinkingBudgetTokens(super.getThinkingBudgetTokens(aiModel));
        }
        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }
        return builder.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel) {
        return createStreamingChatModel(aiModel, null, null, null);
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel, ChatModelListener langChain4JMonitor) {
        return createStreamingChatModel(aiModel, null, null, langChain4JMonitor);
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel, Boolean enableThinking, String reasoningEffort, ChatModelListener monitoringService) {
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
                .sendThinking(super.getSendThinking(aiModel))
                .strictTools(super.getStrictTools(aiModel))
                .maxTokens(super.getOutputMaxTokens(aiModel))
                .disableParallelToolUse(!super.getParallelToolCalls(aiModel))
                ;
        if(enableThinking==null){
            enableThinking=super.getEnableThinking(aiModel);
        }
        // 模型级硬约束：supportThinking=false 时强制关闭思考
        enableThinking = super.constrainEnableThinking(aiModel, enableThinking);
        if (enableThinking) {
            builder.thinkingType("enabled").thinkingBudgetTokens(super.getThinkingBudgetTokens(aiModel));
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
