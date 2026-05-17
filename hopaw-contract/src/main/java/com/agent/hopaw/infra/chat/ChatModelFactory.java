package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.model.dto.AiModelVO;
import com.agent.hopaw.infra.model.dto.ModelCapabilityTestResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

public interface ChatModelFactory {
    ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking, ChatModelListener langChain4JMonitor);

    StreamingChatModel createStreamingChatModel(AiModelVO aiModel,boolean enableThinking, ChatModelListener langChain4JMonitor);

    String getProviderName();

    ModelCapabilityTestResult testModelCapability(ChatModel chatModel);
}