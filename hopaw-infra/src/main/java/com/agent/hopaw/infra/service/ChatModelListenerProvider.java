package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.monitor.LangChain4jChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatModelListenerProvider implements IChatModelListenerProvider{
    private final ApplicationEventPublisher eventPublisher;
    private final IRequestResponseLogService requestResponseLogService;

    public ChatModelListenerProvider(ApplicationEventPublisher eventPublisher,
                                     IRequestResponseLogService requestResponseLogService) {
        this.eventPublisher = eventPublisher;
        this.requestResponseLogService = requestResponseLogService;
    }

    @Override
    public ChatModelListener getChatModelListener(AiModelCallSourceEnum source,String sessionId, String userId, Long agentId, String requestId) {
        return new LangChain4jChatModelListener(source)
                .setAgentId(agentId)
                .setUserId(userId)
                .setSessionId(sessionId)
                .setRequestId(requestId)
                .setRequestResponseLogService(requestResponseLogService)
                .setEventPublisher(eventPublisher);
    }

    @Override
    public ChatModelListener getChatModelListener(AiModelCallSourceEnum source, String sessionId, String userId, Long agentId, String requestId, Map<String, Object> exData) {
        return new LangChain4jChatModelListener(source)
                .setAgentId(agentId)
                .setUserId(userId)
                .setSessionId(sessionId)
                .setRequestId(requestId)
                .setExData(exData)
                .setRequestResponseLogService(requestResponseLogService)
                .setEventPublisher(eventPublisher);
    }
}
