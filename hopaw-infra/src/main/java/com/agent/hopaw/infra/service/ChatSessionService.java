package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.mapper.ChatSessionMapper;
import com.agent.hopaw.infra.model.dto.ChatSessionStatsVO;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ChatSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    public List<ChatSession> getVisibleSessions(String userId, Long agentId) {
        return chatSessionMapper.findVisibleSessions(userId, agentId);
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
    public ChatSession createSessionWithId(String userId, String title, String sessionId) {
        ChatSession chatSession = new ChatSession(sessionId, userId, title);
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

    @Override
    public ChatSession insertSession(ChatSession chatSession) {
        chatSessionMapper.insert(chatSession);
        return chatSession;
    }

    @Override
    public void updateBizType(String sessionId, String bizType) {
        chatSessionMapper.updateBizType(sessionId, bizType);
    }

    /**
     * 分页查询用户会话及消息记录数量（会话清理设置页）
     */
    @Override
    public Map<String, Object> getSessionStatsPage(String userId, int page, int pageSize) {
        int total = chatSessionMapper.countByUserId(userId);
        int offset = Math.max(0, (page - 1) * pageSize);
        List<ChatSession> sessions = chatSessionMapper.findPageByUserId(userId, offset, pageSize);

        // 批量统计消息数量，避免逐会话查询
        Map<String, Long> countMap = new HashMap<>();
        if (!sessions.isEmpty()) {
            List<String> sessionIds = sessions.stream().map(ChatSession::getSessionId).collect(Collectors.toList());
            List<Map<String, Object>> rows = chatHistoryMapper.countMessagesBySessionIds(sessionIds);
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Object sid = row.get("session_id");
                    Object cnt = row.get("cnt");
                    if (sid != null && cnt != null) {
                        countMap.put(sid.toString(), ((Number) cnt).longValue());
                    }
                }
            }
        }

        List<ChatSessionStatsVO> list = new ArrayList<>(sessions.size());
        for (ChatSession s : sessions) {
            ChatSessionStatsVO vo = new ChatSessionStatsVO();
            vo.setId(s.getId());
            vo.setSessionId(s.getSessionId());
            vo.setTitle(s.getTitle());
            vo.setBizType(s.getBizType());
            vo.setCreateTime(s.getCreateTime());
            vo.setLastUpdateTime(s.getLastUpdateTime());
            vo.setMessageCount(countMap.getOrDefault(s.getSessionId(), 0L));
            list.add(vo);
        }

        Map<String, Object> result = new HashMap<>(4);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }
}
