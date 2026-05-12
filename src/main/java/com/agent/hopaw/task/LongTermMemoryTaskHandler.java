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
import com.agent.hopaw.tools.MemoryTool;
import com.agent.hopaw.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class LongTermMemoryTaskHandler implements TaskHandler {
    private final AiModelService aiModelService;
    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryTaskHandler.class);

    private final com.agent.hopaw.service.LongTermMemoryService longTermMemoryService;
    private final ChatMemoryMapper chatMemoryMapper;
    private final AgentMapper agentMapper;
    private final SysConfigService sysConfigService;
    private final TokenUsageService tokenUsageService;
    
    // 运行中标记，防止并发执行
    private volatile boolean running = false;

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

    public void processAgentMemories() {
        try {
            List<Agent> allAgents = agentMapper.findAll();
            for (String userId : Arrays.asList(DefaultUser.USER)) {
                for (Agent agent : allAgents) {
                    try {
                        processMemoryForIdentity(agent, userId);
                    } catch (Exception e) {
                        logger.error("Error processing memory for agent {} userId {}", agent.getId(), userId, e);
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

    private String buildMessageSummary(List<ChatMemory> cleanedMessages) {
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
                    if (content instanceof TextContent) {
                        conversationBuilder.append("User:").append("\n").append(((TextContent) content).text()).append("\n");
                    } else if (content instanceof ImageContent) {
                        conversationBuilder.append("User:").append("\n").append("[Image]").append("\n");
                    } else if (content instanceof VideoContent) {
                        conversationBuilder.append("User:").append("\n").append("[Video]").append("\n");
                    } else if (content instanceof AudioContent) {
                        conversationBuilder.append("User:").append("\n").append("[Audio]").append("\n");
                    } else if (content instanceof PdfFileContent) {
                        conversationBuilder.append("User:").append("\n").append("[PdfFile]").append("\n");
                    }
                }
            } else if (message instanceof AiMessage) {
                AiMessage aiMessage = (AiMessage) message;
                if (aiMessage.thinking() != null) {
                    //conversationBuilder.append("Ai thinking:").append("\n").append(aiMessage.thinking()).append("\n");
                }
                if (aiMessage.text() != null) {
                    conversationBuilder.append("Ai:").append("\n").append(aiMessage.text()).append("\n");
                }
                if (aiMessage.toolExecutionRequests() != null && !aiMessage.toolExecutionRequests().isEmpty()) {
                    ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
                    conversationBuilder.append("Ai tool call:").append("\n")
                            .append("id:").append(toolExecutionRequest.id()).append("\n")
                            .append("name:").append(toolExecutionRequest.name()).append("\n");
                    if (toolExecutionRequest.arguments() != null) {
                        String arguments = toolExecutionRequest.arguments();
                        //参数作用不大，如果长度大于100进行截取
                        if (arguments.length() > 100) {
                            arguments = arguments.substring(0, 100);
                        }
                        conversationBuilder
                                .append("arguments:")
                                .append(arguments).append("\n");
                    }
                    conversationBuilder.append("\n");
                }
            } else if (message instanceof SystemMessage) {
//                SystemMessage systemMessage = (SystemMessage) message;
//                String systemText = systemMessage.text();
//                if (systemText != null) {
//                    conversationBuilder.append("System:").append("\n").append(systemText).append("\n");
//                } else {
//                    conversationBuilder.append("System: (no text)").append("\n");
//                }
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
                } else {
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

    private void processMemoryForIdentity(Agent agent, String userId) {
        //已标记清理的消息
        List<ChatMemory> cleanedMessages = chatMemoryMapper.findByAgentIdAndUserIdInStatus(agent.getId(), userId, Arrays.asList(1, 2));
        if (cleanedMessages.isEmpty()) {
            return;
        }
        int batchSize = agent.getMaxMemoryRecords() / 2;
        if (batchSize <= 0) {
            batchSize = 5;
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
        List<LongTermMemory> longTermMemories = longTermMemoryService.queryUserAllMemories(agent.getId(), userId);

        //扩展知识往往较大，不输入详情
        String longTermMemoryContent = longTermMemoryService.buildMemoryContent(longTermMemories, longTermMemory -> {
            if (LongTermMemoryTypeEnum.USER_PROFILE.getCode().equals(longTermMemory.getMemoryType()) || LongTermMemoryTypeEnum.TASK_RECORDS.getCode().equals(longTermMemory.getMemoryType())) {
                return true;
            }
            return false;
        });

        String content = buildContent(longTermMemoryContent, newConversation);

        boolean handle = handle(agent.getId(), userId, content);
        if (handle) {
            chatMemoryMapper.deleteByIds(cleanedMessages.stream().map(ChatMemory::getId).toList());
        }

        //todo: 几天前的任务记录移入向量库

        //移除几天前的任务记录
        longTermMemoryService.deleteExpiredTaskRecordsMemories(agent.getId(), userId);

        logger.info("Processing memory for agentId: {}, cleaned messages count: {}", agentIdStr, cleanedMessages.size());
    }

    private boolean handle(Long agentId, String userId, String content) {
        try {
            String modelIdStr = getConfig("memory_ai_model_id", "");
            Long modelId = null;
            if (!modelIdStr.isBlank()) {
                try {
                    modelId = Long.parseLong(modelIdStr);
                } catch (NumberFormatException ignored) {
                }
            }
            LangChain4jMonitor langChain4jMonitor = new LangChain4jMonitor(AiModelCallSourceEnum.MEMORYORGANIZE).setAgentId(agentId).setUserId(userId).setTokenUsageService(tokenUsageService);

            ChatModel chatModel = aiModelService.createChatModel(modelId, true, langChain4jMonitor);

            String systemMessage = buildSystemMessage();
            if (!StringUtils.hasLength(systemMessage)) {
                logger.warn("缺失记忆整理提示词，无法进行记忆整理，请先设置提示词。");
                return false;
            }
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create();
            invocationParametersWrapper.setAgentId(agentId);
            invocationParametersWrapper.setUserId(userId);

            MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> systemMessage)
                    .tools(Arrays.asList(new MemoryTool(longTermMemoryService)))
                    .build();
            logger.info("开始汇总记忆 \n {}", content);
            ChatRequestParameters chatRequestParameters = ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.chat(content, chatRequestParameters, invocationParametersWrapper.getParameters());
            logger.info("记忆汇总完毕：{}", result);
            return true;
        } catch (Exception ex) {
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
        
        // 检查是否已经在运行
        if (running) {
            logger.warn("记忆整理任务正在运行中，跳过本次执行 [{}]", task.getId());
            return;
        }
        
        // 标记为运行中
        running = true;
        try {
            processAgentMemories();
        } catch (Exception e) {
            logger.error("记忆整理任务执行失败", e);
        } finally {
            // 任务结束后恢复标记
            running = false;
            logger.info("记忆整理任务执行完成 [{}]", task.getId());
        }
    }

    private String buildSystemMessage() {
        String customPrompt = longTermMemoryService.getMemoryOrganizingRules();
        return customPrompt;
    }

    public interface MemoryAssistant {
        @UserMessage("{{content}}")
        String chat(@V("content") String content,
                    ChatRequestParameters chatRequestParameters,
                    InvocationParameters invocationParameters);
    }

    /**
     * @param longTermMemoriesContent
     * @param newConversation
     * @return
     */
    private String buildContent(String longTermMemoriesContent, String newConversation) {
        StringBuilder memory = new StringBuilder();
        memory.append("以下是需要分析的内容\n");
        memory.append("===========================\n");
        if (longTermMemoriesContent != null && !longTermMemoriesContent.isEmpty()) {
            memory.append("【以下是现有记忆内容】\n");
            memory.append(longTermMemoriesContent).append("\n");
        }
        memory.append("【以下是新对话】\n").append(newConversation).append("\n\n");
        memory.append("===========================");
        return memory.toString();
    }
}
