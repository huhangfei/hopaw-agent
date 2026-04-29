package com.agent.hopaw.task;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatHistoryMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ChatMemory;
import com.agent.hopaw.service.LangChain4jMonitoringService;
import com.agent.hopaw.service.LongTermMemoryService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
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
    private final LangChain4jMonitoringService monitoringService;
    private final ChatMemoryMapper chatMemoryMapper;
    private final AgentMapper agentMapper;

    public LongTermMemoryTask(LongTermMemoryService longTermMemoryService,
                              ChatMemoryMapper chatMemoryMapper,
                              LangChain4jMonitoringService monitoringService, AgentMapper agentMapper) {
        this.longTermMemoryService = longTermMemoryService;
        this.chatMemoryMapper = chatMemoryMapper;
        this.monitoringService = monitoringService;
        this.agentMapper = agentMapper;
    }

    @Scheduled(fixedDelay = 5000)
    public void processAgentMemories() {
        try {
            List<Agent> allAgents = agentMapper.findAll();
            for (Agent agent : allAgents) {
                try {
                    processMemoryForIdentity(agent);
                } catch (Exception e) {
                    logger.error("Error processing memory for agent {}", agent.getId(), e);
                }
            }


        } catch (Exception e) {
            logger.error("Error fetching agent ids for memory processing", e);
        }
    }

    private void processMemoryForIdentity(Agent agent) {

        //已标记清理的消息
        List<ChatMemory> cleanedMessages = chatMemoryMapper.findByAgentIdAndCleaned(agent.getId(), 1);

        if (cleanedMessages.isEmpty() || cleanedMessages.size() < 5) {
            return;
        }

        StringBuilder conversationBuilder = new StringBuilder();
        for (ChatMemory chat : cleanedMessages) {
            ChatMessage message = ChatMessageDeserializer.messageFromJson(chat.getMessageJson());
            conversationBuilder.append(message.toString());
        }

        String identity=agent.getId().toString();
        String newConversation = conversationBuilder.toString();
        String existingMemory = longTermMemoryService.getMemoryTree(identity);

        String memory = buildMemorySummary(existingMemory, newConversation);
        boolean handle = handle(identity, memory);
        if (handle) {
            chatMemoryMapper.deleteByIds(cleanedMessages.stream().map(ChatMemory::getId).toList());
        }
        logger.info("Processing memory for identity: {}, cleaned messages count: {}", identity, cleanedMessages.size());
    }

    private boolean handle(String identity, String content) {
        try {
            var builder = OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName(modelName)
                    .temperature(0.5)
                    .listeners(List.of(monitoringService));

            if (openaiBaseUrl != null && !openaiBaseUrl.isEmpty()) {
                builder.baseUrl(openaiBaseUrl);
            }

            String systemMessage = "你是一个记忆整理助手。善于根据聊天记录提取关键的用户记忆信息,不能胡编乱造信息，记忆要完全从内容中来。" +
                    "记忆需要先分类，分类要简洁，然后再总结概要到该分类下，概要要抓住重点信息尽量简洁。先保存分类作为父级记忆得到编号，再保存概要内容作为子级记忆，子级记忆的parentId是父级记忆的编号。" +
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
            return true;
        }catch (Exception ex){
            logger.error("Error processing memory for identity {}", identity, ex);
            return false;
        }
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
