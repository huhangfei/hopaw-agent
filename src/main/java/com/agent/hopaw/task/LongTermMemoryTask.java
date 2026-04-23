package com.agent.hopaw.task;

import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.mapper.MemoryProcessLogMapper;
import com.agent.hopaw.model.ChatHistory;
import com.agent.hopaw.model.LongTermMemory;
import com.agent.hopaw.service.AgentService;
import com.agent.hopaw.service.LongTermMemoryService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class LongTermMemoryTask {
    @Value("${openai.api.key:demo-key}")
    private String openaiApiKey;

    @Value("${openai.base.url:}")
    private String openaiBaseUrl;

    @Value("${openai.model.name:gpt-3.5-turbo}")
    private String modelName;
    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryTask.class);

    private final LongTermMemoryService longTermMemoryService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final MemoryProcessLogMapper memoryProcessLogMapper;

    public LongTermMemoryTask(LongTermMemoryService longTermMemoryService,
                              ChatHistoryMapper chatHistoryMapper,
                              MemoryProcessLogMapper memoryProcessLogMapper) {
        this.longTermMemoryService = longTermMemoryService;
        this.chatHistoryMapper = chatHistoryMapper;
        this.memoryProcessLogMapper = memoryProcessLogMapper;
    }

    @Scheduled(fixedRate = 60000)
    public void processGlobalMemory() {
        try {
            processMemoryForIdentity(LongTermMemoryService.GLOBAL_IDENTITY, null);
        } catch (Exception e) {
            logger.error("Error processing global memory", e);
        }
    }

    //@Scheduled(fixedRate = 60000)
    public void processAgentMemories() {
        try {
            List<Long> agentIds = chatHistoryMapper.findDistinctAgentIds();
            for (Long agentId : agentIds) {
                try {
                    processMemoryForIdentity(agentId.toString(), agentId);
                } catch (Exception e) {
                    logger.error("Error processing memory for agent {}", agentId, e);
                }
            }
        } catch (Exception e) {
            logger.error("Error fetching agent ids for memory processing", e);
        }
    }

    private void processMemoryForIdentity(String identity, Long agentId) {
        Long lastProcessedId = longTermMemoryService.getLastProcessedChatId(identity);
        lastProcessedId = lastProcessedId == null ? 0 : lastProcessedId;
        List<ChatHistory> newMessages;
        if (agentId != null) {
            newMessages = chatHistoryMapper.findByAgentIdAfterId(agentId, lastProcessedId);
        } else {
            newMessages = chatHistoryMapper.findAllAfterId(lastProcessedId);
        }

        if (newMessages.isEmpty()) {
            return;
        }

        StringBuilder conversationBuilder = new StringBuilder();
        for (ChatHistory chat : newMessages) {
            String role = "user".equals(chat.getRole()) ? "用户" : "助手";
            conversationBuilder.append(role).append(": ").append(chat.getContent()).append("\n");
        }

        String newConversation = conversationBuilder.toString();
        String existingMemory = longTermMemoryService.buildMemoryTree(identity);

        String memory = buildMemorySummary(existingMemory, newConversation);
        handle(identity,memory);
        logger.info("Processing memory for identity: {}, new messages count: {}", identity, newMessages.size());

        List<LongTermMemory> rootMemories = longTermMemoryService.getRootMemories(identity);


        long maxChatId = 0;
        for (ChatHistory chat : newMessages) {
            if (chat.getId() != null && chat.getId() > maxChatId) {
                maxChatId = chat.getId();
            }
        }
        longTermMemoryService.updateLastProcessedChatId(identity, maxChatId);
    }

    private void handle(String identity, String content) {
        var builder = OpenAiChatModel.builder()
                .apiKey(openaiApiKey)
                .modelName(modelName)
                .temperature(0.7);

        if (openaiBaseUrl != null && !openaiBaseUrl.isEmpty()) {
            builder.baseUrl(openaiBaseUrl);
        }

        String systemMessage = "你是一个记忆整理助手。善于根据聊天记录提取关键的用户记忆信息,不能胡编乱造信息，记忆要完全从内容中来。" +
                "记忆需要先分类汇总，先保存分类作为父级记忆得到编号，再保存记忆内容作为子级记忆，子级记忆的parentId是父级记忆的编号。" +
                "在你判断需要存储记忆时，你可以调用保存智能体记忆工具。" +
                "请认真总结记忆然后保存，最后给出保存记忆的清单。" +
                "本次记忆的identity是" + identity;

        OpenAiChatModel chatModel = builder.build();

        MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
                .chatModel(chatModel)
                .systemMessageProvider(chatMemoryId -> systemMessage)
                .tools(Arrays.asList(longTermMemoryService))
                .build();
        logger.info("开始汇总记忆 \n {}", content);
        String result = assistant.chat(content);
        logger.info("记忆汇总完毕：{}", result);
    }

    public interface MemoryAssistant {
        @UserMessage("{{content}}")
        String chat(String content);
    }

    private String buildMemorySummary(String existingMemory, String newConversation) {
        StringBuilder memory = new StringBuilder();
        memory.append("以下是需要分析的内容");
        memory.append("===========================\n");
        if (!existingMemory.isEmpty()) {
            memory.append("【现有记忆】\n").append(existingMemory).append("\n");
        }
        memory.append("【新对话】\n").append(newConversation).append("\n\n");
        memory.append("===========================");
        return memory.toString();
    }
}
