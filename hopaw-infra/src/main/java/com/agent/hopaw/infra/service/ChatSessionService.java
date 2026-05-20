package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.mapper.ChatSessionMapper;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ChatSessionService {
    private final ChatSessionMapper chatSessionMapper;
    private final ChatHistoryMapper chatHistoryMapper;

    public ChatSessionService(ChatSessionMapper chatSessionMapper, ChatHistoryMapper chatHistoryMapper) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    public List<ChatSession> getAllSessions() {
        return chatSessionMapper.findAll();
    }

    public List<ChatSession> getSessionsByUserId(String userId) {
        return chatSessionMapper.findByUserId(userId);
    }

    public List<ChatSession> getSessionsByUserIdAndAgentId(String userId, Long agentId) {
        return chatSessionMapper.findByUserIdAndAgentId(userId, agentId);
    }

    public ChatSession getSessionById(Long id) {
        return chatSessionMapper.findById(id);
    }

    public ChatSession getSessionBySessionId(String sessionId) {
        return chatSessionMapper.findBySessionId(sessionId);
    }

    public List<ChatHistory> getChatHistoryBySessionId(String sessionId, int limit) {
        return chatHistoryMapper.findBySessionId(sessionId, limit);
    }

    public ChatSession createSession(Long agentId, String userId, String title) {
        String sessionId = UUID.randomUUID().toString();
        ChatSession chatSession = new ChatSession(sessionId, agentId, userId, title);
        chatSessionMapper.insert(chatSession);
        return chatSession;
    }

    public ChatSession createSessionWithId(Long agentId, String userId, String title, String sessionId) {
        ChatSession chatSession = new ChatSession(sessionId, agentId, userId, title);
        chatSessionMapper.insert(chatSession);
        return chatSession;
    }

    public void updateSession(ChatSession chatSession) {
        chatSessionMapper.update(chatSession);
    }

    public void updateSessionTitle(Long id, String title) {
        chatSessionMapper.updateTitle(id, title);
    }

    public void deleteSession(Long id) {
        ChatSession session = chatSessionMapper.findById(id);
        if (session != null) {
            chatHistoryMapper.deleteBySessionId(session.getSessionId());
            chatSessionMapper.deleteById(id);
        }
    }

    public void deleteSessionBySessionId(String sessionId) {
        chatHistoryMapper.deleteBySessionId(sessionId);
        chatSessionMapper.deleteBySessionId(sessionId);
    }

    public void deleteSessionsByAgentId(Long agentId) {
        List<ChatSession> sessions = chatSessionMapper.findByUserIdAndAgentId(null, agentId);
        for (ChatSession session : sessions) {
            chatHistoryMapper.deleteBySessionId(session.getSessionId());
        }
        chatSessionMapper.deleteByAgentId(agentId);
    }
}
