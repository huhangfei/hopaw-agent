package com.agent.hopaw.service.ChatModel;

import com.agent.hopaw.model.AiModelVO;
import com.agent.hopaw.model.ModelCapabilityTestResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ChatModelFactory {
    ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking);

    StreamingChatModel createStreamingChatModel(AiModelVO aiModel,boolean enableThinking);

    String getProviderName();

    ModelCapabilityTestResult testModelCapability(ChatModel chatModel);
}