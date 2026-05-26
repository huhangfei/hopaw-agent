package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.mapper.ChatSessionMapper;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatSessionService implements IChatSessionService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatHistoryMapper chatHistoryMapper;

    public ChatSessionService(ChatSessionMapper chatSessionMapper, ChatHistoryMapper chatHistoryMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @Override
    public List<ChatSession> getAllSessions() {
        return chatSessionMapper.findAll();
    }

    @Override
    public List<ChatSession> getSessionsByUserId(String userId) {
        return chatSessionMapper.findByUserId(userId);
    }

    @Override
    public List<ChatSession> getSessionsByUserIdAndAgentId(String userId, Long agentId) {
        return chatSessionMapper.findByUserIdAndAgentId(userId, agentId);
    }

    @Override
    public ChatSession getSessionById(Long id) {
        return chatSessionMapper.findById(id);
    }

    @Override
    public ChatSession getSessionBySessionId(String sessionId) {
        return chatSessionMapper.findBySessionId(sessionId);
    }

    @Override
    public List<ChatHistory> getChatHistoryBySessionId(String sessionId, int limit) {
        return chatHistoryMapper.findBySessionId(sessionId, limit);
    }

    @Override
    public ChatSession createSession(Long agentId, String userId, String title) {
        String sessionId = UUID.randomUUID().toString();
        ChatSession chatSession = new ChatSession(sessionId, agentId, userId, title);
        chatSessionMapper.insert(chatSession);
        return chatSession;
    }

    @Override
    public ChatSession createSessionWithId(Long agentId, String userId, String title, String sessionId) {
        ChatSession chatSession = new ChatSession(sessionId, agentId, userId, title);
        chatSessionMapper.insert(chatSession);
        return chatSession;
    }

    @Override
    public void updateSession(ChatSession chatSession) {
        chatSessionMapper.update(chatSession);
    }

    @Override
    public void updateSessionTitle(Long id, String title) {
        chatSessionMapper.updateTitle(id, title);
    }

    @Override
    public void deleteSession(Long id) {
        ChatSession session = chatSessionMapper.findById(id);
        if (session != null) {
            chatHistoryMapper.deleteBySessionId(session.getSessionId());
            chatSessionMapper.deleteById(id);
        }
    }

    @Override
    public void deleteSessionBySessionId(String sessionId) {
        chatHistoryMapper.deleteBySessionId(sessionId);
        chatSessionMapper.deleteBySessionId(sessionId);
    }

    @Override
    public void deleteSessionsByAgentId(Long agentId) {
        List<ChatSession> sessions = chatSessionMapper.findByUserIdAndAgentId(null, agentId);
        for (ChatSession session : sessions) {
            chatHistoryMapper.deleteBySessionId(session.getSessionId());
        }
        chatSessionMapper.deleteByAgentId(agentId);
    }
}
