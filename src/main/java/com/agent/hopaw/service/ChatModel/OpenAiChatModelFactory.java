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
@Service
public class OpenAiChatModelFactory extends  BaseChatModelFactory {

    private final LangChain4jMonitoringService monitoringService;

    public OpenAiChatModelFactory(LangChain4jMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @Override
    public ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking) {
        AiModelProvider aiModelProvider=aiModel.getAiModelProvider();
        Map<String, Object> extraParams = new HashMap<>();
        extraParams.put("thinking",new HashMap(){{
            put("type", enableThinking ? "enabled" : "disabled");
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
                .timeout(java.time.Duration.ofSeconds(super.getTimeoutSeconds(aiModel)));
        if(enableThinking){
            builder.reasoningEffort(super.getReasoningEffort(aiModel));
        }
        if (monitoringService != null) {
            builder.listeners(List.of(monitoringService));
        }
        return builder.build();
    }

    @Override
    public StreamingChatModel createStreamingChatModel(AiModelVO aiModel,boolean enableThinking) {
        AiModelProvider aiModelProvider=aiModel.getAiModelProvider();
        Map<String, Object> extraParams = new HashMap<>();
        extraParams.put("thinking",new HashMap(){{
            put("type", enableThinking ? "enabled" : "disabled");
        }});
        var builder = OpenAiStreamingChatModel.builder()
                .accumulateToolCallId(true)
                .apiKey(aiModelProvider.getApiKey())
                .modelName(aiModel.getModelName())
                .baseUrl(aiModelProvider.getUrl())
                .temperature(super.getTemperature(aiModel))
                .customParameters(extraParams)
                .sendThinking(super.getSendThinking(aiModel), super.getThinkingContentKey(aiModel))
                .returnThinking(super.getReturnThinking(aiModel))
                .logRequests(super.getLogRequests(aiModel))
                .logResponses(super.getLogResponses(aiModel))
                .timeout(java.time.Duration.ofSeconds(super.getTimeoutSeconds(aiModel)));
        if(enableThinking){
            builder.reasoningEffort(super.getReasoningEffort(aiModel));
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