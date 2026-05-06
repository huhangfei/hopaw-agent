package com.agent.hopaw.service;

import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.ChatMemory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 
 */
public class SQLiteChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryMapper chatMemoryMapper;
    private final Long agentId;
    private final String userId;
    public SQLiteChatMemoryStore(ChatMemoryMapper chatMemoryMapper, Long agentId, String userId) {
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentId = agentId;
        this.userId = userId;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMemory> records = chatMemoryMapper.findByAgentIdAndUserId(agentId, userId);
        LinkedHashMap<String, ChatMessage> messages = new LinkedHashMap<>(records.size());
        for (ChatMemory record : records) {
            if (record.getStatus().equals(2)){
                continue;
            }
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
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<ChatMemory> existingRecords = chatMemoryMapper.findByAgentIdAndUserId(agentId, userId);
        Map<String, ChatMemory> memoryMap = existingRecords.stream()
                .collect(Collectors.toMap(ChatMemory::getMessageId, record -> record));

        Set<String> messageIds = new HashSet<>(messages.size());
        for (ChatMessage message : messages) {
            String messageId = generateMessageId(message);
            String messageJson = ChatMessageSerializer.messageToJson(message);
            messageIds.add(messageId);
            if (!memoryMap.containsKey(messageId)) {
                chatMemoryMapper.insert(agentId, userId, messageId, messageJson);
            }
        }
        for (String messageId : memoryMap.keySet()) {
            if (!messageIds.contains(messageId)) {
                chatMemoryMapper.updateStatus(agentId, userId, messageId, 1);
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMemoryMapper.deleteByAgentIdAndUserId(agentId, userId);
    }

    private String generateMessageId(ChatMessage message) {
        String content = message.toString();
        return UUID.nameUUIDFromBytes((content).getBytes()).toString();
    }
}
