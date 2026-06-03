package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.ChatMemoryStatusEnum;
import com.agent.hopaw.infra.mapper.ChatMemoryMapper;
import com.agent.hopaw.infra.model.entity.ChatMemory;
import com.agent.hopaw.infra.model.entity.ChatMemoryId;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 
 */
@Service
public class SQLiteChatMemoryStore implements IChatMemoryService {
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(SQLiteChatMemoryStore.class);
    private final ChatMemoryMapper chatMemoryMapper;
    private final ILongTermMemoryProvider longTermMemoryProvider;
    public SQLiteChatMemoryStore(ChatMemoryMapper chatMemoryMapper, ILongTermMemoryProvider longTermMemoryProvider) {
        this.chatMemoryMapper = chatMemoryMapper;
        this.longTermMemoryProvider = longTermMemoryProvider;
    }

    private List<ChatMemory> getChatMemories(ChatMemoryId memoryId) {
        List<ChatMemory> records = chatMemoryMapper.findBySessionId(memoryId.getSessionId());
        return records;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryIdObj) {
        ChatMemoryId memoryId = (ChatMemoryId) memoryIdObj;
        List<ChatMemory> records = getChatMemories(memoryId);
        LinkedHashMap<String, ChatMessage> messages = new LinkedHashMap<>(records.size());
        HashSet<String> toolExecutionResultToolIds = new HashSet<>();
        for (ChatMemory record : records) {
            String messageJson = record.getMessageJson();
            if (messageJson != null) {
                try {
                    ChatMessage message = ChatMessageDeserializer.messageFromJson(messageJson);
                    if (record.getStatus().equals(1) && message instanceof SystemMessage) {
                        continue;
                    }
                    if(message instanceof ToolExecutionResultMessage){
                        toolExecutionResultToolIds.add(((ToolExecutionResultMessage) message).id());
                    }
                    String messageId = record.getMessageId();
                    if(messages.containsKey(messageId)){
                        messages.remove(messageId);
                    }
                    messages.put(messageId, message);
                } catch (Exception e) {
                }
            }
        }
        return messages.values().stream().toList();
    }

    /**
     * @param memoryIdObj
     * @param messages
     */
    @Override
    public void updateMessages(Object memoryIdObj, List<ChatMessage> messages) {
        ChatMemoryId memoryId = (ChatMemoryId) memoryIdObj;
        List<ChatMemory> existingRecords = chatMemoryMapper.findBySessionId(memoryId.getSessionId());
        Map<String, ChatMemory> memoryMap = existingRecords.stream()
                .collect(Collectors.toMap(ChatMemory::getMessageId, record -> record));

        Set<String> messageIds = new HashSet<>(messages.size());
        for (ChatMessage message : messages) {
            String messageId = generateMessageId(message);
            String messageJson = ChatMessageSerializer.messageToJson(message);
            messageIds.add(messageId);
            if (!memoryMap.containsKey(messageId)) {
                //持久化到数据库
                chatMemoryMapper.insert(memoryId.getAgentId(), memoryId.getUserId(), messageId, messageJson, memoryId.getSessionId(),memoryId.getRequestId(), LocalDateTime.now());
              }
        }
        List<ChatMemory> deletedMemoryList=new ArrayList<>();
        for (Map.Entry<String, ChatMemory> item : memoryMap.entrySet()) {
            if (!messageIds.contains(item.getKey())) {
                //在窗口记忆中移除
                chatMemoryMapper.deleteByMessageId(memoryId.getSessionId(), memoryId.getUserId(),item.getKey());
                deletedMemoryList.add(item.getValue());
            }
        }
        if(!deletedMemoryList.isEmpty()){
            //写入长时记忆
            longTermMemoryProvider.receiveUserTempMemory(deletedMemoryList);
        }
    }

    @Override
    public void deleteMessages(Object memoryIdObj) {
        ChatMemoryId memoryId = (ChatMemoryId) memoryIdObj;
        chatMemoryMapper.deleteBySessionIdAndUserId(memoryId.getSessionId(), memoryId.getUserId());
    }

    private String generateMessageId(ChatMessage message) {
        String content = message.toString();
        return UUID.nameUUIDFromBytes((content).getBytes()).toString();
    }

    /**
     * 清理历史孤儿信息
     * @param memoryId
     */
    @Override
    public void orphanCleanup(ChatMemoryId memoryId){
        List<ChatMemory> original = getChatMemories(memoryId);
        if (original == null || original.isEmpty()) {
            return;
        }

        // 1. 收集所有已完成的 ToolExecutionResultMessage 的 ID
        Set<String> resolvedCallIds = new HashSet<>();
        Map<String, LocalDateTime> callToolRequestCreateTime=new HashMap<>();
        List<ChatMessage> chatMessages = new ArrayList<>();
        for (ChatMemory memory : original) {
            ChatMessage msg = ChatMessageDeserializer.messageFromJson(memory.getMessageJson());
            if (msg instanceof ToolExecutionResultMessage) {
                resolvedCallIds.add(((ToolExecutionResultMessage) msg).id());
            }else if(msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()){
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    callToolRequestCreateTime.put(req.id(), memory.getCreateTime());
                }
            }
            chatMessages.add(msg);
        }
        // 2. 构建清理后的列表
        for (ChatMessage msg : chatMessages) {
            if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                // 存在孤儿调用：保留 AiMessage 本身，插入错误结果
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    if (!resolvedCallIds.contains(req.id())) {
                        ToolExecutionResultMessage errorMsg = new ToolExecutionResultMessage.Builder()
                                .id(req.id())
                                .toolName(req.name())
                                .text("工具调用因服务中断而失败，请重试或向用户说明情况。")
                                .isError(false)
                                .build();
                        String messageId = generateMessageId(errorMsg);
                        String messageJson = ChatMessageSerializer.messageToJson(errorMsg);
                        LocalDateTime time = callToolRequestCreateTime.getOrDefault(req.id(), LocalDateTime.now());
                        chatMemoryMapper.insert(memoryId.getAgentId(), memoryId.getUserId(), messageId, messageJson, memoryId.getSessionId(), memoryId.getRequestId(), time.plus(1, ChronoUnit.MILLIS));
                        logger.info("为tool call {} 创建了错误结果 {}", req.id(), messageId);
                    }
                }
            }
        }
    }

    @Override
    public void clear(String sessionId) {
        List<ChatMemory> records = chatMemoryMapper.findBySessionId(sessionId);
        if(records.isEmpty()){
            return;
        }
        longTermMemoryProvider.receiveUserTempMemory(records);
        chatMemoryMapper.deleteBySessionIdAndUserId(sessionId,null);
    }

    @Override
    public int updateStatusBySessionIdAndRequestId(String sessionId, String requestId, ChatMemoryStatusEnum status, ChatMemoryStatusEnum newStatus) {
        return chatMemoryMapper.updateStatusBySessionIdAndRequestId(sessionId, requestId, status.getCode(),newStatus.getCode());
    }

    @Override
    public List<ChatMemory> findBySessionIdAndStatus(String sessionId, List<Integer> status) {
        return chatMemoryMapper.findBySessionIdAndStatus(sessionId, status);
    }

    @Override
    public int deleteByIds(List<Long> ids) {
        return chatMemoryMapper.deleteByIds(ids);
    }
}
