package com.agent.hopaw.infra.chat;

import com.agent.hopaw.infra.model.dto.AiModelVO;
import com.agent.hopaw.infra.model.dto.ModelCapabilityTestResult;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

public interface ChatModelFactory {

    ChatModel createChatModel(AiModelVO aiModel);

    ChatModel createChatModel(AiModelVO aiModel, ChatModelListener langChain4JMonitor);

    ChatModel createChatModel(AiModelVO aiModel, Boolean enableThinking, String reasoningEffort, ChatModelListener langChain4JMonitor);

    StreamingChatModel createStreamingChatModel(AiModelVO aiModel);

    StreamingChatModel createStreamingChatModel(AiModelVO aiModel, ChatModelListener langChain4JMonitor);

    StreamingChatModel createStreamingChatModel(AiModelVO aiModel, Boolean enableThinking, String reasoningEffort, ChatModelListener langChain4JMonitor);

    String getProviderName();

    ModelCapabilityTestResult testModelCapability(ChatModel chatModel);
}