package com.agent.hopaw.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ChatModelFactory {
    ChatModel createChatModel(AiModelProvider aiModelProvider,AiModel aiModel,boolean enableThinking);

    StreamingChatModel createStreamingChatModel(AiModelProvider aiModelProvider,AiModel aiModel,boolean enableThinking);

    String getProviderName();
}