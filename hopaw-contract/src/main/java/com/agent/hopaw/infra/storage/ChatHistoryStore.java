package com.agent.hopaw.infra.storage;

import com.agent.hopaw.infra.model.entity.ChatHistory;

import java.util.List;

public interface ChatHistoryStore {
    void saveChatHistoryBatch(List<ChatHistory> chatHistories);
}
