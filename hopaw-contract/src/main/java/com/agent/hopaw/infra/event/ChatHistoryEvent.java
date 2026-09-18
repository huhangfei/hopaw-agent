package com.agent.hopaw.infra.event;

import com.agent.hopaw.infra.model.entity.ChatHistory;

public class ChatHistoryEvent {

    private ChatHistory chatHistory;

    public ChatHistoryEvent(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }

    public ChatHistory getChatHistory() {
        return chatHistory;
    }

    public void setChatHistory(ChatHistory chatHistory) {
        this.chatHistory = chatHistory;
    }
}
