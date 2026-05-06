package com.agent.hopaw.service;

import com.agent.hopaw.model.ChatHistory;

import java.util.List;

public interface ChatHistoryStorageService {
    void saveChatHistory(ChatHistory chatHistory);

    void saveChatHistoryBatch(List<ChatHistory> chatHistories);
}
