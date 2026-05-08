package com.agent.hopaw.service;

import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.ChatMemory;
import com.agent.hopaw.model.ChatMemoryId;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 
 */
@Service
public class SQLiteChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryMapper chatMemoryMapper;
    private final ScheduledTaskService scheduledTaskService;
    public SQLiteChatMemoryStore(ChatMemoryMapper chatMemoryMapper,  ScheduledTaskService scheduledTaskService) {
        this.chatMemoryMapper = chatMemoryMapper;
        this.scheduledTaskService = scheduledTaskService;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryIdObj) {
        ChatMemoryId memoryId = (ChatMemoryId) memoryIdObj;
        List<Integer> status=new ArrayList<>();
        status.add(0);
        if(scheduledTaskService.isTaskRunning("longTermMemory")){
            status.add(1);
        }
        List<ChatMemory> records = chatMemoryMapper.findByAgentIdAndUserIdInStatus(memoryId.getAgentId(), memoryId.getUserId(), status);
        LinkedHashMap<String, ChatMessage> messages = new LinkedHashMap<>(records.size());
        for (ChatMemory record : records) {
            String messageJson = record.getMessageJson();
            if (messageJson != null) {
                try {
                    ChatMessage message = ChatMessageDeserializer.messageFromJson(messageJson);
                    if (record.getStatus().equals(1) && message instanceof SystemMessage) {
                        continue;
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

        Set<String> messageIds = new HashSet<>(messages.size());
        for (ChatMessage message : messages) {
            String messageId = generateMessageId(message);
            String messageJson = ChatMessageSerializer.messageToJson(message);
            messageIds.add(messageId);
            if (!memoryMap.containsKey(messageId)) {
                chatMemoryMapper.insert(memoryId.getAgentId(), memoryId.getUserId(), messageId, messageJson);
            }
        }
        for (String messageId : memoryMap.keySet()) {
            if (!messageIds.contains(messageId)) {
                chatMemoryMapper.updateStatus(memoryId.getAgentId(), memoryId.getUserId(), messageId, 1);
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
}
