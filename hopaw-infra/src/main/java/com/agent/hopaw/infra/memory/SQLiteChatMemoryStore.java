package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.mapper.ChatMemoryMapper;
import com.agent.hopaw.infra.service.ScheduledTaskService;
import com.agent.hopaw.infra.model.entity.ChatMemory;
import com.agent.hopaw.infra.model.entity.ChatMemoryId;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 
 */
@Service
public class SQLiteChatMemoryStore implements ChatMemoryStore {
    private final Logger logger = org.slf4j.LoggerFactory.getLogger(SQLiteChatMemoryStore.class);
    private final ChatMemoryMapper chatMemoryMapper;
    private final ScheduledTaskService scheduledTaskService;
    public SQLiteChatMemoryStore(ChatMemoryMapper chatMemoryMapper,  ScheduledTaskService scheduledTaskService) {
        this.chatMemoryMapper = chatMemoryMapper;
        this.scheduledTaskService = scheduledTaskService;
    }

    private List<ChatMemory> getChatMemories(ChatMemoryId memoryId) {
        List<Integer> status=new ArrayList<>();
        status.add(0);
        if(scheduledTaskService.isTaskRunning("longTermMemory")){
            status.add(1);
            status.add(3);
        }
        List<ChatMemory> records = chatMemoryMapper.findByAgentIdAndUserIdInStatus(memoryId.getAgentId(), memoryId.getUserId(), status);
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

    @Override
    public void updateMessages(Object memoryIdObj, List<ChatMessage> messages) {
        ChatMemoryId memoryId = (ChatMemoryId) memoryIdObj;
        List<ChatMemory> existingRecords = chatMemoryMapper.findByAgentIdAndUserIdInStatus(memoryId.getAgentId(), memoryId.getUserId(), Arrays.asList(0));
        Map<String, ChatMemory> memoryMap = existingRecords.stream()
                .collect(Collectors.toMap(ChatMemory::getMessageId, record -> record));
        boolean longTermMemoryTaskRunning=scheduledTaskService.isTaskRunning("longTermMemory");

        Set<String> messageIds = new HashSet<>(messages.size());
        for (ChatMessage message : messages) {
            String messageId = generateMessageId(message);
            String messageJson = ChatMessageSerializer.messageToJson(message);
            messageIds.add(messageId);
            if (!memoryMap.containsKey(messageId)) {
                chatMemoryMapper.insert(memoryId.getAgentId(), memoryId.getUserId(), messageId, messageJson, LocalDateTime.now());
            }
        }
        for (String messageId : memoryMap.keySet()) {
            if (!messageIds.contains(messageId)) {
                if(longTermMemoryTaskRunning){
                    chatMemoryMapper.updateStatus(memoryId.getAgentId(), memoryId.getUserId(), messageId, 1);
                }else{
                    chatMemoryMapper.deleteByMessageId(memoryId.getAgentId(), memoryId.getUserId(), messageId);
                }
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryIdObj) {
        ChatMemoryId memoryId = (ChatMemoryId) memoryIdObj;
        chatMemoryMapper.deleteByAgentIdAndUserId(memoryId.getAgentId(), memoryId.getUserId());
    }

    private String generateMessageId(ChatMessage message) {
        String content = message.toString();
        return UUID.nameUUIDFromBytes((content).getBytes()).toString();
    }

    /**
     * 清理历史孤儿信息，同时将状态1的消息转到状态3
     * @param memoryId
     */
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
                        chatMemoryMapper.insert(memoryId.getAgentId(), memoryId.getUserId(),messageId, messageJson,time.plus(1, ChronoUnit.MILLIS));
                        logger.info("为tool call {} 创建了错误结果 {}", req.id(), messageId);
                    }
                }
            }
        }

        chatMemoryMapper.updateStatusByStatus(memoryId.getAgentId(), memoryId.getUserId(), 1, 3);
    }
}
