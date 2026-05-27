package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;

import java.util.List;

public interface IChatSessionService {
    List<ChatSession> getAllSessions();

    List<ChatSession> getSessionsByUserId(String userId);

    List<ChatSession> getSessionsByUserIdAndAgentId(String userId, Long agentId);

    ChatSession getSessionById(Long id);

    ChatSession getSessionBySessionId(String sessionId);

    List<ChatHistory> getChatHistoryBySessionId(String sessionId, int limit);

    ChatSession createSession(Long agentId, String userId, String title);

    ChatSession createSessionWithId(Long agentId, String userId, String title, String sessionId);

    void updateSession(ChatSession chatSession);

    void updateSessionTitle(Long id, String title);

    void deleteSession(Long id);

    void deleteSessionBySessionId(String sessionId);

    void deleteSessionsByAgentId(Long agentId);

    ChatSession insertSession(ChatSession chatSession);
}
