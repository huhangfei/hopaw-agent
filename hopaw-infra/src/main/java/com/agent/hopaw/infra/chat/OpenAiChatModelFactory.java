package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.constant.ModelProviderEnum;
import com.agent.hopaw.infra.model.dto.AiModelVO;
import com.agent.hopaw.infra.model.entity.AiModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class OpenAiChatModelFactory extends  BaseChatModelFactory {

    @Override
    public ChatModel createChatModel(AiModelVO aiModel) {
        return createChatModel(aiModel, null, null, null);
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, ChatModelListener langChain4JMonitor) {
        return createChatModel(aiModel, null, null,langChain4JMonitor);
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, Boolean enableThinking, String reasoningEffort, ChatModelListener monitoringService) {
        AiModelProvider aiModelProvider=aiModel.getAiModelProvider();
        Map<String, Object> extraParams = new HashMap<>(0);
        if(enableThinking==null){
            enableThinking=super.getEnableThinking(aiModel);
        }
        if(reasoningEffort==null){
            reasoningEffort=super.getReasoningEffort(aiModel);
        }
        // 模型级硬约束：supportThinking=false 时强制关闭思考
        enableThinking = super.constrainEnableThinking(aiModel, enableThinking);
        Boolean finalEnableThinking = enableThinking;
        extraParams.put("thinking",new HashMap(1){{
            put("type", finalEnableThinking ? "enabled" : "disabled");
        }});
        var builder = OpenAiChatModel.builder()
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModel))
                .customParameters(extraParams)
                .sendThinking(super.getSendThinking(aiModel), super.getThinkingContentKey(aiModel))
                .returnThinking(super.getReturnThinking(aiModel))
                .logRequests(super.getLogRequests(aiModel))
                .logResponses(super.getLogResponses(aiModel))
                .timeout(java.time.Duration.ofSeconds(super.getTimeoutSeconds(aiModel)))
                .strictTools(super.getStrictTools(aiModel))
                .parallelToolCalls(super.getParallelToolCalls(aiModel));
        if (getUseMaxCompletionTokens(aiModel)) {
            builder.maxCompletionTokens(super.getOutputMaxTokens(aiModel));
        } else {
            builder.maxTokens(super.getOutputMaxTokens(aiModel));
        }
        if(enableThinking){
            builder.reasoningEffort(reasoningEffort);
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
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel,Boolean enableThinking, String reasoningEffort, ChatModelListener monitoringService) {
        AiModelProvider aiModelProvider=aiModel.getAiModelProvider();
        Map<String, Object> extraParams = new HashMap<>(0);
        if(enableThinking==null){
            enableThinking=super.getEnableThinking(aiModel);
        }
        if(reasoningEffort==null){
            reasoningEffort=super.getReasoningEffort(aiModel);
        }
        // 模型级硬约束：supportThinking=false 时强制关闭思考
        enableThinking = super.constrainEnableThinking(aiModel, enableThinking);
        Boolean finalEnableThinking = enableThinking;
        extraParams.put("thinking",new HashMap(1){{
            put("type", finalEnableThinking ? "enabled" : "disabled");
        }});
        var builder = OpenAiStreamingChatModel.builder()
                .accumulateToolCallId(super.getAccumulateToolCallId(aiModel))
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModel))
                .customParameters(extraParams)
                .sendThinking(super.getSendThinking(aiModel), super.getThinkingContentKey(aiModel))
                .returnThinking(super.getReturnThinking(aiModel))
                .logRequests(super.getLogRequests(aiModel))
                .logResponses(super.getLogResponses(aiModel))
                .timeout(java.time.Duration.ofSeconds(super.getTimeoutSeconds(aiModel)))
                .strictTools(super.getStrictTools(aiModel))
                .parallelToolCalls(super.getParallelToolCalls(aiModel));
        if (getUseMaxCompletionTokens(aiModel)) {
            builder.maxCompletionTokens(super.getOutputMaxTokens(aiModel));
        } else {
            builder.maxTokens(super.getOutputMaxTokens(aiModel));
        }
        if(enableThinking){
            builder.reasoningEffort(reasoningEffort);
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