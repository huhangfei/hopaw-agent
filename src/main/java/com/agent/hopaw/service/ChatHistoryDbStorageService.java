package com.agent.hopaw.service;

import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.model.ChatHistory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
                LocalDateTime startTime = old.getCreateTime();
                long toolExecutionTime = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
                chatHistory.setToolExecutionTime(toolExecutionTime);
                chatHistory.setId(old.getId());
                chatHistoryMapper.updateToolCallStatusAndContent(chatHistory.getId(), chatHistory.getToolCallStatus(), chatHistory.getContent(), chatHistory.getToolExecutionTime());
            }else{
                chatHistory.setToolExecutionTime(0L);
                chatHistoryMapper.insert(chatHistory);
            }
        }else{
            chatHistoryMapper.insert(chatHistory);
        }
    }

    @Override
    public void saveChatHistoryBatch(List<ChatHistory> chatHistories) {
        if (chatHistories == null || chatHistories.isEmpty()) {
            return;
        }
        chatHistoryMapper.insertBatch(chatHistories);
    }
}
