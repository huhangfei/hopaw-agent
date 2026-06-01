package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.monitor.LangChain4jChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class ChatModelListenerProvider implements IChatModelListenerProvider{
    private final ApplicationEventPublisher eventPublisher;

    public ChatModelListenerProvider(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public ChatModelListener getChatModelListener(AiModelCallSourceEnum source,String sessionId, String userId, Long agentId) {
        return new LangChain4jChatModelListener(source).setAgentId(agentId).setUserId(userId).setSessionId(sessionId).setEventPublisher(eventPublisher);
    }
}
