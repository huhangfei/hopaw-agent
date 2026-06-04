package com.agent.hopaw.infra.storage;

import com.agent.hopaw.infra.event.ChatHistoryEvent;
import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * @author hhf
 */
@Service
public class SqliteChatHistoryDbStore implements ChatHistoryStore {

    private final ChatHistoryMapper chatHistoryMapper;

    public SqliteChatHistoryDbStore(ChatHistoryMapper chatHistoryMapper) {
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @EventListener
    public void listenChatHistoryEvent(ChatHistoryEvent chatHistoryEvent) {
        ChatHistory chatHistory = chatHistoryEvent.getChatHistory();
        if(chatHistory.getMessageType().equals("tool_call")){
            ChatHistory old = chatHistoryMapper.findBySessionIdAndToolCallId(chatHistory.getSessionId(), chatHistory.getToolCallId());
            if(old != null){
                LocalDateTime startTime = old.getCreateTime();
                long toolExecutionTime = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());
                chatHistory.setToolExecutionTime(toolExecutionTime);
                chatHistory.setId(old.getId());
                chatHistoryMapper.updateToolCallStatusAndContent(chatHistory.getId(), chatHistory.getToolCallStatus(),chatHistory.getToolArguments(), chatHistory.getContent(), chatHistory.getToolExecutionTime());
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
