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

public class SQLiteChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryMapper chatMemoryMapper;
    private final Long agentId;
    private final LongTermMemoryService longTermMemoryService;

    public SQLiteChatMemoryStore(ChatMemoryMapper chatMemoryMapper, Long agentId, LongTermMemoryService longTermMemoryService) {
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentId = agentId;
        this.longTermMemoryService = longTermMemoryService;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<ChatMemory> records = chatMemoryMapper.findByAgentId(agentId);
        Map<String, ChatMessage> messages = new LinkedHashMap<>(records.size());
        String rootMemory = longTermMemoryService.getRootMemory(agentId.toString());
        if(StringUtils.hasLength(rootMemory)){
            SystemMessage longTermMemoryMessage = new SystemMessage("这是所有记忆分类，如果需要详细的记忆内容可以根据记忆编号查询所有子记忆：" + rootMemory);
            messages.put(generateMessageId(longTermMemoryMessage), longTermMemoryMessage);
        }
        for (ChatMemory record : records) {
            String messageJson = record.getMessageJson();
            if (messageJson != null) {
                try {
                    ChatMessage message = ChatMessageDeserializer.messageFromJson(messageJson);
                    String messageId = generateMessageId(message);
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
        List<ChatMemory> existingRecords = chatMemoryMapper.findByAgentId(agentId);
        Map<String, ChatMemory> memoryMap = existingRecords.stream()
                .collect(Collectors.toMap(ChatMemory::getMessageId, record -> record));

        Set<String> messageIds = new HashSet<>(messages.size());
        for (ChatMessage message : messages) {
            String messageId = generateMessageId(message);
            String messageJson = ChatMessageSerializer.messageToJson(message);
            messageIds.add(messageId);
            if (!memoryMap.containsKey(messageId)) {
                chatMemoryMapper.insert(agentId, messageId, messageJson);
            }
        }
        for (String messageId : memoryMap.keySet()) {
            if (!messageIds.contains(messageId)) {
                chatMemoryMapper.markCleaned(agentId, messageId);
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMemoryMapper.deleteByAgentId(agentId);
    }

    private String generateMessageId(ChatMessage message) {
        String content = "";
        if (message instanceof UserMessage) {
            content = ((UserMessage) message).toString();
        } else if (message instanceof AiMessage) {
            content = ((AiMessage) message).toString();
        } else if (message instanceof SystemMessage) {
            content = ((SystemMessage) message).toString();
        } else if (message instanceof ToolExecutionResultMessage) {
            content = ((ToolExecutionResultMessage) message).toString();
        }

        return UUID.nameUUIDFromBytes((content).getBytes()).toString();
    }
}
