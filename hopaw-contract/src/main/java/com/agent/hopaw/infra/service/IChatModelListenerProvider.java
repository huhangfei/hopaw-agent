package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import java.util.Map;

public interface IChatModelListenerProvider {

    ChatModelListener getChatModelListener(AiModelCallSourceEnum source, String sessionId, String userId, Long agentId);
    ChatModelListener getChatModelListener(AiModelCallSourceEnum source, String sessionId, String userId, Long agentId, Map<String,Object> exData);
}
