package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;

import java.util.List;
import java.util.Map;

public interface IChatSessionService {
    List<ChatSession> getAllSessions();

    List<ChatSession> getSessionsByUserId(String userId);

    List<ChatSession> getSessionsByUserIdAndAgentId(String userId, Long agentId);

    /** 首页可见会话：用户自己的聊天会话 + 所有人的项目/工作流任务会话 */
    List<ChatSession> getVisibleSessions(String userId, Long agentId);

    ChatSession getSessionById(Long id);

    ChatSession getSessionBySessionId(String sessionId);

    List<ChatHistory> getChatHistoryBySessionId(String sessionId, int limit);

    ChatSession createSession(Long agentId, String userId, String title);

    ChatSession createSessionWithId(String userId, String title, String sessionId);

    void updateSession(ChatSession chatSession);

    void updateSessionTitle(Long id, String title);

    void deleteSession(Long id);

    void deleteSessionBySessionId(String sessionId);

    void deleteSessionsByAgentId(Long agentId);

    ChatSession insertSession(ChatSession chatSession);

    void updateBizType(String sessionId, String bizType);

    /** 分页查询用户会话及消息记录数量（会话清理设置页），返回 total/page/pageSize/list */
    Map<String, Object> getSessionStatsPage(String userId, int page, int pageSize);
}
