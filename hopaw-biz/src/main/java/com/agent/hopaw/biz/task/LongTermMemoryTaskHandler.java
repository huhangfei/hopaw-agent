package com.agent.hopaw.biz.task;

import com.agent.hopaw.biz.tool.memory.MemoryTool;
import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.infra.constant.VectorMemoryTypeEnum;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.memory.IVectorMemoryService;
import com.agent.hopaw.infra.model.entity.ChatMemory;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.service.*;
import com.agent.hopaw.infra.task.TaskHandler;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.alibaba.fastjson2.JSON;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class LongTermMemoryTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryTaskHandler.class);

    private final IAiModelService aiModelService;
    private final ILongTermMemoryService longTermMemoryService;
    private final IVectorMemoryService vectorMemoryService;
    private final MemoryTool memoryTool;
    private final IChatMemoryService chatMemoryService;
    private final ISysConfigService sysConfigService;
    private final ITokenUsageService tokenUsageService;
    
    // 运行中标记，防止并发执行
    private volatile boolean running = false;

    public LongTermMemoryTaskHandler(IAiModelService aiModelService,
                                     ILongTermMemoryService longTermMemoryService,
                                     IVectorMemoryService vectorMemoryService,
                                     MemoryTool memoryTool,
                                     IChatMemoryService chatMemoryService,
                                     ISysConfigService sysConfigService,
                                     ITokenUsageService tokenUsageService) {
        this.aiModelService = aiModelService;
        this.longTermMemoryService = longTermMemoryService;
        this.vectorMemoryService = vectorMemoryService;
        this.memoryTool = memoryTool;
        this.chatMemoryService = chatMemoryService;
        this.sysConfigService = sysConfigService;
        this.tokenUsageService = tokenUsageService;
    }

    private void saveToVectorMemory() {
        List<LongTermMemory> longTermMemories = longTermMemoryService.findByStatus(0);
        for (LongTermMemory longTermMemory : longTermMemories) {
            String memory = "id:" + longTermMemory.getId()+"\nsummary:" + longTermMemory.getSummary() + "\nmemory:" + longTermMemory.getMemory();
            String storeId = vectorMemoryService.store(memory, longTermMemory.getSessionId(), null, longTermMemory.getUserId(), VectorMemoryTypeEnum.fromCode(longTermMemory.getMemoryType()), longTermMemory.getCreateTime());
            longTermMemory.setEmbeddingId(storeId);
            longTermMemory.setStatus(1);
            longTermMemoryService.update(longTermMemory);
        }
    }

    public void processAgentMemories() {
        try {
            saveToVectorMemory();

            List<ChatMemory> pairs = chatMemoryService.findDistinctSessionUserPairs();
            for (ChatMemory pair : pairs) {
                try {
                    processMemoryForIdentity(pair.getSessionId(), pair.getUserId());
                } catch (Exception e) {
                    logger.error("Error processing memory for agentId {} userId {}", pair.getAgentId(), pair.getUserId(), e);
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

    private void processMemoryForIdentity(String sessionId, String userId) {
        //已标记清理的消息
        List<ChatMemory> cleanedMessages = chatMemoryService.findBySessionIdAndStatus(sessionId, Arrays.asList(2, 3));
        if (cleanedMessages.isEmpty()) {
            return;
        }
        String memoryMaxBatchSize = getConfig("memory_max_batch_size", "10");
        int batchSize = Integer.parseInt(memoryMaxBatchSize);
        //获取时间配置
        String memoryTimeConfig = getConfig("memory_time_config", "5");
        int timeConfig = Integer.parseInt(memoryTimeConfig);
        LocalDateTime latestTime = cleanedMessages.stream()
                .map(ChatMemory::getCreateTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        LocalDateTime timeAgo = LocalDateTime.now().minusMinutes(timeConfig);
        if (cleanedMessages.size() < batchSize && latestTime.isAfter(timeAgo)) {
            return;
        }
        //这是新消息
        String newConversation = buildMessageSummary(cleanedMessages);
        //现有记忆
        List<LongTermMemory> longTermMemories = longTermMemoryService.queryUserAllMemories(sessionId, userId);

        //扩展知识往往较大，不输入详情
        String longTermMemoryContent = longTermMemoryService.buildMemoryContent(longTermMemories, longTermMemory -> {
            if (LongTermMemoryTypeEnum.USER_PROFILE.getCode().equals(longTermMemory.getMemoryType()) || LongTermMemoryTypeEnum.TASK_RECORDS.getCode().equals(longTermMemory.getMemoryType())) {
                return true;
            }
            return false;
        });

        String content = "以下是需要分析的新会话内容\n";
        content += newConversation;
        content +=("\n===========================");

        boolean handle = handle(sessionId, userId, longTermMemoryContent,content);
        if (handle) {
            storeChatHistoryToVector(cleanedMessages);
            chatMemoryService.deleteByIds(cleanedMessages.stream().map(ChatMemory::getId).toList());
        }
        longTermMemoryService.deleteExpiredTaskRecordsMemories(sessionId, userId);

        logger.info("Processing memory for sessionId: {}, cleaned messages count: {}", sessionId, cleanedMessages.size());
    }

    private boolean handle(String sessionId, String userId,String longTermMemoriesContent, String content) {
        try {
            String modelIdStr = getConfig("memory_ai_model_id", "");
            Long modelId = null;
            if (!modelIdStr.isBlank()) {
                try {
                    modelId = Long.parseLong(modelIdStr);
                } catch (NumberFormatException ignored) {
                }
            }
            com.agent.hopaw.infra.monitor.LangChain4jMonitor langChain4jMonitor = new com.agent.hopaw.infra.monitor.LangChain4jMonitor(AiModelCallSourceEnum.MEMORYORGANIZE)
                    .setSessionId(sessionId)
                    .setUserId(userId)
                    .setTokenUsageService(tokenUsageService);

            ChatModel chatModel = aiModelService.createChatModel(modelId, true, langChain4jMonitor);

            String systemMessage = buildSystemMessage();
            if (!StringUtils.hasLength(systemMessage)) {
                logger.warn("缺失记忆整理提示词，无法进行记忆整理，请先设置提示词。");
                return false;
            }
            systemMessage+= "\n========现有记忆========\n" + longTermMemoriesContent;
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
                    .setSessionId(sessionId)
                    .setUserId(userId)
                    .setRequestId(UUID.randomUUID().toString());

            String finalSystemMessage = systemMessage;

            MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> finalSystemMessage)
                    .tools(Arrays.asList(memoryTool))
                    .build();
            logger.info("开始汇总记忆 \n {}", content);
            ChatRequestParameters chatRequestParameters = ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.chat(content, chatRequestParameters, invocationParametersWrapper.getParameters());
            logger.info("记忆汇总完毕：{}", result);
            return true;
        } catch (Exception ex) {
            logger.error("Error processing memory for sessionId {}", sessionId, ex);
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
     * 将被清理的聊天历史记录写入向量库
     */
    private void storeChatHistoryToVector(List<ChatMemory> cleanedMessages) {
        if (cleanedMessages == null || cleanedMessages.isEmpty()) {
            return;
        }
        try {
            for (ChatMemory chat : cleanedMessages) {
                if (chat == null || chat.getMessageJson() == null) {
                    continue;
                }
                ChatMessage message = ChatMessageDeserializer.messageFromJson(chat.getMessageJson());
                if (message == null) {
                    continue;
                }
                String role = "";
                String text = "";
                if (message instanceof dev.langchain4j.data.message.UserMessage) {
                    role = "User";
                    text = extractTextContent(message);
                } else if (message instanceof AiMessage) {
                    role = "Ai";
                    text = ((AiMessage) message).text();
                } else if (message instanceof SystemMessage) {
                    role = "System";
                    text = ((SystemMessage) message).text();
                } else if (message instanceof ToolExecutionResultMessage) {
                    role = "Tool";
                    text = ((ToolExecutionResultMessage) message).text();
                } else {
                    continue;
                }

                if (text == null || text.isBlank()) {
                    continue;
                }

                String label = String.format("[%s] %s", role, text);
                vectorMemoryService.store(label,chat.getSessionId(), chat.getAgentId(), chat.getUserId(), VectorMemoryTypeEnum.CHAT_HISTORY, chat.getCreateTime());
                logger.info("Stored chat history messages to vector store, sessionId={}, agentId={}, userId={}", chat.getSessionId(), chat.getAgentId(), chat.getUserId());
            }
        } catch (Exception e) {
            logger.error("Failed to store chat history to vector store, msg{}", JSON.toJSONString(cleanedMessages), e);
        }
    }

    private String extractTextContent(ChatMessage message) {
        if (!(message instanceof dev.langchain4j.data.message.UserMessage)) {
            return "";
        }
        dev.langchain4j.data.message.UserMessage userMessage = (dev.langchain4j.data.message.UserMessage) message;
        StringBuilder sb = new StringBuilder();
        for (Content content : userMessage.contents()) {
            if (content instanceof TextContent) {
                sb.append(((TextContent) content).text());
            }
        }
        return sb.toString();
    }
}
