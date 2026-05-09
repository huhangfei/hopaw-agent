package com.agent.hopaw.task;

import com.agent.hopaw.constant.AiModelCallSourceEnum;
import com.agent.hopaw.constant.DefaultUser;
import com.agent.hopaw.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ChatMemory;
import com.agent.hopaw.model.LongTermMemory;
import com.agent.hopaw.model.ScheduledTask;
import com.agent.hopaw.service.*;
import com.agent.hopaw.util.InvocationParametersWrapper;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.*;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final TokenUsageService tokenUsageService;
    public LongTermMemoryTaskHandler(AiModelService aiModelService,
                                     LongTermMemoryService longTermMemoryService,
                                     ChatMemoryMapper chatMemoryMapper, AgentMapper agentMapper,
                                     SysConfigService sysConfigService, TokenUsageService tokenUsageService) {
        this.aiModelService = aiModelService;
        this.longTermMemoryService = longTermMemoryService;
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentMapper = agentMapper;
        this.sysConfigService = sysConfigService;
        this.tokenUsageService = tokenUsageService;
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
        return sysConfigService.getValueByKey(key, defaultValue);
    }

    private String buildMessageSummary(List<ChatMemory> cleanedMessages ) {
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

                for (Content content : userMessage.contents()) {
                    if(content instanceof TextContent){
                        conversationBuilder.append("User:").append("\n").append(((TextContent)content).text()).append("\n");
                    }else  if(content instanceof ImageContent){
                        conversationBuilder.append("User:").append("\n").append("[Image]").append("\n");
                    }else  if(content instanceof VideoContent){
                        conversationBuilder.append("User:").append("\n").append("[Video]").append("\n");
                    }else  if(content instanceof AudioContent){
                        conversationBuilder.append("User:").append("\n").append("[Audio]").append("\n");
                    }else  if(content instanceof PdfFileContent){
                        conversationBuilder.append("User:").append("\n").append("[PdfFile]").append("\n");
                    }
                }
            } else if (message instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) message;
                if (aiMessage.thinking() != null) {
                    conversationBuilder.append("Ai thinking:").append("\n").append(aiMessage.thinking()).append("\n");
                }
                if (aiMessage.text() != null) {
                    conversationBuilder.append("Ai:").append("\n").append(aiMessage.text()).append("\n");
                }
                if (aiMessage.toolExecutionRequests() != null && !aiMessage.toolExecutionRequests().isEmpty()) {
                    logger.info("Ai tool call:", JSON.toJSONString(aiMessage.toolExecutionRequests().stream().map(x -> "id:" + x.id() + ",name:" + x.name() + ",arguments:" + x.arguments()).collect(Collectors.toList())));
                }
            } else if (message instanceof SystemMessage) {
                SystemMessage systemMessage = (SystemMessage) message;
                String systemText = systemMessage.text();
                if (systemText != null) {
                    conversationBuilder.append("System:").append("\n").append(systemText).append("\n");
                } else {
                    conversationBuilder.append("System: (no text)").append("\n");
                }
            } else if (message instanceof ToolExecutionResultMessage) {
                ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) message;
                String toolName = toolExecutionResultMessage.toolName();
                String toolId = toolExecutionResultMessage.id();
                String toolText = toolExecutionResultMessage.text();
                Boolean error = toolExecutionResultMessage.isError();

                conversationBuilder.append("Ai tool call:").append("\n");
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
                conversationBuilder.append(",state:");
                if (error != null && error) {
                    conversationBuilder.append("error");
                }else{
                    conversationBuilder.append("success");
                }
                //工具的结果不进行记忆整理
//                conversationBuilder.append(",text:");
//                if (toolText != null) {
//                    conversationBuilder.append(toolText);
//                } else {
//                    conversationBuilder.append("(null)");
//                }
                conversationBuilder.append("\n");
            } else {
                conversationBuilder.append("Other: ").append("\n").append(message.getClass().getSimpleName()).append(": ").append(message).append("\n");
            }
            conversationBuilder.append("\n");
        }
        return conversationBuilder.toString();
    }

    private void processMemoryForIdentity(Agent agent,String userId) {
        //已标记清理的消息
        List<ChatMemory> cleanedMessages = chatMemoryMapper.findByAgentIdAndUserIdInStatus(agent.getId(), userId,Arrays.asList(1,2));
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
        //这是新消息
        String newConversation = buildMessageSummary(cleanedMessages);
        String agentIdStr = String.valueOf(agent.getId());

        //现有记忆
        List<LongTermMemory> longTermMemories = longTermMemoryService.getRecentMemoriesByAgentIdAndUserId(agentIdStr, userId);
        String content = buildContent(longTermMemories, newConversation);

        boolean handle = handle(agentIdStr,userId, content);
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
            LangChain4jMonitor langChain4jMonitor = new LangChain4jMonitor(AiModelCallSourceEnum.MEMORYORGANIZE).setAgentId(Long.valueOf(agentId)).setUserId(userId).setTokenUsageService(tokenUsageService);

            ChatModel chatModel = aiModelService.createChatModel(modelId, true,langChain4jMonitor);

            String systemMessage = buildSystemMessage(agentId);
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create();
            invocationParametersWrapper.setAgentId(agentId);
            invocationParametersWrapper.setUserId(userId);

            MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> systemMessage)
                    .tools(Arrays.asList(longTermMemoryService))
                    .build();
            logger.info("开始汇总记忆 \n {}", content);
            ChatRequestParameters chatRequestParameters=ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.chat(content,chatRequestParameters,invocationParametersWrapper.getParameters());
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
                    "请根据现有记忆和新会话总结出用户的关键记忆信息，要严格按以下要求进行分类整理：\n" +
                    "========\n" +
                    "分类1，用户画像\n" +
                    "内容包含：姓名、昵称、年龄、地域、职业、收入、常用设备、喜好、交流风格、偏好与厌恶、经常提的要求规则等，只记录简短的用户各种标签。\n" +
                    "整理限制: 请给出一段段简短的用户画像描述作为概要，其他相关内容作为画像内容；用户画像只有汇总出一条完整的画像不需要分割；\n" +
                    "分类2，任务记录\n" +
                    "内容包含：正在做的什么事情（开始时间、任务说明、任务过程主要节点、结果、结束时间）\n" +
                    "整理限制: 每次可以汇总出一条或多条不同任务记录，要根据具体的对话场景和已有的任务记录做判断，那些是旧任务的延续，哪些是新任务的开始；旧任务就更新内容新任务就新增内容；" +
                    "每条任务都要汇总出一段简短的任务概要；内容要抓住终点，涵盖完整任务内容但不要啰嗦\n" +
                    "========\n" +
                    "请认真总结记忆得到清单后进行检查，不要有重复的记忆,记忆内容不能胡编乱造信息，要完全从内容中来，冲突的记忆以最新的为准。\n" +
                    "在完成记忆总结后，你可以调用记忆操作相关工具，其他未列出的工具都不能用。\n"  ;
        }
        return customPrompt;
    }

    public interface MemoryAssistant {
        @UserMessage("{{content}}")
        String chat(@V("content") String content,
                    ChatRequestParameters chatRequestParameters,
                    InvocationParameters invocationParameters);
    }

    /**
     * @param longTermMemories
     * @param newConversation
     * @return
     */
    private String buildContent(List<LongTermMemory> longTermMemories, String newConversation) {
        StringBuilder memory = new StringBuilder();
        memory.append("以下是需要分析的内容\n");
        memory.append("===========================\n");
        if(!longTermMemories.isEmpty()){
            memory.append("以下是现有记忆内容:\n");
            String memoryContent = longTermMemoryService.buildMemoryContent(longTermMemories);
            memory.append(memoryContent).append("\n");
        }
        memory.append("【新对话】\n").append(newConversation).append("\n\n");
        memory.append("===========================");
        return memory.toString();
    }
}
