package com.agent.hopaw.infra.executor;


import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.constant.ChatMemoryStatusEnum;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.event.ChatHistoryEvent;
import com.agent.hopaw.infra.exception.ToolCallRejectedException;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.model.entity.*;
import com.agent.hopaw.infra.model.dto.*;
import com.agent.hopaw.infra.service.AiModelService;
import com.agent.hopaw.infra.service.IChatModelListenerProvider;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.storage.ChatHistoryStore;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.agent.hopaw.infra.util.PendingResponse;
import com.agent.hopaw.infra.util.UuidUtil;
import com.alibaba.fastjson2.JSON;
import org.springframework.context.ApplicationEventPublisher;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.search.vector.VectorToolSearchStrategy;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AgentExecutor implements IAgentExecutor {
    private final Logger logger = LoggerFactory.getLogger(AgentExecutor.class);

    /**
     * 工具执行线程池的 ThreadFactory，静态常量复用
     */
    private static final ThreadFactory TOOL_THREAD_FACTORY = new ThreadFactory() {
        private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName("agent-tool-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    };

    private final Long agentId;
    private final String userId;
    private final String sessionId;
    private final Long aiModelId;
    private final AgentMessageHandler agentMessageHandler;
    private final AtomicBoolean cancelTask = new AtomicBoolean(false);
    private final IChatMemoryService memoryStore;
    private final java.util.concurrent.ConcurrentMap<String, AtomicBoolean> toolCancelInvocations = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, CountDownLatch> toolCancelLatch = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, Consumer<String>> toolStopHooks = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, PendingResponse<Boolean>> toolApprovalLocks = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, String> toolNameByCallIdMap = new ConcurrentHashMap<>();
    /** 本执行器生命周期内已开始的工具调用次数（工具开始执行时递增，供统计展示） */
    private final java.util.concurrent.atomic.AtomicInteger executedToolCount = new java.util.concurrent.atomic.AtomicInteger(0);
    private final Map<String, ToolInfo> toolInfoMap = new HashMap<>();
    private final ChatMemoryId memoryId;
    private final EmbeddingModel embeddingModel;
    private final ThreadPoolExecutor toolExecutor;
    private final AiModelService aiModelService;
    private CountDownLatch taskLatch = new CountDownLatch(0);
    /** 可重置看门狗：当前任务等待的最后活动截止时间戳（毫秒），收到消息/工具调用等活动时重置 */
    private final java.util.concurrent.atomic.AtomicLong watchdogDeadlineMs = new java.util.concurrent.atomic.AtomicLong(0);
    /** 看门狗超时时长（毫秒），0 表示未启用 */
    private volatile long watchdogTimeoutMs = 0;
    /** 本次任务开始时间（毫秒级时间戳），0 表示未开始 */
    private volatile long startTimeMs = 0;
    private String requestId;
    private final ApplicationEventPublisher eventPublisher;
    private final IChatSessionService chatSessionService;
    private final AgentExecutorParams agentExecutorParams;
    private final List<McpClient> mcpClients = new ArrayList<>();
    private final Function<Long, String> systemMessageProvider;
    private final IChatModelListenerProvider chatModelListenerProvider;
    public AgentExecutor(AgentExecutorParams agentExecutorParams,
                         IChatMemoryService memoryStore,
                         EmbeddingModel embeddingModel,
                         Function<Long, String> systemMessageProvider,
                         AiModelService aiModelService,
                         IChatModelListenerProvider chatModelListenerProvider,
                         ApplicationEventPublisher eventPublisher,
                         IChatSessionService chatSessionService) {
        this.agentExecutorParams = agentExecutorParams;
        this.agentId = agentExecutorParams.getAgentId();
        this.userId = agentExecutorParams.getUserId();
        this.aiModelId = agentExecutorParams.getAiModelId();
        this.sessionId = agentExecutorParams.getSessionId();
        this.requestId = agentExecutorParams.getRequestId();

        this.chatSessionService = chatSessionService;
        this.chatModelListenerProvider = chatModelListenerProvider;
        this.eventPublisher = eventPublisher;
        this.aiModelService = aiModelService;
        this.memoryStore = memoryStore;
        this.embeddingModel = embeddingModel;
        this.systemMessageProvider = systemMessageProvider;

        this.memoryId = new ChatMemoryId(sessionId,this.requestId, agentId, userId);
        // 创建工具执行线程池
        this.toolExecutor = createToolExecutor();
        this.agentMessageHandler = new AgentMessageHandler(this.sessionId, this.requestId, eventPublisher, toolInfoMap);
        for (ToolSetInfo toolSet : agentExecutorParams.getToolSets()) {
            for (ToolInfo tool : toolSet.getTools()) {
                toolInfoMap.put(tool.getName(),tool);
            }
        }
        ToolInfo toolInfo = new ToolInfo(AgentTool.TOOL_SEARCH_TOOL_NAME, AgentTool.TOOL_SEARCH_TOOL_DESCRIPTION,new ArrayList<>(0));
        toolInfo.setDescriptions(Arrays.asList(AgentTool.TOOL_SEARCH_TOOL_DESCRIPTION));
        toolInfoMap.put(AgentTool.TOOL_SEARCH_TOOL_NAME, toolInfo);
    }

    @Override
    public Long getAgentId() {
        return agentId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    @Override
    public Long getAiModelId() {
        return aiModelId;
    }

    private List<String> getToolDescriptions(String toolName) {
        ToolInfo toolInfo = toolInfoMap.get(toolName);
        if (toolInfo == null || toolInfo.getDescriptions() == null || toolInfo.getDescriptions().isEmpty()) {
            return new ArrayList<>();
        }
        return toolInfo.getDescriptions();
    }

    @Override
    public void stop() {

        //拒绝所有审批
        toolApprovalLocks.values().forEach(x->{
            x.complete(false);
        });
        //停止所有工具
        toolCancelInvocations.values().forEach(atomicBoolean -> atomicBoolean.set(true));
        toolStopHooks.entrySet().forEach(entry -> {
            String callId = entry.getKey();
            String toolName = toolNameByCallIdMap.get(callId);
            List<String> toolDescriptions = toolName == null ? new ArrayList<>() : getToolDescriptions(toolName);
            AiToolCallMessageInfo stopping = AiToolCallMessageInfo.stopping(sessionId, requestId, callId, toolDescriptions);
            agentMessageHandler.sendMessageToChannel(stopping);
            entry.getValue().accept(callId);
        });
        //工具取消 latch 立即放行，不再逐个等待工具自然结束（工具已收到取消标记与停止钩子）
        toolCancelLatch.values().forEach(countDownLatch -> countDownLatch.countDown());

        //停止任务
        cancelTask.set(true);
        //立即唤醒 execute() 的等待循环，使其进入 finally 清理并发送 task-done，
        //不再等待任务自然结束（模型静默时 taskLatch 可能长时间不 countDown）
        taskLatch.countDown();

        //立即通知前端会话已停止
        agentMessageHandler.done();

        // 关闭工具执行线程池，释放资源（工具已收到取消标记，直接中断）
        if (toolExecutor != null && !toolExecutor.isShutdown()) {
            toolExecutor.shutdownNow();
        }

        // 关闭 MCP 客户端连接
        for (McpClient client : mcpClients) {
            try {
                client.close();
                logger.info("MCP client closed: {}", client);
            } catch (Exception e) {
                logger.error("Failed to close MCP client: {}", e.getMessage());
            }
        }
        mcpClients.clear();
    }

    @Override
    public void addToolStopHook(String callId, Consumer<String> hook) {
        toolStopHooks.put(callId, hook);
        String toolName = toolNameByCallIdMap.get(callId);
        List<String> toolDescriptions = toolName == null ? new ArrayList<>() : getToolDescriptions(toolName);
        AiToolCallMessageInfo stoppable = AiToolCallMessageInfo.stoppable(sessionId, requestId, callId, toolDescriptions);
        agentMessageHandler.sendMessageToChannel(stoppable);
    }

    @Override
    public void stopTool(String callId) {
        //停止工具
        if (toolCancelInvocations.containsKey(callId)) {
            toolCancelInvocations.get(callId).set(true);
        }
        if (toolStopHooks.containsKey(callId)) {
            Consumer<String> hook = toolStopHooks.get(callId);
            String toolName = toolNameByCallIdMap.get(callId);
            List<String> toolDescriptions = toolName == null ? new ArrayList<>() : getToolDescriptions(toolName);
            AiToolCallMessageInfo stopping = AiToolCallMessageInfo.stopping(sessionId, requestId, callId, toolDescriptions);
            agentMessageHandler.sendMessageToChannel(stopping);
            hook.accept(callId);
        }
    }

    @Override
    public boolean toolHaveCall(String callId) {
        return toolCancelInvocations.containsKey(callId);
    }

    @Override
    public boolean toolIsCancelled(String callId) {
        return toolCancelInvocations.containsKey(callId) && toolCancelInvocations.get(callId).get();
    }

    @Override
    public void sendToolRunningContent(String callId, Object resultPartial) {
        resetWatchdog();
        String toolName = toolNameByCallIdMap.get(callId);
        List<String> toolDescriptions = toolName == null ? new ArrayList<>() : getToolDescriptions(toolName);
        AiToolCallMessageInfo aiToolCallMessageInfo = AiToolCallMessageInfo.running(sessionId, requestId, callId, resultPartial, toolDescriptions);
        agentMessageHandler.sendMessageToChannel(aiToolCallMessageInfo);
    }

    private void sendToolApprovalMessage(String sessionId,String callId, String toolName, Object arguments){
        List<String> toolDescriptions = getToolDescriptions(toolName);
        AiToolCallMessageInfo aiToolCallMessageInfo = AiToolCallMessageInfo.approval(sessionId, requestId, callId, toolName, arguments, toolDescriptions);
        agentMessageHandler.sendMessageToChannel(aiToolCallMessageInfo);
    }
    @Override
    public void toolApprovalComplete(String callId,Boolean allowed){
        resetWatchdog();
        String approvalId = callId;
        if(toolApprovalLocks.containsKey(approvalId)){
            toolApprovalLocks.get(approvalId).complete(allowed);
        }
    }

    private void sendFirstState() {
        try {
            AiMessageBaseInfo message=new AiMessageBaseInfo("received");
            message.sessionId(sessionId);
            message.setRequestId(requestId);
            message.setContent("已收到消息，开始处理");
            agentMessageHandler.sendMessageToChannel(message);
        } catch (Exception e) {
            logger.error("sendFirstState error", e);
        }
    }
    @Override
    public boolean running() {
        return taskLatch.getCount() > 0;
    }

    /**
     * 重置看门狗截止时间：收到消息、流式内容、思考内容、工具调用/执行/过程通知等活动时调用，
     * 保证只要任务持续有进展就不会因固定超时被误判结束
     */
    private void resetWatchdog() {
        long timeoutMs = watchdogTimeoutMs;
        if (timeoutMs > 0) {
            watchdogDeadlineMs.set(System.currentTimeMillis() + timeoutMs);
        }
    }

    /**
     * 查询看门狗剩余等待时间（秒，向上取整）；
     * 执行器未运行或未启用看门狗时返回 0
     */
    @Override
    public long getWatchdogRemainingSeconds() {
        if (!running()) {
            return 0;
        }
        long remaining = watchdogDeadlineMs.get() - System.currentTimeMillis();
        return Math.max(0, (remaining + 999) / 1000);
    }

    /**
     * 查询本次任务已运行时长（秒，向上取整）；
     * 执行器未运行或未记录开始时间时返回 0
     */
    @Override
    public long getElapsedSeconds() {
        if (!running() || startTimeMs <= 0) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return Math.max(0, (elapsed + 999) / 1000);
    }

    @Override
    public int getExecutedToolCount() {
        return executedToolCount.get();
    }

    @Override
    public int getMaxToolInvocations() {
        return agentExecutorParams.getMaxToolInvocations() != null ? agentExecutorParams.getMaxToolInvocations() : 0;
    }

    @Override
    public void execute(List<Content> contents){
        execute(contents,300L);
    }
    @Override
    public void execute(List<Content> contents,long timeout) {
        try {
            // 启用可重置看门狗：超时时间在有活动（消息/工具调用/过程通知）时会顺延
            this.watchdogTimeoutMs = timeout * 1000L;
            resetWatchdog();
            // 记录本次任务开始时间（毫秒级时间戳），供前端展示已运行时长
            this.startTimeMs = System.currentTimeMillis();
            sendFirstState();
            saveChatSession(contents);
            this.memoryStore.orphanCleanup(memoryId);
            this.cancelTask.set(false);
            this.taskLatch = new CountDownLatch(1);


            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
                    .setUserId(userId)
                    .setAgentId(agentId)
                    .setSessionId(sessionId)
                    .setRequestId(requestId);

            ChatAgentAssistant chatAgentAssistant = createChatAgentAssistant();

            TokenStream tokenStream = chatAgentAssistant.streamingChat(contents, invocationParametersWrapper.getParameters())
                    .onError(e -> {
                        logger.error("Streaming chat error: {}", e.getMessage(), e);
                        agentMessageHandler.onErrorHandler(e);
                        taskLatch.countDown();
                    }).onCompleteResponse(response -> {
                        agentMessageHandler.onCompleteResponseHandler(response);
                        taskLatch.countDown();
                    }).onPartialResponseWithContext((r, ctx) -> {
                        resetWatchdog();
                        if (cancelTask.get()) {
                            agentMessageHandler.partialResponseHandler(r.text());
                            agentMessageHandler.done();
                            ctx.streamingHandle().cancel(); // ✅ 真正中断：关闭流、停止LLM、省token
                            taskLatch.countDown();
                            return;
                        }
                        agentMessageHandler.partialResponseHandler(r.text());
                    })
                    .onPartialThinkingWithContext((thinking, ctx) -> {
                        resetWatchdog();
                        if (cancelTask.get()) {
                            agentMessageHandler.thinkingHandler(thinking);
                            agentMessageHandler.done();
                            ctx.streamingHandle().cancel(); // ✅ 真正中断：关闭流、停止LLM、省token
                            taskLatch.countDown();
                            return;
                        }
                        agentMessageHandler.thinkingHandler(thinking);
                    })
                    .onPartialToolCallWithContext((toolCall, ctx) -> {
                        resetWatchdog();
                        if (!toolCancelInvocations.containsKey(toolCall.id())) {
                            toolCancelInvocations.put(toolCall.id(), new AtomicBoolean(false));
                            toolCancelLatch.put(toolCall.id(), new CountDownLatch(1));
                        }
                        toolNameByCallIdMap.put(toolCall.id(), toolCall.name());
                        //logger.info("Tool call: {}", toolCall.toString());
                        agentMessageHandler.partialToolExecutionHandler(toolCall);
                        //工具或任务停止
                        if (toolCancelInvocations.get(toolCall.id()).get() || cancelTask.get()) {
                            if (toolCancelLatch.containsKey(toolCall.id())) {
                                toolCancelLatch.get(toolCall.id()).countDown();
                            }
                            ctx.streamingHandle().cancel(); // ✅ 真正中断：关闭流、停止LLM、省token
                            agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_EXECUTED, toolCall.id(),toolCall.name(),null,"用户取消了工具调用");
                            taskLatch.countDown();
                            return;
                        }
                    })
                    .beforeToolExecution(toolExecution -> {
                        resetWatchdog();
                        String toolCallId = toolExecution.request().id();
                        String toolName = toolExecution.request().name();
                        String arguments = toolExecution.request().arguments();

                        if(toolCallId==null){
                            return;
                        }

                        ToolInfo toolInfo = toolInfoMap.getOrDefault(toolName, null);
                        ToolSecurityLevel.Level toolLevel = toolInfo==null? ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL:toolInfo.getSecurityLevel();

                        InvocationParameters invocationParameters = toolExecution.invocationContext().invocationParameters();
                        invocationParameters.put("toolCallId", toolCallId);
                        //拦截执行
                        boolean allowed=false;
                        if(toolName.equals(AgentTool.TOOL_SEARCH_TOOL_NAME) || ToolSecurityLevel.Level.SAFE.equals(toolLevel)) {
                            allowed=true;
                        }else if("auto".equals(agentExecutorParams.getToolCallPermission())){
                            //完全自动
                            allowed=true;
                        }else if("smart_call".equals(agentExecutorParams.getToolCallPermission())){
                            if(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL.equals(toolLevel)){
                                String result = analyzeToolCall(toolInfo, arguments);
                                if(result!=null && result.contains("否")){
                                    allowed=true;
                                }
                            }
                        }else{
                        }
                        ToolExecutionRequest toolCallInfo = toolExecution.request();
                        if(!allowed){
                            //需要审批
                            String approvalId=toolCallId;
                            // 这个对象会阻塞工具的进一步执行，直到被外部完成
                            PendingResponse<Boolean> pending = new PendingResponse<>(approvalId);
                            toolApprovalLocks.put(approvalId,pending);
                            //审批开始
                            agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_APPROVAL, toolCallInfo.id(),toolCallInfo.name(),toolCallInfo.arguments(),null);
                            //审批结果
                            allowed = pending.blockingGet();
                        }
                        if(allowed){
                            // 任务开始
                            executedToolCount.incrementAndGet();
                            agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_STARTING, toolCallInfo.id(),toolCallInfo.name(),toolCallInfo.arguments(),null);
                        }else{
                            //拒绝执行
                            agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_REJECTED, toolCallInfo.id(),toolCallInfo.name(),toolCallInfo.arguments(),"用户拒绝了工具调用");
                            throw new ToolCallRejectedException("用户拒绝了工具调用");
                        }
                    })
                    .onToolExecuted(toolExecution -> {
                        resetWatchdog();
                        ToolExecutionRequest toolExecutionRequest = toolExecution.request();
                        //任务完成
                        if (toolCancelLatch.containsKey(toolExecutionRequest.id())) {
                            toolCancelLatch.get(toolExecutionRequest.id()).countDown();
                        }
                        if (toolStopHooks.containsKey(toolExecutionRequest.id())) {
                            toolStopHooks.remove(toolExecutionRequest.id());
                        }
                        if (toolCancelInvocations.containsKey(toolExecutionRequest.id())) {
                            toolCancelInvocations.remove(toolExecutionRequest.id());
                        }
                        toolNameByCallIdMap.remove(toolExecutionRequest.id());
                        //工具执行完成
                        agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_EXECUTED, toolExecutionRequest.id(),toolExecutionRequest.name(),toolExecutionRequest.arguments(),toolExecution.result());
                    });
            tokenStream.start();
            // 可重置看门狗等待：活动会重置截止时间，仅在持续无活动超过超时时间时结束
            while (taskLatch.getCount() > 0) {
                long remainingMs = watchdogDeadlineMs.get() - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    logger.warn("执行器等待超时（{}秒内无任何活动），sessionId: {}", timeout, sessionId);
                    break;
                }
                if (taskLatch.await(remainingMs, TimeUnit.MILLISECONDS)) {
                    break;
                }
            }
        } catch (Exception e) {
            logger.error("\n(注: 流式响应失败: " + e.getMessage() + ")\n可以尝试清理对话后重新发送。", e);
            agentMessageHandler.onErrorHandler(e);
        } finally {
            cancelTask.set(true);
            toolCancelLatch.values().forEach(latch -> latch.countDown());
            toolCancelLatch.clear();
            toolCancelInvocations.clear();
            toolStopHooks.clear();
            toolApprovalLocks.clear();
            toolNameByCallIdMap.clear();
            taskLatch.countDown();
            agentMessageHandler.taskDone();
            updateMemoryStateToDone();
        }
    }

    private void updateMemoryStateToDone() {
        try {
            this.memoryStore.updateStatusBySessionIdAndRequestId(sessionId, requestId,ChatMemoryStatusEnum.DEFAULT, ChatMemoryStatusEnum.TASK_DONE);
        }catch (Exception ex){
            logger.error("Error updating memory state to done", ex);
        }
    }

    private String analyzeUserIntent(List<Content> contents) {
        try {
            ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(AiModelCallSourceEnum.ChatAnalyzeUserIntent, sessionId, userId, agentId,agentExecutorParams.getExtParams());

            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
                    .setUserId(userId)
                    .setAgentId(agentId)
                    .setRequestId(requestId)
                    .setSessionId(sessionId);
            ChatModel chatModel = aiModelService.createChatModel(agentExecutorParams.getAiModelId(), false, chatModelListener);

            ChatAgentAssistant assistant = AiServices.builder(ChatAgentAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> "你只需要通过用户输入的内容来分析用户意图，不需要为用户的提问给出答案，直接返回给我一个15字以内的简要说明，不要带人称和句号。")
                    .build();
            ChatRequestParameters chatRequestParameters = ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.analyze(contents, chatRequestParameters, invocationParametersWrapper.getParameters());
            return result;
        } catch (Exception ex) {
            logger.error("Error analyzing user intent", ex);
            return null;
        }
    }

    private String analyzeToolCall(ToolInfo toolInfo,String arguments) {
        try {
            ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(AiModelCallSourceEnum.ChatToolCallCheck, sessionId, userId, agentId,agentExecutorParams.getExtParams());
            String systemMessage="你只是一个工具调用安全检查员，你需要判断用户提交到调用是否需要人工介入？只需要返回给用户：是或否";

            List<Content> contents=new ArrayList<>();
            contents.add(new TextContent("现在我要调用函数"+toolInfo.getName()+",这个函数的作用是"+toolInfo.getDescription()));

            if(StringUtils.hasLength(arguments)){
                contents.add(new TextContent("参数是:"+arguments));
            }
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
                    .setUserId(userId)
                    .setAgentId(agentId)
                    .setRequestId(requestId)
                    .setSessionId(sessionId);
            ChatModel chatModel = aiModelService.createChatModel(agentExecutorParams.getAiModelId(), false, chatModelListener);

            String finalSystemMessage = systemMessage;
            ChatAgentAssistant assistant = AiServices.builder(ChatAgentAssistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> finalSystemMessage)
                    .build();
            ChatRequestParameters chatRequestParameters = ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.analyze(contents, chatRequestParameters, invocationParametersWrapper.getParameters());
            return result;
        } catch (Exception ex) {
            logger.error("Error analyzing user intent", ex);
            return null;
        }
    }

    private ChatAgentAssistant createChatAgentAssistant() {
        MessageWindowChatMemory.Builder memoryBuilder = MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(agentExecutorParams.getMaxMemoryRecords() != null ? agentExecutorParams.getMaxMemoryRecords() : 20)
                .chatMemoryStore(memoryStore != null ? memoryStore : new InMemoryChatMemoryStore());
        var aiBuilder = AiServices
                .builder(ChatAgentAssistant.class)
                .systemMessageProvider(chatMemoryId -> systemMessageProvider.apply(agentId))
                .chatMemory(memoryBuilder.build())
                .executeToolsConcurrently(toolExecutor)
                // 幻觉工具处理：不抛异常，而是把"工具不存在"作为工具执行结果返回给大模型，引导其改用正确工具
                .hallucinatedToolNameStrategy(request -> {
                    logger.warn("Hallucinated tool call detected: tool={}, requestId={}, sessionId={}",
                            request.name(), requestId, sessionId);
                    return ToolExecutionResultMessage.from(request,
                            "工具 " + request.name() + " 不存在。请勿调用不存在的工具，只能使用本次会话中提供的可用工具列表里的工具。");
                })
                // 工具参数错误处理：参数解析/类型转换失败（如布尔参数传成字符串）时，不终止会话，
                // 把错误信息作为工具执行结果返回给大模型，引导其修正参数后重试
                .toolArgumentsErrorHandler((throwable, context) -> {
                    logger.warn("Tool arguments error: tool={}, requestId={}, sessionId={}, error={}",
                            context.toolExecutionRequest().name(), requestId, sessionId, throwable.getMessage());
                    agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_EXECUTED,
                            context.toolExecutionRequest().id(), context.toolExecutionRequest().name(),
                            context.toolExecutionRequest().arguments(),
                            "工具调用参数错误：" + throwable.getMessage());
                    return ToolErrorHandlerResult.text(
                            "工具调用参数错误：" + throwable.getMessage() + "。请检查参数类型（布尔、数字等不要以字符串形式传入），修正参数后重新调用该工具。");
                })
                // 工具执行异常处理：工具内部抛出的异常同样不终止会话，作为结果返回给大模型自行处理
                .toolExecutionErrorHandler((throwable, context) -> {
                    logger.warn("Tool execution error: tool={}, requestId={}, sessionId={}, error={}",
                            context.toolExecutionRequest().name(), requestId, sessionId, throwable.getMessage());
                    agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_EXECUTED,
                            context.toolExecutionRequest().id(), context.toolExecutionRequest().name(),
                            context.toolExecutionRequest().arguments(),
                            "工具执行异常：" + throwable.getMessage());
                    return ToolErrorHandlerResult.text(
                            "工具执行异常：" + throwable.getMessage() + "。请根据异常信息调整调用方式或修正后重试。");
                });
        List<AgentTool> selectedTools = agentExecutorParams.getToolSets().stream().map(x->x.getAgentTool()).collect(Collectors.toList());
        if (selectedTools != null && agentExecutorParams.getVectorToolSearch() != null && agentExecutorParams.getVectorToolSearch()) {
            int maxResults = agentExecutorParams.getVectorToolSearchMaxResults() != null ? agentExecutorParams.getVectorToolSearchMaxResults() : 10;
            aiBuilder.toolSearchStrategy(
                    VectorToolSearchStrategy
                            .builder()
                            .embeddingModel(embeddingModel)
                            .maxResults(maxResults).build()
            );
        }
        if (!selectedTools.isEmpty()) {
            int maxToolInvocations = agentExecutorParams.getMaxToolInvocations() != null ? agentExecutorParams.getMaxToolInvocations() : 0;
            if (maxToolInvocations > 0) {
                aiBuilder.maxSequentialToolsInvocations(maxToolInvocations);
            }
            aiBuilder.tools(selectedTools.toArray());
        }

        // MCP 工具集成：为每个已启用的 MCP 服务器创建客户端并注册
        List<McpServerConfig> mcpConfigs = agentExecutorParams.getMcpServerConfigs();
        if (mcpConfigs != null && !mcpConfigs.isEmpty()) {
            List<McpClient> clients = new ArrayList<>();
            for (McpServerConfig config : mcpConfigs) {
                long startMs = System.currentTimeMillis();
                try {
                    McpTransport transport = buildMcpTransport(config);
                    logger.info("MCP client initializing: name={}, type={}, url/cmd={}",
                            config.getName(),
                            config.getTransportType(),
                            "http".equalsIgnoreCase(config.getTransportType()) ? config.getUrl() : config.getCommand());
                    McpClient mcpClient = DefaultMcpClient.builder()
                            .key(config.getName())
                            .transport(transport)
                            .toolExecutionTimeout(java.time.Duration.ofSeconds(30))
                            .toolExecutionTimeoutErrorMessage("MCP 工具执行超时（30s）")
                            .build();
                    clients.add(mcpClient);
                    logger.info("MCP client created: name={}, elapsed={}ms", config.getName(), System.currentTimeMillis() - startMs);
                } catch (Exception e) {
                    logger.error("Failed to create MCP client for {}: {}, elapsed={}ms", config.getName(), e.getMessage(), System.currentTimeMillis() - startMs, e);
                }
            }
            if (!clients.isEmpty()) {
                ToolProvider toolProvider = McpToolProvider.builder()
                        .mcpClients(clients)
                        .failIfOneServerFails(false)
                        .build();
                aiBuilder.toolProvider(toolProvider);
                mcpClients.addAll(clients);
                logger.info("MCP toolProvider registered with {} client(s)", clients.size());
            } else {
                logger.warn("MCP configs found but no client was created, proceeding without MCP tools");
            }
        }
        ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(agentExecutorParams.getBizType().getAiModelCallSourceEnum(), sessionId, userId, agentId,agentExecutorParams.getExtParams());
        StreamingChatModel streamingModel = aiModelService.createStreamingChatModel(agentExecutorParams.getAiModelId(), agentExecutorParams.getEnableThinking(), chatModelListener);
        return aiBuilder.streamingChatModel(streamingModel).build();
    }

    /**
     * 根据 MCP 配置构建对应的传输层。
     *
     * <p>对于 HTTP 类型，支持从 {@code extParams}（JSON）读取以下可选字段：
     * <ul>
     *   <li>{@code headers}        : Map&lt;String,String&gt; 自定义请求头</li>
     *   <li>{@code timeoutSeconds} : Number 连接超时（秒）</li>
     *   <li>{@code logRequests}    : Boolean 打印请求</li>
     *   <li>{@code logResponses}   : Boolean 打印响应</li>
     *   <li>{@code followRedirects}: Boolean 跟随 3xx 重定向</li>
     *   <li>{@code httpVersion1_1} : Boolean 强制 HTTP/1.1</li>
     *   <li>{@code subsidiaryChannel}: Boolean 启用附属 SSE 通道</li>
     * </ul>
     *
     * <p>对于 STDIO 类型，支持：
     * <ul>
     *   <li>{@code env}        : Map&lt;String,String&gt; 子进程环境变量</li>
     *   <li>{@code logEvents}  : Boolean 打印流量</li>
     * </ul>
     */
    private McpTransport buildMcpTransport(McpServerConfig config) {
        String transportType = config.getTransportType();
        // 解析扩展参数
        com.alibaba.fastjson2.JSONObject ext = null;
        if (config.getExtParams() != null && !config.getExtParams().isBlank()) {
            try {
                ext = JSON.parseObject(config.getExtParams());
            } catch (Exception e) {
                logger.warn("Failed to parse extParams for MCP '{}', ignoring: {}", config.getName(), e.getMessage());
            }
        }

        if ("http".equalsIgnoreCase(transportType)) {
            StreamableHttpMcpTransport.Builder builder = StreamableHttpMcpTransport.builder()
                    .url(config.getUrl())
                    .timeout(java.time.Duration.ofSeconds(15)); // 默认 15 秒，防止 build() 无限阻塞

            if (ext != null) {
                // 自定义请求头
                com.alibaba.fastjson2.JSONObject headers = ext.getJSONObject("headers");
                if (headers != null && !headers.isEmpty()) {
                    Map<String, String> headerMap = new LinkedHashMap<>();
                    for (String key : headers.keySet()) {
                        headerMap.put(key, headers.getString(key));
                    }
                    builder.customHeaders(headerMap);
                }
                // 连接超时（覆盖默认值）
                Long timeoutSeconds = ext.getLong("timeoutSeconds");
                if (timeoutSeconds != null && timeoutSeconds > 0) {
                    builder.timeout(java.time.Duration.ofSeconds(timeoutSeconds));
                }
                // 日志开关
                Boolean logRequests = ext.getBoolean("logRequests");
                if (logRequests != null) {
                    builder.logRequests(logRequests);
                }
                Boolean logResponses = ext.getBoolean("logResponses");
                if (logResponses != null) {
                    builder.logResponses(logResponses);
                }
                // 跟随重定向
                Boolean followRedirects = ext.getBoolean("followRedirects");
                if (followRedirects != null) {
                    builder.followRedirects(followRedirects);
                }
                // 强制 HTTP/1.1
                Boolean httpVersion1_1 = ext.getBoolean("httpVersion1_1");
                if (Boolean.TRUE.equals(httpVersion1_1)) {
                    builder.setHttpVersion1_1();
                }
                // 附属 SSE 通道
                Boolean subsidiaryChannel = ext.getBoolean("subsidiaryChannel");
                if (subsidiaryChannel != null) {
                    builder.subsidiaryChannel(subsidiaryChannel);
                }
            }

            return builder.build();
        } else {
            // 默认使用 stdio
            List<String> commandParts = parseCommand(config.getCommand());
            StdioMcpTransport.Builder builder = StdioMcpTransport.builder()
                    .command(commandParts);

            if (ext != null) {
                // 环境变量
                com.alibaba.fastjson2.JSONObject env = ext.getJSONObject("env");
                if (env != null && !env.isEmpty()) {
                    Map<String, String> envMap = new LinkedHashMap<>();
                    for (String key : env.keySet()) {
                        envMap.put(key, env.getString(key));
                    }
                    builder.environment(envMap);
                }
                // 日志开关
                Boolean logEvents = ext.getBoolean("logEvents");
                if (logEvents != null) {
                    builder.logEvents(logEvents);
                }
            }

            return builder.build();
        }
    }

    /**
     * 解析命令行字符串为命令参数列表
     */
    private List<String> parseCommand(String command) {
        if (command == null || command.isBlank()) {
            return Collections.emptyList();
        }
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (char c : command.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private void saveChatSession(List<Content> contents) {
        boolean sendSessionTitle = false;
        ChatSession chatSession=chatSessionService.getSessionBySessionId(sessionId);
        if (chatSession == null) {
            chatSession = new ChatSession();
            // 优先使用外部传入的会话标题（任务/项目场景传任务名称、项目名称），否则从用户输入分析
            String userIntent = agentExecutorParams.getSessionTitle();
            boolean fromParam = userIntent != null && !userIntent.isBlank();
            if (!fromParam) {
                userIntent = analyzeUserIntent(contents);
            }
            if (userIntent == null) {
                userIntent = "新聊天";
            }else{
                sendSessionTitle=true;
            }
            chatSession.setSessionId(sessionId);
            chatSession.setAgentId(agentId);
            chatSession.setUserId(userId);
            chatSession.setTitle(userIntent);
            chatSession.setEnableThinking(agentExecutorParams.getEnableThinking());
            chatSession.setAiModelId(agentExecutorParams.getAiModelId());
            chatSession.setSkillNames(String.join(",", agentExecutorParams.getSkillNames() == null ? new ArrayList<>() : agentExecutorParams.getSkillNames()));
            chatSession.setLastUpdateTime(LocalDateTime.now());
            chatSession.setCreateTime(LocalDateTime.now());
            chatSession.setToolCallPermission(agentExecutorParams.getToolCallPermission());
            chatSession.setBizType(agentExecutorParams.getBizType().getValue());
            chatSessionService.insertSession(chatSession);
            agentMessageHandler.sendMessageToChannel(AiMessageBaseInfo.sessionTitle(sessionId, requestId, userIntent));
        } else {
            // 占位标题（旧默认“新聊天”或前端预创建会话的默认“新会话”）时分析用户意图/使用外部标题
            if("新聊天".equals(chatSession.getTitle()) || "新会话".equals(chatSession.getTitle())){
                // 优先使用外部传入的会话标题（任务/项目场景传任务名称、项目名称），否则从用户输入分析
                String paramTitle = agentExecutorParams.getSessionTitle();
                if (paramTitle != null && !paramTitle.isBlank()) {
                    chatSession.setTitle(paramTitle);
                    sendSessionTitle = true;
                } else {
                    String userIntent = analyzeUserIntent(contents);
                    if (userIntent !=null) {
                        chatSession.setTitle(userIntent);
                        sendSessionTitle=true;
                    }
                }
            }
            chatSession.setSessionId(sessionId);
            chatSession.setAgentId(agentId);
            chatSession.setUserId(userId);
            chatSession.setEnableThinking(agentExecutorParams.getEnableThinking());
            chatSession.setAiModelId(agentExecutorParams.getAiModelId());
            chatSession.setSkillNames(String.join(",", agentExecutorParams.getSkillNames() == null ? new ArrayList<>() : agentExecutorParams.getSkillNames()));
            chatSession.setLastUpdateTime(LocalDateTime.now());
            chatSession.setToolCallPermission(agentExecutorParams.getToolCallPermission());
            // bizType 不参与 update：来源一旦确定不可修改，保持 INSERT 时写入的值
            chatSessionService.updateSession(chatSession);
        }
        if(sendSessionTitle){
            agentMessageHandler.sendMessageToChannel(AiMessageBaseInfo.sessionTitle(sessionId, requestId, chatSession.getTitle()));
        }

    }

    /**
     * 创建工具执行线程池
     * 使用 ThreadPoolExecutor 手动创建，更好地控制线程池行为
     *
     * @return 配置好的 ThreadPoolExecutor 实例
     */
    private ThreadPoolExecutor createToolExecutor() {
        // 固定 5 个线程，有界队列容量 10，CallerRunsPolicy 拒绝策略
        int toolThreadPoolSize = 5;

        // 手动创建 ThreadPoolExecutor
        // corePoolSize = maximumPoolSize = 5，固定大小线程池
        // 使用有界队列防止内存溢出，队列大小为线程数的2倍
        // CallerRunsPolicy 拒绝策略：队列满时由调用线程执行，提供背压机制
        return new ThreadPoolExecutor(
                toolThreadPoolSize,                    // corePoolSize
                toolThreadPoolSize,                    // maximumPoolSize
                60L, TimeUnit.SECONDS,                 // keepAliveTime（固定线程池不适用）
                new LinkedBlockingQueue<>(toolThreadPoolSize * 2),  // 有界队列
                TOOL_THREAD_FACTORY,                   // 复用静态 ThreadFactory
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );
    }

    /**
     * 智能体消息处理器
     */
    public class AgentMessageHandler {
        private final String sessionId;
        private final String requestId;
        private String lastMessageType = "";
        private String currentMessageType = "";
        private StringBuilder messageBuilder = new StringBuilder();
        private StringBuilder thinkingBuilder = new StringBuilder();
        private final ApplicationEventPublisher eventPublisher;
        private final Consumer<ChatHistory> chatHistoryConsumer;
        private final Map<String, ToolInfo> toolInfoMap;

        public AgentMessageHandler(String sessionId,
                                   String requestId,
                                   ApplicationEventPublisher eventPublisher,
                                   Map<String, ToolInfo> toolInfoMap) {
            this.sessionId = sessionId;
            this.requestId = requestId;
            this.eventPublisher = eventPublisher;
            this.chatHistoryConsumer = chatHistory -> {
                eventPublisher.publishEvent(new ChatHistoryEvent(chatHistory));
            };
            this.toolInfoMap = toolInfoMap;
        }

        public void sendMessageToChannel(AiMessageBaseInfo message) {
            // 消息附带会话业务类型，供推送端区分：项目/任务会话推送给所有在线用户
            if (message != null && agentExecutorParams.getBizType() != null) {
                message.setBizType(agentExecutorParams.getBizType());
            }
            eventPublisher.publishEvent(new AgentMessageEvent(userId, agentId, message));
        }

        public void done() {
            AiMessageBaseInfo aiMessageBaseInfo = AiMessageBaseInfo.done(sessionId, requestId);
            sendMessageToChannel(aiMessageBaseInfo);
            messageTypeChangedChatHistoryHandler("done");
        }

        public void taskDone() {
            AiMessageBaseInfo aiMessageBaseInfo = AiMessageBaseInfo.taskDone(sessionId, requestId);
            sendMessageToChannel(aiMessageBaseInfo);
            messageTypeChangedChatHistoryHandler("task-done");
        }

        private void onErrorHandler(Throwable ex) {
            String message="";
            String type="error";
            if(ex instanceof ToolCallRejectedException){
                message=ex.getMessage();
                type="warn";
            }else{
                message="发生异常：" + ex.getMessage();
            }
            AiMessageBaseInfo info = AiMessageBaseInfo.build(type,sessionId, requestId).content(message);
            sendMessageToChannel(info);
            messageTypeChangedChatHistoryHandler(type);
            // error/warn 类型消息独立入库，刷新页面后可重新渲染
            ChatHistory errorChat = new ChatHistory(agentId, "agent", type, message);
            errorChat.setSessionId(sessionId);
            errorChat.setUserId(userId);
            chatHistoryConsumer.accept(errorChat);
            taskDone();
        }

        private void onCompleteResponseHandler(ChatResponse response) {
            //发送
            taskDone();
        }


        private void partialToolExecutionHandler(PartialToolCall toolCall) {
            List<String> toolDescriptions = getToolDescriptions(toolCall.name());
            AiToolCallMessageInfo toolCallMessageInfo=  AiToolCallMessageInfo.preparing(sessionId, requestId,
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.partialArguments(),
                    toolCall.index(),
                    toolDescriptions
            );
            messageTypeChangedChatHistoryHandler(AiToolCallMessageInfo.TYPE_TOOL_CALL+"_"+toolCallMessageInfo.getStatus());
            sendToolCallHistoryEventAndToChannel(toolCallMessageInfo);
        }

        public void toolCallHandler(String status,String id, String toolName, String arguments, Object result) {
            List<String> toolDescriptions = getToolDescriptions(toolName);
            AiToolCallMessageInfo toolCallMessageInfo= AiToolCallMessageInfo.build(status,sessionId, requestId,
                    id,
                    toolName,
                    parseToolArgumentsSafely(arguments),
                    result,
                    toolDescriptions
            );
            sendToolCallHistoryEventAndToChannel(toolCallMessageInfo);
            messageTypeChangedChatHistoryHandler(AiToolCallMessageInfo.TYPE_TOOL_CALL+"_"+status);
        }

        /**
         * 解析工具调用参数 JSON：参数由模型流式生成，可能不完整或非法。
         * 解析失败时不抛异常，返回包含失败原因与原始参数文本的兜底 Map，保证消息链路不断。
         */
        private Object parseToolArgumentsSafely(String arguments) {
            if (arguments == null || arguments.isBlank()) {
                return new com.alibaba.fastjson2.JSONObject();
            }
            try {
                return JSON.parseObject(arguments);
            } catch (Exception e) {
                com.alibaba.fastjson2.JSONObject fallback = new com.alibaba.fastjson2.JSONObject();
                fallback.put("parseError", "工具调用参数不是合法的完整 JSON：" + e.getMessage());
                fallback.put("rawArguments", arguments);
                return fallback;
            }
        }

        private void thinkingHandler(PartialThinking thinking) {
            messageTypeChangedChatHistoryHandler("thinking");
            thinkingBuilder.append(thinking.text());
            //发送
            AiThinkingMessageInfo aiThinkingMessageInfo = AiThinkingMessageInfo.partial(sessionId, requestId, thinking.text());
            sendMessageToChannel(aiThinkingMessageInfo);

        }

        private void partialResponseHandler(String partialResponse) {
            messageTypeChangedChatHistoryHandler("message");
            messageBuilder.append(partialResponse);
            //发送
            AiMessageBaseInfo chunk = AiMessageBaseInfo.chunk(sessionId, requestId, partialResponse);
            sendMessageToChannel(chunk);
        }

        private boolean messageTypeChanged() {
            boolean messageTypeChanged = !lastMessageType.equals(currentMessageType);
            return messageTypeChanged;
        }

        /**
         * 处理历史消息
         *
         * @param currentMessageType
         */
        private void messageTypeChangedChatHistoryHandler(String currentMessageType) {
            this.currentMessageType = currentMessageType;
            if (messageTypeChanged()) {
                //需要处理上个类型的消息
                if (lastMessageType.equals("message")) {
                    ChatHistory textChat = new ChatHistory(agentId, "agent", "text", messageBuilder.toString());
                    textChat.setSessionId(sessionId);
                    chatHistoryConsumer.accept(textChat);
                    messageBuilder = new StringBuilder(100);
                } else if (lastMessageType.equals("thinking")) {
                    //发送
                    AiThinkingMessageInfo aiThinkingMessageInfo = AiThinkingMessageInfo.done(sessionId, requestId, "");
                    aiThinkingMessageInfo.setSessionId(sessionId);
                    sendMessageToChannel(aiThinkingMessageInfo);
                    ChatHistory textChat = new ChatHistory(agentId, "agent", "thinking", thinkingBuilder.toString());
                    textChat.setSessionId(sessionId);
                    chatHistoryConsumer.accept(textChat);
                    thinkingBuilder = new StringBuilder(100);
                }
                lastMessageType = currentMessageType;
            }

        }

        private void sendToolCallHistoryEventAndToChannel(AiToolCallMessageInfo callMessageInfo){
            sendMessageToChannel(callMessageInfo);
            //入库
            ChatHistory toolChat = new ChatHistory(
                    agentId, "agent", AiToolCallMessageInfo.TYPE_TOOL_CALL,
                    callMessageInfo.getToolCallId(),
                    callMessageInfo.getToolName(),
                    (callMessageInfo.getArguments() != null ? callMessageInfo.getArguments().toString() : null),
                    callMessageInfo.getResult() != null ? (String) callMessageInfo.getResult() : null
            );
            toolChat.setToolCallStatus(callMessageInfo.getStatus());
            toolChat.setSessionId(callMessageInfo.getSessionId());
            chatHistoryConsumer.accept(toolChat);
        }
    }

    public interface ChatAgentAssistant {
        String analyze(@dev.langchain4j.service.UserMessage List<Content> contents,
                       ChatRequestParameters chatRequestParameters, InvocationParameters invocationParameters);

        TokenStream streamingChat(@dev.langchain4j.service.UserMessage List<Content> contents,
                                  // ChatRequestParameters requestParameters, // 模型参数
                                  InvocationParameters invocationParameters);
    }
}