package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.chat.ChatModelFactory;
import com.agent.hopaw.infra.model.dto.AiModelVO;
import com.agent.hopaw.infra.model.dto.ModelCapabilityTestResult;
import com.agent.hopaw.infra.model.entity.AiModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import java.util.List;
import java.util.Map;

public interface IAiModelService {
    AiModel findById(Long id);
    List<AiModel> findByProviderId(Long providerId);
    int insert(AiModel aiModel);
    int update(AiModel aiModel);
    int deleteById(Long id);
    ModelCapabilityTestResult testModel(Long id);
    AiModelVO findAiModelVOById(Long id);
    ChatModel createChatModel(Long aiModelId, boolean enableThinking, ChatModelListener chatModelListener);
    StreamingChatModel createStreamingChatModel(Long aiModelId, boolean enableThinking, ChatModelListener chatModelListener);
    Map<String, ChatModelFactory> getAllFactories();
    String getDefaultAiModelExtParamsJson();
}
