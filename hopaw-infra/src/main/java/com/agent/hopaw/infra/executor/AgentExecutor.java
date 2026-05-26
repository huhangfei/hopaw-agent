package com.agent.hopaw.infra.executor;


import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.model.entity.*;
import com.agent.hopaw.infra.model.dto.*;
import com.agent.hopaw.infra.monitor.LangChain4jMonitor;
import com.agent.hopaw.infra.service.AiModelService;
import com.agent.hopaw.infra.storage.ChatHistoryStore;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.message.*;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.service.tool.search.vector.VectorToolSearchStrategy;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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
    private final ChatAgentAssistant chatAgentAssistant;
    private final ChatAgentAssistant streamingChatAgentAssistant;
    private final AgentMessageHandler agentMessageHandler;
    private final AtomicBoolean cancelTask = new AtomicBoolean(false);
    private final ChatHistoryStore chatHistoryStore;
    private final IChatMemoryService memoryStore;
    private final LangChain4jMonitor langChain4jMonitor;
    private final java.util.concurrent.ConcurrentMap<String, AtomicBoolean> toolCancelInvocations = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, CountDownLatch> toolCancelLatch = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, Consumer<String>> toolStopHooks = new ConcurrentHashMap<>();
    private final ChatMemoryId memoryId;
    private final EmbeddingModel embeddingModel;
    private final ThreadPoolExecutor toolExecutor;
    private final AiModelService aiModelService;
    private CountDownLatch taskLatch = new CountDownLatch(0);
    private String requestId;

    public AgentExecutor(AgentExecutorParams agentExecutorParams,
                         IChatMemoryService memoryStore,
                         EmbeddingModel embeddingModel,
                         Function<Long, String> systemMessageProvider,
                         ChatHistoryStore chatHistoryStore,
                         AiModelService aiModelService,
                         LangChain4jMonitor langChain4jMonitor) {
        this.chatHistoryStore = chatHistoryStore;
        this.langChain4jMonitor = langChain4jMonitor;
        this.aiModelService = aiModelService;
        this.agentId = agentExecutorParams.getAgentId();
        this.userId = agentExecutorParams.getUserId();
        this.sessionId = agentExecutorParams.getSessionId();
        this.aiModelId = agentExecutorParams.getAiModelId();
        int maxMemoryRecords = agentExecutorParams.getMaxMemoryRecords() != null ? agentExecutorParams.getMaxMemoryRecords() : 20;
        int maxToolInvocations = agentExecutorParams.getMaxToolInvocations() != null ? agentExecutorParams.getMaxToolInvocations() : 10;
        this.memoryStore = memoryStore;
        this.embeddingModel = embeddingModel;
        this.agentMessageHandler = new AgentMessageHandler(this.sessionId);
        this.memoryId = new ChatMemoryId(sessionId, agentId, userId);
        // 创建工具执行线程池
        this.toolExecutor = createToolExecutor();

        MessageWindowChatMemory.Builder memoryBuilder = MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMemoryRecords)
                .chatMemoryStore(memoryStore != null ? memoryStore : new InMemoryChatMemoryStore());
        var aiBuilder = AiServices
                .builder(ChatAgentAssistant.class)
                .systemMessageProvider(chatMemoryId -> systemMessageProvider.apply(agentId))
                .chatMemory(memoryBuilder.build())
                .executeToolsConcurrently(toolExecutor);

        ChatModel chatModel = aiModelService.createChatModel(agentExecutorParams.getAiModelId(), agentExecutorParams.getEnableThinking(), this.langChain4jMonitor);
        StreamingChatModel streamingModel = aiModelService.createStreamingChatModel(agentExecutorParams.getAiModelId(), agentExecutorParams.getEnableThinking(), this.langChain4jMonitor);

        List<AgentTool> selectedTools = agentExecutorParams.getToolSets();
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
            if (maxToolInvocations > 0) {
                aiBuilder.maxSequentialToolsInvocations(maxToolInvocations);
            }
            aiBuilder.tools(selectedTools.toArray());
        }
        if (chatModel != null) {
            this.chatAgentAssistant = aiBuilder.chatModel(chatModel).build();
        } else {
            this.chatAgentAssistant = null;
        }
        if (streamingModel != null) {
            this.streamingChatAgentAssistant = aiBuilder.streamingChatModel(streamingModel).build();
        } else {
            this.streamingChatAgentAssistant = null;
        }
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

    @Override
    public void stop() {
        //停止所有工具
        toolCancelInvocations.values().forEach(atomicBoolean -> atomicBoolean.set(true));
        toolStopHooks.entrySet().forEach(entry -> {
            AiToolCallMessageInfo stopping = AiToolCallMessageInfo.stopping(sessionId, requestId, entry.getKey());
            agentMessageHandler.sendMessageToChannel(stopping);
            entry.getValue().accept(entry.getKey());
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
    }

    @Override
    public void addToolStopHook(String callId, Consumer<String> hook) {
        toolStopHooks.put(callId, hook);
        AiToolCallMessageInfo stoppable = AiToolCallMessageInfo.stoppable(sessionId, requestId, callId);
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
            AiToolCallMessageInfo stopping = AiToolCallMessageInfo.stopping(sessionId, requestId, callId);
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
        AiToolCallMessageInfo aiToolCallMessageInfo = AiToolCallMessageInfo.running(sessionId, requestId, callId, resultPartial);
        agentMessageHandler.sendMessageToChannel(aiToolCallMessageInfo);
    }

    @Override
    public boolean running() {
        return taskLatch.getCount() > 0;
    }

    @Override
    public String execute(UserRequest userRequest) {
        try {
            List<Content> contents = new ArrayList<>();
            contents.add(new TextContent(userRequest.getMessage()));
            this.requestId = UUID.randomUUID().toString();
            this.memoryStore.orphanCleanup(memoryId);
            if (chatAgentAssistant == null) {
                return getSimulatedResponse();
            }
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
                    .setUserId(userId)
                    .setAgentId(agentId)
                    .setSessionId(sessionId)
                    .setRequestId(requestId);
            return chatAgentAssistant.chat(contents, invocationParametersWrapper.getParameters());
        } catch (Exception e) {
            return getSimulatedResponse() + "\n(注: " + e.getMessage() + ")";
        }
    }

    @Override
    public void executeStreaming(UserRequest userRequest, BiConsumer<String, String> messageConsumer) {
        try {
            List<Content> contents = new ArrayList<>();
            contents.add(new TextContent(userRequest.getMessage()));
            this.requestId = UUID.randomUUID().toString();
            this.memoryStore.orphanCleanup(memoryId);
            List<ChatHistory> chatHistoryList = convertToChatHistory(contents);
            for (ChatHistory chatHistory : chatHistoryList) {
                chatHistory.setUserId(userId);
            }
            chatHistoryStore.saveChatHistoryBatch(chatHistoryList);
            this.cancelTask.set(false);
            this.taskLatch = new CountDownLatch(1);
            agentMessageHandler.setMessageConsumer(messageConsumer);
            agentMessageHandler.setChatHistoryConsumer(chatHistory -> chatHistoryStore.saveChatHistory(chatHistory));

            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
                    .setUserId(userId)
                    .setAgentId(agentId)
                    .setSessionId(sessionId)
                    .setRequestId(requestId);
            TokenStream tokenStream = streamingChatAgentAssistant.streamingChat(contents, invocationParametersWrapper.getParameters())
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
                        //logger.info("Tool call: {}", toolCall.toString());
                        agentMessageHandler.partialToolExecutionHandler(toolCall);
                        //工具或任务停止
                        if (toolCancelInvocations.get(toolCall.id()).get() || cancelTask.get()) {
                            if (toolCancelLatch.containsKey(toolCall.id())) {
                                toolCancelLatch.get(toolCall.id()).countDown();
                            }
                            ctx.streamingHandle().cancel(); // ✅ 真正中断：关闭流、停止LLM、省token
                            agentMessageHandler.done();
                            taskLatch.countDown();
                            return;
                        }
                    })
                    .beforeToolExecution(toolExecution -> {
                        toolExecution.invocationContext().invocationParameters().put("toolCallId", toolExecution.request().id());
                        // 任务开始
                        agentMessageHandler.beforeToolExecutionHandler(toolExecution);
                    })
                    .onToolExecuted(toolExecution -> {
                        //任务完成
                        if (toolCancelLatch.containsKey(toolExecution.request().id())) {
                            toolCancelLatch.get(toolExecution.request().id()).countDown();
                        }
                        if (toolStopHooks.containsKey(toolExecution.request().id())) {
                            toolStopHooks.remove(toolExecution.request().id());
                        }
                        if (toolCancelInvocations.containsKey(toolExecution.request().id())) {
                            toolCancelInvocations.remove(toolExecution.request().id());
                        }
                        agentMessageHandler.toolExecutionHandler(toolExecution);
                    });
            tokenStream.start();

            taskLatch.await(600, java.util.concurrent.TimeUnit.SECONDS);
            agentMessageHandler.taskDone();
            toolCancelLatch.clear();
            toolCancelInvocations.clear();
        } catch (Exception e) {
            logger.error("\n(注: 流式响应失败: " + e.getMessage() + ")\n可以尝试清理对话或强停试试。", e);
            cancelTask.set(true);
            toolCancelLatch.values().forEach(latch -> latch.countDown());
            toolCancelInvocations.clear();
            toolStopHooks.clear();
            taskLatch.countDown();
            agentMessageHandler.onErrorHandler(e);
        }
    }

    private String getSimulatedResponse() {
        return "这是一个模拟响应，因为API密钥未配置或请求失败。";
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
                ChatHistory userChat = new ChatHistory(agentId, "user", "image", "[一张图片]");
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
        private String lastMessageType = "";
        private String currentMessageType = "";
        private StringBuilder messageBuilder = new StringBuilder();
        private StringBuilder thinkingBuilder = new StringBuilder();
        private AiToolCallMessageInfo aiToolCallMessageInfo;
        private BiConsumer<String, String> messageConsumer;
        private Consumer<ChatHistory> chatHistoryConsumer;
        private String requestId;

        public AgentMessageHandler(String sessionId) {
            this.sessionId = sessionId;
        }

        public BiConsumer<String, String> getMessageConsumer() {
            return messageConsumer;
        }

        public void setMessageConsumer(BiConsumer<String, String> messageConsumer) {
            this.messageConsumer = messageConsumer == null ? (sId, r) -> {
            } : messageConsumer;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public void setChatHistoryConsumer(Consumer<ChatHistory> chatHistoryConsumer) {
            this.chatHistoryConsumer = chatHistoryConsumer == null ? (h) -> {
            } : chatHistoryConsumer;
        }

        public void sendMessageToChannel(Object message) {
            messageConsumer.accept(sessionId, JSON.toJSONString(message));
        }

        public void done() {
            Map<String, Object> data = new HashMap<>(3);
            data.put("type", "done");
            data.put("sessionId", sessionId);
            data.put("requestId", requestId);
            sendMessageToChannel(data);
            messageTypeChangedChatHistoryHandler("done");
        }

        public void taskDone() {
            Map<String, Object> data = new HashMap<>(3);
            data.put("type", "task-done");
            data.put("sessionId", sessionId);
            data.put("requestId", requestId);
            sendMessageToChannel(data);
            messageTypeChangedChatHistoryHandler("task-done");
        }

        private void onErrorHandler(Throwable ex) {
            //发送
            Map<String, Object> data = new HashMap<>(3);
            data.put("type", "error");
            data.put("content", "发生异常：" + ex.getMessage());
            data.put("sessionId", sessionId);
            data.put("requestId", requestId);
            sendMessageToChannel(data);
            messageTypeChangedChatHistoryHandler("error");
        }

        private void onCompleteResponseHandler(ChatResponse response) {
            //发送
            done();
        }

        private void partialToolExecutionHandler(PartialToolCall toolCall) {
            this.aiToolCallMessageInfo = AiToolCallMessageInfo.preparing(sessionId, requestId,
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.partialArguments(),
                    toolCall.index()
            );
            messageTypeChangedChatHistoryHandler("tool_call_preparing");
        }

        private void beforeToolExecutionHandler(BeforeToolExecution toolExecution) {

            this.aiToolCallMessageInfo = AiToolCallMessageInfo.starting(sessionId, requestId,
                    toolExecution.request().id(),
                    toolExecution.request().name(),
                    JSON.parseObject(toolExecution.request().arguments())
            );
            messageTypeChangedChatHistoryHandler("tool_call_start");
        }

        private void toolExecutionHandler(ToolExecution toolExecution) {

            this.aiToolCallMessageInfo = AiToolCallMessageInfo.executed(sessionId, requestId,
                    toolExecution.request().id(),
                    toolExecution.request().name(),
                    JSON.parseObject(toolExecution.request().arguments()),
                    toolExecution.result()
            );
            messageTypeChangedChatHistoryHandler("tool_call_end");
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
            Map<String, Object> data = new HashMap<>(3);
            data.put("type", "chunk");
            data.put("content", partialResponse);
            data.put("responseId", sessionId);
            sendMessageToChannel(data);
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
            //开始调用 和 结束调用
            if (currentMessageType.startsWith("tool_call")) {
                sendMessageToChannel(aiToolCallMessageInfo);
                if (currentMessageType.equals("tool_call_start") || currentMessageType.equals("tool_call_end")) {
                    //入库
                    ChatHistory toolChat = new ChatHistory(
                            agentId, "agent", "tool_call",
                            aiToolCallMessageInfo.getToolCallId(), aiToolCallMessageInfo.getToolName(),
                            (aiToolCallMessageInfo.getArguments() != null ? aiToolCallMessageInfo.getArguments().toString() : null), aiToolCallMessageInfo.getResult() != null ? (String) aiToolCallMessageInfo.getResult() : null
                    );
                    toolChat.setToolCallStatus(currentMessageType);
                    toolChat.setSessionId(sessionId);
                    chatHistoryConsumer.accept(toolChat);
                }
            }
        }
    }

    public interface ChatAgentAssistant {
        String chat(@dev.langchain4j.service.UserMessage List<Content> contents,
                    // ChatRequestParameters requestParameters, // 模型参数
                    InvocationParameters invocationParameters);

        TokenStream streamingChat(@dev.langchain4j.service.UserMessage List<Content> contents,
                                  // ChatRequestParameters requestParameters, // 模型参数
                                  InvocationParameters invocationParameters);

        //分析用户意图
        String analyzeUserIntent(@dev.langchain4j.service.UserMessage List<Content> contents);
    }

}