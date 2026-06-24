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
    private final Map<String, ToolInfo> toolInfoMap = new HashMap<>();
    private final ChatMemoryId memoryId;
    private final EmbeddingModel embeddingModel;
    private final ThreadPoolExecutor toolExecutor;
    private final AiModelService aiModelService;
    private CountDownLatch taskLatch = new CountDownLatch(0);
    private String requestId;
    private final ApplicationEventPublisher eventPublisher;
    private final List<Content> contents;
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
        this.contents = agentExecutorParams.getContents();
        this.sessionId = agentExecutorParams.getSessionId() != null ? agentExecutorParams.getSessionId() : UuidUtil.generateSimpleUUID();
        this.requestId = UuidUtil.generateSimpleUUID();

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
        toolCancelLatch.values().forEach(countDownLatch -> {
            try {
                countDownLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.error("Tool cancellation latch await interrupted", e);
            }
        });

        //停止任务
        cancelTask.set(true);

        try {
            taskLatch.await(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ex) {
            logger.error("Task latch await interrupted", ex);
        }
        if (!running()) {
            agentMessageHandler.done();
        }

        // 关闭工具执行线程池，释放资源
        if (toolExecutor != null && !toolExecutor.isShutdown()) {
            toolExecutor.shutdown();
            try {
                if (!toolExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    toolExecutor.shutdownNow();
                    logger.warn("Tool executor force shutdown");
                } else {
                    logger.info("Tool executor shutdown gracefully");
                }
            } catch (InterruptedException e) {
                toolExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
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
            AgentMessageEvent event=new AgentMessageEvent(userId, agentId, message);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.error("sendFirstState error", e);
        }
    }
    @Override
    public boolean running() {
        return taskLatch.getCount() > 0;
    }

    @Override
    public void execute() {
        try {
            sendFirstState();
            saveChatSession();
            saveChatHistory();

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
                        agentMessageHandler.onErrorHandler(e);
                        taskLatch.countDown();
                    }).onCompleteResponse(response -> {
                        agentMessageHandler.onCompleteResponseHandler(response);
                        taskLatch.countDown();
                    }).onPartialResponseWithContext((r, ctx) -> {
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
                        String toolCallId = toolExecution.request().id();
                        String toolName = toolExecution.request().name();
                        String arguments = toolExecution.request().arguments();


                        ToolInfo toolInfo = toolInfoMap.getOrDefault(toolName, null);
                        ToolSecurityLevel.Level toolLevel = toolInfo==null? ToolSecurityLevel.Level.ALL_REQUIRE_APPROVAL:toolInfo.getSecurityLevel();

                        toolExecution.invocationContext().invocationParameters().put("toolCallId", toolCallId);
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
                            agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_STARTING, toolCallInfo.id(),toolCallInfo.name(),toolCallInfo.arguments(),null);
                        }else{
                            //拒绝执行
                            agentMessageHandler.toolCallHandler(AiToolCallMessageInfo.STATUS_REJECTED, toolCallInfo.id(),toolCallInfo.name(),toolCallInfo.arguments(),"用户拒绝了工具调用");
                            throw new ToolCallRejectedException("用户拒绝了工具调用");
                        }
                    })
                    .onToolExecuted(toolExecution -> {
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
            taskLatch.await(600, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("\n(注: 流式响应失败: " + e.getMessage() + ")\n可以尝试清理对话或强停试试。", e);
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

    private String analyzeUserIntent() {
        try {
            ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(AiModelCallSourceEnum.ChatAnalyzeUserIntent, sessionId, userId, agentId);

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
            ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(AiModelCallSourceEnum.ChatToolCallCheck, sessionId, userId, agentId);
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
                .executeToolsConcurrently(toolExecutor);
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
                try {
                    McpTransport transport = buildMcpTransport(config);
                    McpClient mcpClient = DefaultMcpClient.builder()
                            .key(config.getName())
                            .transport(transport)
                            .build();
                    clients.add(mcpClient);
                    logger.info("MCP client created: {}", config.getName());
                } catch (Exception e) {
                    logger.error("Failed to create MCP client for {}: {}", config.getName(), e.getMessage());
                }
            }
            if (!clients.isEmpty()) {
                ToolProvider toolProvider = McpToolProvider.builder()
                        .mcpClients(clients)
                        .failIfOneServerFails(false)
                        .build();
                aiBuilder.toolProvider(toolProvider);
                mcpClients.addAll(clients);
            }
        }

        ChatModelListener chatModelListener = chatModelListenerProvider.getChatModelListener(AiModelCallSourceEnum.Chat, sessionId, userId, agentId);
        StreamingChatModel streamingModel = aiModelService.createStreamingChatModel(agentExecutorParams.getAiModelId(), agentExecutorParams.getEnableThinking(), chatModelListener);
        return aiBuilder.streamingChatModel(streamingModel).build();
    }

    /**
     * 根据 MCP 配置构建对应的传输层
     */
    private McpTransport buildMcpTransport(McpServerConfig config) {
        String transportType = config.getTransportType();
        if ("http".equalsIgnoreCase(transportType)) {
            return StreamableHttpMcpTransport.builder()
                    .url(config.getUrl())
                    .build();
        } else {
            // 默认使用 stdio
            List<String> commandParts = parseCommand(config.getCommand());
            return StdioMcpTransport.builder()
                    .command(commandParts)
                    .build();
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

    private void saveChatSession() {


        boolean sendSessionTitle = false;
        ChatSession chatSession=chatSessionService.getSessionBySessionId(sessionId);
        if (chatSession == null) {
            chatSession = new ChatSession();
            String userIntent = analyzeUserIntent();
            if (userIntent == null) {
                userIntent = "新任务";
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
            chatSessionService.insertSession(chatSession);
            agentMessageHandler.sendMessageToChannel(AiMessageBaseInfo.sessionTitle(sessionId, requestId, userIntent));
        } else {
            if(chatSession.getTitle().equals("新任务")){
                String userIntent = analyzeUserIntent();
                if (userIntent !=null) {
                    chatSession.setTitle(userIntent);
                    sendSessionTitle=true;
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
            chatSessionService.updateSession(chatSession);
        }
        if(sendSessionTitle){
            agentMessageHandler.sendMessageToChannel(AiMessageBaseInfo.sessionTitle(sessionId, requestId, chatSession.getTitle()));
        }

    }

    private void saveChatHistory() {
        List<ChatHistory> chatHistoryList = convertToChatHistory(contents);
        for (ChatHistory chatHistory : chatHistoryList) {
            chatHistory.setUserId(userId);
            eventPublisher.publishEvent(new ChatHistoryEvent(chatHistory));
        }
    }

    private List<ChatHistory> convertToChatHistory(List<Content> contents) {
        List<ChatHistory> chatHistoryList = new ArrayList<ChatHistory>();
        //todo:等支持多种消息类型后完善存储
        for (Content content : contents) {
            if (content instanceof TextContent) {
                ChatHistory userChat = new ChatHistory(agentId, "user", "text", ((TextContent) content).text());
                userChat.setSessionId(sessionId);
                chatHistoryList.add(userChat);
            } else if (content instanceof ImageContent) {
                ImageContent image=(ImageContent)content;
                ChatHistory userChat = new ChatHistory(agentId, "user", "image", "data:"+image.image().mimeType()+";base64,"+image.image().base64Data());
                userChat.setSessionId(sessionId);
                chatHistoryList.add(userChat);
            } else if (content instanceof VideoContent) {
                ChatHistory userChat = new ChatHistory(agentId, "user", "video", "[一段视频]");
                userChat.setSessionId(sessionId);
                chatHistoryList.add(userChat);
            } else if (content instanceof AudioContent) {
                ChatHistory userChat = new ChatHistory(agentId, "user", "audio", "[一段音频]");
                userChat.setSessionId(sessionId);
                chatHistoryList.add(userChat);
            } else if (content instanceof PdfFileContent) {
                ChatHistory userChat = new ChatHistory(agentId, "user", "pdf", "[一个PDF文件]");
                userChat.setSessionId(sessionId);
                chatHistoryList.add(userChat);
            } else {
                logger.info("用户消息 user[{}] agent[{}]: {}", userId, agentId, "未知");
            }
        }
        return chatHistoryList;
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
                    JSON.parseObject(arguments),
                    result,
                    toolDescriptions
            );
            sendToolCallHistoryEventAndToChannel(toolCallMessageInfo);
            messageTypeChangedChatHistoryHandler(AiToolCallMessageInfo.TYPE_TOOL_CALL+"_"+status);
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