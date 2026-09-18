package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ChatHistoryVO;
import com.agent.hopaw.infra.model.entity.ChatHistory;

import java.time.LocalDateTime;
import java.util.List;

public interface IChatHistoryService {
    List<ChatHistoryVO> findBySessionId(String sessionId, int limit);

    /** 游标向前分页：加载早于 (beforeTime, beforeId) 的会话历史（按时间倒序返回） */
    List<ChatHistoryVO> findBySessionIdBefore(String sessionId, LocalDateTime beforeTime, Long beforeId, int limit);
    int deleteBySessionId(String sessionId);
    /** 统计会话的工具调用总数 */
    int countToolCallsBySessionId(String sessionId);
}
