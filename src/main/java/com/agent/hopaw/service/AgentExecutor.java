package com.agent.hopaw.service;


import com.agent.hopaw.model.*;
import com.agent.hopaw.tools.AgentTool;
import com.agent.hopaw.util.InvocationParametersWrapper;
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
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class AgentExecutor {
    private final Logger logger = LoggerFactory.getLogger(AgentExecutor.class);
    private final Agent agent;
    private final String userId;
    private Assistant assistant = null;
    private Assistant streamingAssistant = null;
    private final AgentMessageHandler agentMessageHandler;
    private final AtomicBoolean cancelTask = new AtomicBoolean(false);
    private CountDownLatch taskLatch = new CountDownLatch(0);
    private final ChatHistoryStorageService chatHistoryStorageService;
    private final SQLiteChatMemoryStore memoryStore;
    private final java.util.concurrent.ConcurrentMap<String, AtomicBoolean> toolCancelInvocations = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, CountDownLatch> toolCancelLatch = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String,Consumer<String>> toolStopHooks = new ConcurrentHashMap<>();
    private final ChatMemoryId memoryId;
    public AgentExecutor(Agent agent, String userId,
                         ChatModel chatModel,
                         StreamingChatModel streamingModel,
                         List<AgentTool> selectedTools,
                         SQLiteChatMemoryStore memoryStore,
                         Function<Agent, String> systemMessageProvider,
                         ChatHistoryStorageService chatHistoryStorageService) {
        this.chatHistoryStorageService = chatHistoryStorageService;
        this.agentMessageHandler = new AgentMessageHandler();
        this.agent = agent;
        this.userId = userId;
        this.memoryStore = memoryStore;
        int maxMemoryRecords = agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 20;
        int maxToolInvocations = agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 10;

        memoryId = new ChatMemoryId(agent.getId(), userId);
        MessageWindowChatMemory.Builder memoryBuilder = MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMemoryRecords)
                .chatMemoryStore(memoryStore != null ? memoryStore : new InMemoryChatMemoryStore());
        var aiBuilder = AiServices
                .builder(Assistant.class)
                .systemMessageProvider(chatMemoryId -> systemMessageProvider.apply(agent))
                .chatMemory(memoryBuilder.build())
                .executeToolsConcurrently(Executors.newFixedThreadPool(5));
        if (selectedTools != null && agent.getVectorToolSearch() != null && agent.getVectorToolSearch()) {
            EmbeddingModel embeddingModel = new BgeSmallZhV15EmbeddingModel();
            int maxResults = agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 10;
            aiBuilder.toolSearchStrategy(
                    VectorToolSearchStrategy
                            .builder()
                            .embeddingModel(embeddingModel)
                            .maxResults(maxResults).build()
            );
        }
        if (!selectedTools.isEmpty()) {
            aiBuilder.maxSequentialToolsInvocations(maxToolInvocations)
                    .tools(selectedTools.toArray());
        }
        if (chatModel != null) {
            this.assistant = aiBuilder.chatModel(chatModel).build();
        }
        if (streamingModel != null) {
            this.streamingAssistant = aiBuilder.streamingChatModel(streamingModel).build();
        }
    }

    public Agent getAgent() {
        return agent;
    }

    public String getUserId() {
        return userId;
    }
    public void stop() {
        //停止所有工具
        toolCancelInvocations.values().forEach(atomicBoolean -> atomicBoolean.set(true));
        toolStopHooks.entrySet().forEach(entry -> {
            ToolCallInfo stopping = ToolCallInfo.stopping(entry.getKey());
            agentMessageHandler.getMessageConsumer().accept(JSON.toJSONString(stopping));
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
    }
    public void addToolStopHook(String callId, Consumer<String> hook){
        toolStopHooks.put(callId, hook);
        ToolCallInfo stoppable = ToolCallInfo.stoppable(callId);
        agentMessageHandler.getMessageConsumer().accept(JSON.toJSONString(stoppable));
    }
    public void stopTool(String callId) {
        //停止工具
        if(toolCancelInvocations.containsKey(callId)){
            toolCancelInvocations.get(callId).set(true);
        }
        if(toolStopHooks.containsKey(callId)) {
            Consumer<String> hook = toolStopHooks.get(callId);
            ToolCallInfo stopping = ToolCallInfo.stopping(callId);
            agentMessageHandler.getMessageConsumer().accept(JSON.toJSONString(stopping));
            hook.accept(callId);
        }
    }
    public boolean toolHaveCall(String callId) {
        return toolCancelInvocations.containsKey(callId);
    }
    public boolean toolIsCancelled(String callId) {
        return toolCancelInvocations.containsKey(callId) && toolCancelInvocations.get(callId).get();
    }
    public void sendToolRunningContent(String callId,Object resultPartial){
        ToolCallInfo toolCallInfo = ToolCallInfo.running(callId,resultPartial);
        agentMessageHandler.getMessageConsumer().accept(JSON.toJSONString(toolCallInfo));
    }


    public boolean running() {
        return taskLatch.getCount() > 0;
    }

    public String execute(List<Content> contents) {
        this.memoryStore.orphanCleanup(memoryId);
        if (assistant == null) {
            return getSimulatedResponse();
        }
        try {
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create();
            invocationParametersWrapper.setUserId(userId);
            invocationParametersWrapper.setAgentId(agent.getId());
            invocationParametersWrapper.setRequestId(UUID.randomUUID().toString());
            return assistant.chat(contents, invocationParametersWrapper.getParameters());
        } catch (Exception e) {
            return getSimulatedResponse() + "\n(注: " + e.getMessage() + ")";
        }
    }

    public void executeStreaming(List<Content> contents, Consumer<String> messageConsumer) {
        this.memoryStore.orphanCleanup(memoryId);

        List<ChatHistory> chatHistoryList = convertToChatHistory(contents);
        for (ChatHistory chatHistory : chatHistoryList) {
            chatHistory.setUserId(userId);
        }
        chatHistoryStorageService.saveChatHistoryBatch(chatHistoryList);

        cancelTask.set(false);
        agentMessageHandler.setMessageConsumer(messageConsumer);
        agentMessageHandler.setChatHistoryConsumer(chatHistory -> chatHistoryStorageService.saveChatHistory(chatHistory));

        try {
            if (streamingAssistant == null) {
                String response = execute(contents);
                agentMessageHandler.partialResponseHandler(response);
                agentMessageHandler.done();
                return;
            }
            this.taskLatch = new CountDownLatch(1);
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create();
            invocationParametersWrapper.setUserId(userId);
            invocationParametersWrapper.setAgentId(agent.getId());
            invocationParametersWrapper.setRequestId(UUID.randomUUID().toString());
            TokenStream tokenStream = streamingAssistant.streamingChat(contents, invocationParametersWrapper.getParameters())
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
                        if(!toolCancelInvocations.containsKey(toolCall.id())){
                            toolCancelInvocations.put(toolCall.id(), new AtomicBoolean(false));
                            toolCancelLatch.put(toolCall.id(), new CountDownLatch(1));
                        }
                        //logger.info("Tool call: {}", toolCall.toString());
                        agentMessageHandler.partialToolExecutionHandler(toolCall);
                        //工具或任务停止
                        if (toolCancelInvocations.get(toolCall.id()).get() || cancelTask.get()) {
                            if(toolCancelLatch.containsKey(toolCall.id())){
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
                        if(toolCancelLatch.containsKey(toolExecution.request().id())){
                            toolCancelLatch.get(toolExecution.request().id()).countDown();
                        }
                        agentMessageHandler.toolExecutionHandler(toolExecution);
                    });
            tokenStream.start();

            taskLatch.await(600, java.util.concurrent.TimeUnit.SECONDS);
            agentMessageHandler.done();
            toolCancelLatch.clear();
            toolCancelInvocations.clear();
        } catch (Exception e) {
            logger.error("\n(注: 流式响应失败: " + e.getMessage() + ")", e);
            cancelTask.set(true);
            agentMessageHandler.onErrorHandler(e);
        }
    }

    private String getSimulatedResponse() {
        return agent.getName() + ": \n这是一个模拟响应，因为API密钥未配置或请求失败。";
    }

    private List<ChatHistory> convertToChatHistory(List<Content> contents) {
        List<ChatHistory> chatHistoryList = new ArrayList<ChatHistory>();
        //todo:等支持多种消息类型后完善存储
        for (Content content : contents) {
            if (content instanceof TextContent) {
                ChatHistory userChat = new ChatHistory(agent.getId(), "user", "text", ((TextContent) content).text());
                chatHistoryList.add(userChat);
            } else if (content instanceof ImageContent) {
                ChatHistory userChat = new ChatHistory(agent.getId(), "user", "image", "[一张图片]");
                chatHistoryList.add(userChat);
            } else if (content instanceof VideoContent) {
                ChatHistory userChat = new ChatHistory(agent.getId(), "user", "video", "[一段视频]");
                chatHistoryList.add(userChat);
            } else if (content instanceof AudioContent) {
                ChatHistory userChat = new ChatHistory(agent.getId(), "user", "audio", "[一段音频]");
                chatHistoryList.add(userChat);
            } else if (content instanceof PdfFileContent) {
                ChatHistory userChat = new ChatHistory(agent.getId(), "user", "pdf", "[一个PDF文件]");
                chatHistoryList.add(userChat);
            } else {
                logger.info("用户消息 user[{}] agent[{}]: {}", userId, agent.getId(), "未知");
            }
        }
        return chatHistoryList;
    }

    /**
     * 智能体消息处理器
     */
    public class AgentMessageHandler {
        private Consumer<String> messageConsumer;
        private String lastMessageType = "";
        private String currentMessageType = "";
        private String responseId;
        private StringBuilder messageBuilder = new StringBuilder();
        private StringBuilder thinkingBuilder = new StringBuilder();

        private ToolCallInfo toolCallInfo;
        public Consumer<String> getMessageConsumer() {
            return messageConsumer;
        }

        public Consumer<ChatHistory> getChatHistoryConsumer() {
            return chatHistoryConsumer;
        }

        public String getLastMessageType() {
            return lastMessageType;
        }

        public String getCurrentMessageType() {
            return currentMessageType;
        }

        public String getResponseId() {
            return responseId;
        }

        public ToolCallInfo getToolCallInfo() {
            return toolCallInfo;
        }

        public void setMessageConsumer(Consumer<String> messageConsumer) {
            this.messageConsumer = messageConsumer == null ? (r) -> {
            } : messageConsumer;
        }

        private Consumer<ChatHistory> chatHistoryConsumer;

        public void setChatHistoryConsumer(Consumer<ChatHistory> chatHistoryConsumer) {
            this.chatHistoryConsumer = chatHistoryConsumer == null ? (h) -> {
            } : chatHistoryConsumer;
        }

        public AgentMessageHandler() {
            this.responseId = UUID.randomUUID().toString();
        }

        public void done() {
            Map<String, Object> data = new HashMap<>(3);
            data.put("type", "done");
            data.put("responseId", responseId);
            messageConsumer.accept(JSON.toJSONString(data));
            messageTypeChangedChatHistoryHandler("done");
        }

        private void onErrorHandler(Throwable ex) {
            //发送
            Map<String, Object> data = new HashMap<>(3);
            data.put("type", "error");
            data.put("content", "发生异常：" + ex.getMessage());
            data.put("responseId", responseId);
            messageConsumer.accept(JSON.toJSONString(data));
            messageTypeChangedChatHistoryHandler("error");
        }

        private void onCompleteResponseHandler(ChatResponse response) {
            //发送
            done();
        }

        private void partialToolExecutionHandler(PartialToolCall toolCall) {
            this.toolCallInfo = ToolCallInfo.preparing(
                    toolCall.id(),
                    toolCall.name(),
                    toolCall.partialArguments()
            );
            toolCallInfo.setResponseId(responseId);
            toolCallInfo.setIndex(toolCall.index());
            messageTypeChangedChatHistoryHandler("tool_call_preparing");
        }

        private void beforeToolExecutionHandler(BeforeToolExecution toolExecution) {

            this.toolCallInfo = ToolCallInfo.starting(
                    toolExecution.request().id(),
                    toolExecution.request().name(),
                    JSON.parseObject(toolExecution.request().arguments())
            );
            toolCallInfo.setResponseId(responseId);
            messageTypeChangedChatHistoryHandler("tool_call_start");
        }

        private void toolExecutionHandler(ToolExecution toolExecution) {

            this.toolCallInfo = ToolCallInfo.executed(
                    toolExecution.request().id(),
                    toolExecution.request().name(),
                    JSON.parseObject(toolExecution.request().arguments()),
                    toolExecution.result()
            );
            toolCallInfo.setResponseId(responseId);

            messageTypeChangedChatHistoryHandler("tool_call_end");
        }

        private void thinkingHandler(PartialThinking thinking) {
            messageTypeChangedChatHistoryHandler("thinking");
            thinkingBuilder.append(thinking.text());
            //发送
            ThinkingInfo thinkingInfo = ThinkingInfo.partial(thinking.text(), responseId);
            thinkingInfo.setResponseId(responseId);
            messageConsumer.accept(JSON.toJSONString(thinkingInfo));


        }

        private void partialResponseHandler(String partialResponse) {
            messageTypeChangedChatHistoryHandler("message");
            messageBuilder.append(partialResponse);
            //发送
            Map<String, Object> data = new HashMap<>(3);
            data.put("type", "chunk");
            data.put("content", partialResponse);
            data.put("responseId", responseId);
            messageConsumer.accept(JSON.toJSONString(data));
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
                    ChatHistory textChat = new ChatHistory(agent.getId(), "agent", "text", messageBuilder.toString());
                    chatHistoryConsumer.accept(textChat);
                    messageBuilder = new StringBuilder(100);
                } else if (lastMessageType.equals("thinking")) {

                    //发送
                    ThinkingInfo thinkingInfo = ThinkingInfo.done("", responseId);
                    thinkingInfo.setResponseId(responseId);
                    messageConsumer.accept(JSON.toJSONString(thinkingInfo));

                    ChatHistory textChat = new ChatHistory(agent.getId(), "agent", "thinking", thinkingBuilder.toString());
                    chatHistoryConsumer.accept(textChat);
                    thinkingBuilder = new StringBuilder(100);
                }
                lastMessageType = currentMessageType;
            }
            //开始调用 和 结束调用
            if (currentMessageType.startsWith("tool_call")) {
                messageConsumer.accept(JSON.toJSONString(toolCallInfo));
                if (currentMessageType.equals("tool_call_start") || currentMessageType.equals("tool_call_end")) {
                    //入库
                    ChatHistory toolChat = new ChatHistory(
                            agent.getId(), "agent", "tool_call",
                            toolCallInfo.getToolCallId(), toolCallInfo.getToolName(),
                            (toolCallInfo.getArguments()!=null?toolCallInfo.getArguments().toString():null), toolCallInfo.getResult() != null ? (String) toolCallInfo.getResult() : null
                    );
                    toolChat.setToolCallStatus(currentMessageType);
                    chatHistoryConsumer.accept(toolChat);
                }
            }
        }
    }

    public interface Assistant {
        String chat(@dev.langchain4j.service.UserMessage List<Content> contents,
                    // ChatRequestParameters requestParameters, // 模型参数
                    InvocationParameters invocationParameters);

        TokenStream streamingChat(@dev.langchain4j.service.UserMessage List<Content> contents,
                                  // ChatRequestParameters requestParameters, // 模型参数
                                  InvocationParameters invocationParameters);
    }

}