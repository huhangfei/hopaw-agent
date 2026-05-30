package com.agent.hopaw.infra.storage;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.memory.IVectorMemoryService;
import com.agent.hopaw.infra.model.entity.ChatHistory;
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

    private final IVectorMemoryService vectorMemoryService;
    public SqliteChatHistoryDbStore(ChatHistoryMapper chatHistoryMapper, IVectorMemoryService vectorMemoryService) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.vectorMemoryService = vectorMemoryService;
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
        //存储到向量数据库
        vectorMemoryService.store(formatMemoryContent(chatHistory), chatHistory.getSessionId(), chatHistory.getUserId(), UserMemoryTypeEnum.CHAT_HISTORY, LocalDateTime.now());
    }

    @Override
    public void saveChatHistoryBatch(List<ChatHistory> chatHistories) {
        if (chatHistories == null || chatHistories.isEmpty()) {
            return;
        }
        chatHistoryMapper.insertBatch(chatHistories);
        for (ChatHistory chatHistory : chatHistories) {
            //存储到向量数据库
            vectorMemoryService.store(formatMemoryContent(chatHistory), chatHistory.getSessionId(), chatHistory.getUserId(), UserMemoryTypeEnum.CHAT_HISTORY, LocalDateTime.now());
        }

    }

    private String formatMemoryContent(ChatHistory chatHistory) {
        if (chatHistory.getMessageType().equals("tool_call")) {
            return chatHistory.getRole() + ": " + chatHistory.getMessageType() + ": " + chatHistory.getToolName() + ": " + chatHistory.getToolArguments()+ ": " + chatHistory.getContent();
        }
        return chatHistory.getRole() + ": " + chatHistory.getMessageType() + ": " + chatHistory.getContent();
    }
}
