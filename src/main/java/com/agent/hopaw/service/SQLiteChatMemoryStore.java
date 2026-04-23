package com.agent.hopaw.service;

import com.agent.hopaw.mapper.ChatMemoryMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SQLiteChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryMapper chatMemoryMapper;
    private final Long agentId;

    public SQLiteChatMemoryStore(ChatMemoryMapper chatMemoryMapper, Long agentId) {
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentId = agentId;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        List<Map<String, Object>> records = chatMemoryMapper.findByAgentId(agentId);
        List<ChatMessage> messages = new ArrayList<>();
        
        for (Map<String, Object> record : records) {
            String messageJson = (String) record.get("message_json");
            if (messageJson != null) {
                try {
                    ChatMessage message = ChatMessageDeserializer.messageFromJson(messageJson);
                    messages.add(message);
                } catch (Exception e) {
                }
            }
        }
        
        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<Map<String, Object>> existingRecords = chatMemoryMapper.findByAgentId(agentId);
        
        for (Map<String, Object> record : existingRecords) {
            String messageId = (String) record.get("message_id");
            chatMemoryMapper.deleteByMessageId(agentId, messageId);
        }
        
        for (ChatMessage message : messages) {
            String messageId = generateMessageId(message);
            String messageJson = ChatMessageSerializer.messageToJson(message);
            chatMemoryMapper.insert(agentId, messageId, messageJson);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMemoryMapper.deleteByAgentId(agentId);
    }

    private String generateMessageId(ChatMessage message) {
        String content = "";
        if (message instanceof UserMessage) {
            content = ((UserMessage) message).singleText();
        } else if (message instanceof AiMessage) {
            content = ((AiMessage) message).text();
        } else if (message instanceof SystemMessage) {
            content = ((SystemMessage) message).text();
        } else if (message instanceof ToolExecutionResultMessage) {
            content = ((ToolExecutionResultMessage) message).text();
        }
        
        return UUID.nameUUIDFromBytes((content + System.currentTimeMillis()).getBytes()).toString();
    }
}
