package com.agent.hopaw.service;

import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.ChatHistory;
import org.springframework.stereotype.Service;

@Service
public class ChatHistoryDbStorageService implements ChatHistoryStorageService{

    private final ChatHistoryMapper chatHistoryMapper;

    public ChatHistoryDbStorageService(ChatHistoryMapper chatHistoryMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @Override
    public void saveChatHistory(ChatHistory chatHistory) {
        if(chatHistory.getMessageType().equals("tool_call")){
            ChatHistory old = chatHistoryMapper.findByAgentIdAndToolCallId(chatHistory.getAgentId(), chatHistory.getToolCallId());
            if(old != null){
                chatHistory.setId(old.getId());
                chatHistoryMapper.updateToolCallStatusAndContent(chatHistory.getId(), chatHistory.getToolCallStatus(), chatHistory.getContent());
            }else{
                chatHistoryMapper.insert(chatHistory);
            }
        }else{
            chatHistoryMapper.insert(chatHistory);
        }
    }
}
