package com.agent.hopaw.service.ChatModel;

import com.agent.hopaw.model.AiModelVO;
import com.agent.hopaw.model.ModelCapabilityTestResult;
import com.agent.hopaw.service.LangChain4jMonitor;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ChatModelFactory {
    ChatModel createChatModel(AiModelVO aiModel, boolean enableThinking, LangChain4jMonitor langChain4JMonitor);

    StreamingChatModel createStreamingChatModel(AiModelVO aiModel,boolean enableThinking, LangChain4jMonitor langChain4JMonitor);

    String getProviderName();

    ModelCapabilityTestResult testModelCapability(ChatModel chatModel);
}