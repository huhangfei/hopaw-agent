package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.constant.ChatMemoryStatusEnum;
import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.mapper.ChatMemoryMapper;
import com.agent.hopaw.infra.mapper.ChatMemoryObsoleteMapper;
import com.agent.hopaw.infra.mapper.ChatMemoryProcessedCursorMapper;
import com.agent.hopaw.infra.model.entity.ChatMemory;
import com.agent.hopaw.infra.model.entity.ChatMemoryProcessedCursor;
import com.agent.hopaw.infra.model.entity.LongTermMemory;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.monitor.LangChain4jChatModelListener;
import com.agent.hopaw.infra.service.IAiModelService;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.task.TaskHandler;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
public class LongTermMemoryTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(LongTermMemoryTaskHandler.class);

    private final IAiModelService aiModelService;
    private final ILongTermMemoryService longTermMemoryService;
    private final ISysConfigService sysConfigService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatMemoryObsoleteMapper chatMemoryObsoleteMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final ChatMemoryProcessedCursorMapper processedCursorMapper;

    // 运行中标记，防止并发执行
    private volatile boolean running = false;

    public LongTermMemoryTaskHandler(IAiModelService aiModelService,
                                     ILongTermMemoryService longTermMemoryService,
                                     ISysConfigService sysConfigService,
                                     ApplicationEventPublisher eventPublisher,
                                     ChatMemoryObsoleteMapper chatMemoryObsoleteMapper,
                                     ChatMemoryMapper chatMemoryMapper,
                                     ChatMemoryProcessedCursorMapper processedCursorMapper) {
        this.aiModelService = aiModelService;
        this.longTermMemoryService = longTermMemoryService;
        this.sysConfigService = sysConfigService;
        this.eventPublisher = eventPublisher;
        this.chatMemoryObsoleteMapper = chatMemoryObsoleteMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.processedCursorMapper = processedCursorMapper;
    }
    public void processAgentMemories() {
        try {
            // 同时从 chat_memory_obsolete 与 chat_memory(status=TASK_DONE) 中发现需要整理的会话
            // 去重后再逐个处理
            List<SessionUserPair> pairs = collectSessionUserPairs();
            for (SessionUserPair pair : pairs) {
                try {
                    processMemoryForIdentity(pair.getSessionId(), pair.getUserId());
                } catch (Exception e) {
                    logger.error("Error processing memory for sessionId {} userId {}",
                            pair.getSessionId(), pair.getUserId(), e);
                }
            }

        } catch (Exception e) {
            logger.error("Error fetching agent ids for memory processing", e);
        }
    }

    /**
     * 汇总需要整理记忆的 (sessionId, userId) 组合：
     * 1) chat_memory_obsolete 中尚有未处理数据
     * 2) chat_memory 中 status = TASK_DONE 的数据（未及时迁移的）
     * 去重后返回。
     */
    private List<SessionUserPair> collectSessionUserPairs() {
        Set<String> pairKeys = new HashSet<>();
        List<SessionUserPair> result = new ArrayList<>();

        // 来自 chat_memory_obsolete
        try {
            for (ChatMemory pair : chatMemoryObsoleteMapper.findObsoleteDistinctSessionUserPairs()) {
                if (pair == null || pair.getSessionId() == null || pair.getUserId() == null) {
                    continue;
                }
                String key = pair.getSessionId() + "::" + pair.getUserId();
                if (pairKeys.add(key)) {
                    result.add(new SessionUserPair(pair.getSessionId(), pair.getUserId()));
                }
            }
        } catch (Exception e) {
            logger.error("查询 chat_memory_obsolete 会话组合失败", e);
        }

        // 来自 chat_memory(status=TASK_DONE)
        try {
            List<ChatMemory> taskDonePairs = chatMemoryMapper.findTaskDoneDistinctSessionUserPairs(
                    ChatMemoryStatusEnum.TASK_DONE.getCode());
            for (ChatMemory pair : taskDonePairs) {
                if (pair == null || pair.getSessionId() == null || pair.getUserId() == null) {
                    continue;
                }
                String key = pair.getSessionId() + "::" + pair.getUserId();
                if (pairKeys.add(key)) {
                    result.add(new SessionUserPair(pair.getSessionId(), pair.getUserId()));
                }
            }
        } catch (Exception e) {
            logger.error("查询 chat_memory(TASK_DONE) 会话组合失败", e);
        }

        return result;
    }

    /**
     * 会话 / 用户 组合的轻量 DTO，避免每次返回完整的 ChatMemory 实体。
     */
    private static class SessionUserPair {
        private final String sessionId;
        private final String userId;

        SessionUserPair(String sessionId, String userId) {
            this.sessionId = sessionId;
            this.userId = userId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getUserId() {
            return userId;
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
        // 1) 读取当前 (session, user) 的整理进度游标
        ChatMemoryProcessedCursor cursor = processedCursorMapper.findBySessionIdAndUserId(sessionId, userId);
        long lastChatMemoryId = cursor == null || cursor.getLastChatMemoryId() == null ? 0L : cursor.getLastChatMemoryId();
        long lastObsoleteMemoryId = cursor == null || cursor.getLastObsoleteMemoryId() == null ? 0L : cursor.getLastObsoleteMemoryId();

        // 2) 增量查询 chat_memory_obsolete 中未处理的数据
        List<ChatMemory> obsoleteIncremental = chatMemoryObsoleteMapper.findObsoleteChatMemoryBySessionIdAndUserIdAfterId(
                sessionId, userId, lastObsoleteMemoryId);
        if (obsoleteIncremental == null) {
            obsoleteIncremental = Collections.emptyList();
        }

        // 3) 增量查询 chat_memory 中 status = TASK_DONE 的数据
        List<Integer> taskDoneStatus = Collections.singletonList(ChatMemoryStatusEnum.TASK_DONE.getCode());
        List<ChatMemory> taskDoneIncremental = chatMemoryMapper.findTaskDoneChatMemoryBySessionIdAndUserIdAfterId(
                sessionId, userId, taskDoneStatus, lastChatMemoryId);
        if (taskDoneIncremental == null) {
            taskDoneIncremental = Collections.emptyList();
        }

        // 拼接后排序：先按 create_time 升序，再按 id 升序，保证上下文连续
        List<ChatMemory> cleanedMessages = new ArrayList<>(obsoleteIncremental.size() + taskDoneIncremental.size());
        cleanedMessages.addAll(obsoleteIncremental);
        cleanedMessages.addAll(taskDoneIncremental);
        cleanedMessages.sort((a, b) -> {
            LocalDateTime ta = a.getCreateTime();
            LocalDateTime tb = b.getCreateTime();
            if (ta != null && tb != null && !ta.equals(tb)) {
                return ta.compareTo(tb);
            }
            Long ia = a.getId();
            Long ib = b.getId();
            if (ia == null && ib == null) return 0;
            if (ia == null) return -1;
            if (ib == null) return 1;
            return ia.compareTo(ib);
        });

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
            if (UserMemoryTypeEnum.USER_PROFILE.getCode().equals(longTermMemory.getMemoryType()) || UserMemoryTypeEnum.TASK_RECORDS.getCode().equals(longTermMemory.getMemoryType())) {
                return true;
            }
            return false;
        });

        String content = "以下是需要分析的新会话内容\n";
        content += newConversation;
        content +=("\n===========================");

        boolean handle = handle(sessionId, userId, longTermMemoryContent,content);
        if (handle) {
            // 2.1) 推进 chat_memory 游标
            long newLastChatMemoryId = lastChatMemoryId;
            for (ChatMemory m : taskDoneIncremental) {
                if (m != null && m.getId() != null && m.getId() > newLastChatMemoryId) {
                    newLastChatMemoryId = m.getId();
                }
            }

            // 2.2) 仅清理本次读取过的 chat_memory_obsolete 行（id <= 本次处理最大 id）
            if (!obsoleteIncremental.isEmpty()) {
                long maxObsoleteId = lastObsoleteMemoryId;
                for (ChatMemory m : obsoleteIncremental) {
                    if (m != null && m.getId() != null && m.getId() > maxObsoleteId) {
                        maxObsoleteId = m.getId();
                    }
                }
                if (maxObsoleteId > lastObsoleteMemoryId) {
                    chatMemoryObsoleteMapper.deleteObsoleteChatMemoryBySessionIdUserIdUpToId(
                            sessionId, userId, maxObsoleteId);
                }
            }

            // 2.3) 持久化最新游标
            ChatMemoryProcessedCursor newCursor = new ChatMemoryProcessedCursor(
                    sessionId, userId, newLastChatMemoryId,
                    cursor == null ? Math.max(lastObsoleteMemoryId, computeMaxId(obsoleteIncremental))
                                   : Math.max(cursor.getLastObsoleteMemoryId() == null ? 0L : cursor.getLastObsoleteMemoryId(),
                                              computeMaxId(obsoleteIncremental)));
            processedCursorMapper.upsert(newCursor);
        }
        longTermMemoryService.deleteExpiredTaskRecordsMemories(sessionId, userId);

        logger.info("Processing memory for sessionId: {}, cleaned messages count: {}", sessionId, cleanedMessages.size());
    }

    private static long computeMaxId(List<ChatMemory> items) {
        long max = 0L;
        if (items == null) {
            return max;
        }
        for (ChatMemory m : items) {
            if (m != null && m.getId() != null && m.getId() > max) {
                max = m.getId();
            }
        }
        return max;
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
            LangChain4jChatModelListener langChain4JChatModelListener = new LangChain4jChatModelListener(AiModelCallSourceEnum.MemoryOrganize)
                    .setSessionId(sessionId)
                    .setUserId(userId)
                    .setEventPublisher(eventPublisher);

            ChatModel chatModel = aiModelService.createChatModel(modelId, true, langChain4JChatModelListener);

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


    public class MemoryTool{
        private final ILongTermMemoryService longTermMemoryService;

        public MemoryTool(ILongTermMemoryService longTermMemoryService) {
            this.longTermMemoryService = longTermMemoryService;
        }

        @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
        @Tool(value = {"按Id查询用户记忆", "查询用户记忆详细内容,根据指定Id查询"},searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
        public String queryUserMemoryById(@P(description="记忆Id")Long id){
            return longTermMemoryService.getMemoryContentById(id);
        }
        @ToolSecurityLevel(ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL)
        @Tool(value = {"按Id删除用户记忆", "删除用户记忆内容，根据指定id删除"},searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
        public String deleteUserMemoryById(@P(description="记忆Id") Long id){
            longTermMemoryService.deleteMemory(id);
            return "成功";
        }
        @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
        @Tool(value = {"保存用户记忆", "保存用户记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。"},searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
        public String saveUserMemory(@P(description = "记忆类型:userProfile、taskRecords、empiricalKnowledge",required = false) String memoryType,
                                     @P(description = "记忆概要") String summary,
                                     @P(description = "记忆内容") String memory,
                                     @P(description = "记忆Id，如果传入记忆Id则为更新，如果不传记忆Id则为新增。",required = false) Long id,
                                     InvocationParameters invocationParameters) {
            return longTermMemoryService.saveMemory(UserMemoryTypeEnum.fromCode(memoryType),summary,memory,id,invocationParameters);
        }

    }
}
