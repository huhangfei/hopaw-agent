package com.agent.hopaw.service.ChatModel;

import com.agent.hopaw.model.AiModelVO;
import com.agent.hopaw.model.ModelCapabilityTestResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

import java.util.Map;

public interface ChatModelFactory {
    ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking, Map<String, String> metadata);

    StreamingChatModel createStreamingChatModel(AiModelVO aiModel,boolean enableThinking, Map<String, String> metadata);

    String getProviderName();

    ModelCapabilityTestResult testModelCapability(ChatModel chatModel);
}