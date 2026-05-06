package com.agent.hopaw.task;

import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ChatMemory;
import com.agent.hopaw.model.ScheduledTask;
import com.agent.hopaw.service.AiModelService;
import com.agent.hopaw.service.SysConfigService;
import com.agent.hopaw.util.InvocationParametersUtil;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.*;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class LongTermMemoryTaskHandler implements TaskHandler {
    private final AiModelService aiModelService;
    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryTaskHandler.class);

    private final com.agent.hopaw.service.LongTermMemoryService longTermMemoryService;
    private final ChatMemoryMapper chatMemoryMapper;
    private final AgentMapper agentMapper;
    private final SysConfigService sysConfigService;

    public LongTermMemoryTaskHandler(AiModelService aiModelService,
                                     com.agent.hopaw.service.LongTermMemoryService longTermMemoryService,
                                     ChatMemoryMapper chatMemoryMapper, AgentMapper agentMapper,
                                     SysConfigService sysConfigService) {
        this.aiModelService = aiModelService;
        this.longTermMemoryService = longTermMemoryService;
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentMapper = agentMapper;
        this.sysConfigService = sysConfigService;
    }

    public void processAgentMemories(){
        try {
            List<Agent> allAgents = agentMapper.findAll();
            for (String userId : Arrays.asList(DefaultUser.USER)) {
                for (Agent agent : allAgents) {
                    try {
                        processMemoryForIdentity(agent,userId);
                    } catch (Exception e) {
                        logger.error("Error processing memory for agent {} userId {}", agent.getId(),userId, e);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error fetching agent ids for memory processing", e);
        }
    }

    private String getConfig(String key, String defaultValue) {
        var config = sysConfigService.getByKey(key);
        return config != null ? config.getConfigValue() : defaultValue;
    }

    private void processMemoryForIdentity(Agent agent,String userId) {

        //已标记清理的消息
        List<ChatMemory> cleanedMessages = chatMemoryMapper.findByAgentIdAndUserIdAndStatus(agent.getId(), userId,1);
        List<ChatMemory> cleanedMessages1 = chatMemoryMapper.findByAgentIdAndUserIdAndStatus(agent.getId(), userId,2);
        if(!cleanedMessages1.isEmpty()){
            cleanedMessages.addAll(cleanedMessages1);
            cleanedMessages=cleanedMessages.stream().sorted(Comparator.comparing(ChatMemory::getCreateTime)).collect(Collectors.toList());
        }
        if (cleanedMessages.isEmpty()){
            return;
        }
        int batchSize = agent.getMaxMemoryRecords()/2;

        if(batchSize<=0){
            batchSize=5;
        }
        LocalDateTime latestTime = cleanedMessages.stream()
                .map(ChatMemory::getCreateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        if (cleanedMessages.size() < batchSize && latestTime.isAfter(fiveMinutesAgo)) {
            return;
        }

        StringBuilder conversationBuilder = new StringBuilder();
        for (ChatMemory chat : cleanedMessages) {
            if (chat == null || chat.getMessageJson() == null) {
                continue;
            }
            
            ChatMessage message = ChatMessageDeserializer.messageFromJson(chat.getMessageJson());
            if (message == null) {
                continue;
            }
            
            if (message instanceof dev.langchain4j.data.message.UserMessage) {
                dev.langchain4j.data.message.UserMessage userMessage = ((dev.langchain4j.data.message.UserMessage) message);
                String userText = userMessage.singleText();
                if (userText != null) {
                    conversationBuilder.append("User:").append(userText);
                } else {
                    conversationBuilder.append("User:(no text)");
                }
            } else if (message instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) message;
                if (aiMessage.thinking() != null) {
                    conversationBuilder.append("Ai thinking:").append(aiMessage.thinking()).append("\n");
                }
                if (aiMessage.text() != null) {
                    conversationBuilder.append("Ai:").append(aiMessage.text()).append("\n");
                }
                if (aiMessage.toolExecutionRequests() != null && !aiMessage.toolExecutionRequests().isEmpty()) {
                    logger.info("Ai tool call:", JSON.toJSONString(aiMessage.toolExecutionRequests().stream().map(x -> "id:" + x.id() + ",name:" + x.name() + ",arguments:" + x.arguments()).collect(Collectors.toList())));
                }
            } else if (message instanceof SystemMessage) {
                SystemMessage systemMessage = (SystemMessage) message;
                String systemText = systemMessage.text();
                if (systemText != null) {
                    conversationBuilder.append("System:").append(systemText).append("\n");
                } else {
                    conversationBuilder.append("System:(no text)").append("\n");
                }
            } else if (message instanceof ToolExecutionResultMessage) {
                ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
                String toolName = toolExecutionResultMessage.toolName();
                String toolId = toolExecutionResultMessage.id();
                String toolText = toolExecutionResultMessage.text();
                
                conversationBuilder.append("Ai tool call:");
                if (toolName != null) {
                    conversationBuilder.append("toolName:").append(toolName);
                } else {
                    conversationBuilder.append("toolName:(null)");
                }
                conversationBuilder.append(",id:");
                if (toolId != null) {
                    conversationBuilder.append(toolId);
                } else {
                    conversationBuilder.append("(null)");
                }
                conversationBuilder.append(",text:");
                if (toolText != null) {
                    conversationBuilder.append(toolText);
                } else {
                    conversationBuilder.append("(null)");
                }
                conversationBuilder.append("\n");
            } else {
                conversationBuilder.append("Other: ").append(message.getClass().getSimpleName()).append(": ").append(message).append("\n");
            }
            conversationBuilder.append("\n");
        }

        String agentIdStr = String.valueOf(agent.getId());
        String newConversation = conversationBuilder.toString();
        String existingMemory = longTermMemoryService.getMemoryTree(agentIdStr,userId);

        String memory = buildMemorySummary(existingMemory, newConversation);
        boolean handle = handle(agentIdStr,userId, memory);
        if (handle) {
            chatMemoryMapper.deleteByIds(cleanedMessages.stream().map(ChatMemory::getId).toList());
        }
        logger.info("Processing memory for agentId: {}, cleaned messages count: {}", agentIdStr, cleanedMessages.size());
    }

    private boolean handle(String agentId,String userId, String content) {
        try {
            String modelIdStr = getConfig("memory_ai_model_id", "");
            Long modelId = null;
            if (!modelIdStr.isBlank()) {
                try {
                    modelId = Long.parseLong(modelIdStr);
                } catch (NumberFormatException ignored) {}
            }

            ChatModel chatModel = aiModelService.createChatModel(modelId, true,new HashMap<>(1){{
                put("source","memory-task");
                put("agentId",agentId);
                put("userId",userId);
            }});

            String systemMessage = buildSystemMessage(agentId);
            InvocationParameters invocationParameters=new InvocationParameters();
            InvocationParametersUtil.setAgentId(invocationParameters,agentId);
            InvocationParametersUtil.setUserId(invocationParameters,userId);

            MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> systemMessage)
                    .tools(Arrays.asList(longTermMemoryService))
                    .build();
            logger.info("开始汇总记忆 \n {}", content);
            String result = assistant.chat(content,invocationParameters);
            logger.info("记忆汇总完毕：{}", result);
            return true;
        }catch (Exception ex){
            logger.error("Error processing memory for agentId {}", agentId, ex);
            return false;
        }
    }

    @Override
    public String getType() {
        return "longTermMemory";
    }

    @Override
    public void execute(ScheduledTask task) {
        logger.info("定时记忆整理任务执行 [{}]", task.getId());
        try {
            processAgentMemories();
        } catch (Exception e) {
            logger.error("记忆整理任务执行失败", e);
        }
    }

    private String buildSystemMessage(String agentId) {
        String customPrompt = getConfig("memory_prompt", "");
        if (customPrompt.isBlank()) {
            customPrompt="你是一个记忆整理助手。善于根据聊天记录提取关键的用户记忆信息。" +
                    "请根据内容总结出用户的关键记忆信息，并按以下格式进行分类，分类不够可以自己添加，但是分类要精简：\n" +
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
                    "请认真总结记忆得到清单后进行检查，不要有重复的记忆或分类,记忆内容不能胡编乱造信息，要完全从内容中来。\n" +
                    "在完成记忆总结后，你可以调用保存智能体记忆工具。\n" +
                    "归类后先保存分类作为父级记忆得到编号，再保存概要内容作为子级记忆，子级记忆的parentId是父级记忆的编号。" ;
        }
        return customPrompt+ "\n本次记忆的agentId是" + agentId;
    }

    public interface MemoryAssistant {
        @UserMessage("{{content}}")
        String chat(String content, InvocationParameters invocationParameters);
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
