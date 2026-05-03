package com.agent.hopaw.service;

import com.agent.hopaw.model.ChatHistory;

public interface ChatHistoryStorageService {
    void saveChatHistory(ChatHistory chatHistory);
}
