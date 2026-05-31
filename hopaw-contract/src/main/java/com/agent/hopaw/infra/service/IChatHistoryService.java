package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ChatHistoryVO;
import com.agent.hopaw.infra.model.entity.ChatHistory;

import java.util.List;

public interface IChatHistoryService {
    List<ChatHistoryVO> findBySessionId(String sessionId, int limit);
    int deleteBySessionId(String sessionId);
}
