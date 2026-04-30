package com.agent.hopaw.task;

import com.agent.hopaw.config.ChatModelFactoryConfig;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ChatMemory;
import com.agent.hopaw.model.ChatModelFactory;
import com.agent.hopaw.service.LangChain4jMonitoringService;
import com.agent.hopaw.service.LongTermMemoryService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class LongTermMemoryTask {
    private final ChatModelFactory chatModelFactory;
    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryTask.class);

    private final LongTermMemoryService longTermMemoryService;
    private final LangChain4jMonitoringService monitoringService;
    private final ChatMemoryMapper chatMemoryMapper;
    private final AgentMapper agentMapper;

    public LongTermMemoryTask(ChatModelFactoryConfig chatModelFactoryConfig, LongTermMemoryService longTermMemoryService,
                              ChatMemoryMapper chatMemoryMapper,
                              LangChain4jMonitoringService monitoringService, AgentMapper agentMapper) {
        this.chatModelFactory = chatModelFactoryConfig.getFactory();
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


            String systemMessage = "你是一个记忆整理助手。善于根据聊天记录提取关键的用户记忆信息。" +
                    "请根据内容总结出用户的关键记忆信息，并按以下格式进行分类，分类不够可以自己添加，但是分类要精简。记忆内容不能胡编乱造信息，要完全从内容中来：" +
                    "========\n" +
                    "1,基础档案\n" +
                    "个人特质、地域作息、性格、身份角色、核心标签、敏感雷区等\n" +
                    "2,工作职场\n" +
                    "岗位业务、负责项目、技术 / 专业栈、协作习惯、工作痛点、目标规划、常用工具规范等\n" +
                    "3,生活日常\n" +
                    "家庭情况、饮食作息、消费偏好、出行习惯、休闲爱好等\n" +
                    "4,健康状况\n" +
                    "身体症状、慢病困扰、用药习惯、体质特点、就医相关等\n" +
                    "5,需求偏好\n" +
                    "高频诉求、内容输出偏好、功能需求、长期规划（理财 / 生活 / 学习）等\n" +
                    "6,沟通交互\n" +
                    "说话风格、回复格式偏好、交互习惯、定制化要求等\n" +
                    "7,关键事件\n" +
                    "重要时间节点、过往关键经历、待办长期事项、特殊记录等\n" +
                    "8,知识沉淀\n" +
                    "高频咨询问题、专属认知观点、常用资料 / 规则、成功处理任务经验等\n" +
                    "========\n" +
                    "请认真总结记忆得到清单后进行检查，不要有重复的或分类不对的记忆。\n" +
                    "在完成记忆总结后，你可以调用保存智能体记忆工具。\n" +
                    "归类后先保存分类作为父级记忆得到编号，再保存概要内容作为子级记忆，子级记忆的parentId是父级记忆的编号。\n" +
                    "本次记忆的identity是" + identity;

            ChatModel chatModel = chatModelFactory.createChatModel(null, null, false);

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
