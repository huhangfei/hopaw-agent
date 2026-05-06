package com.agent.hopaw.service;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.*;
import com.agent.hopaw.tools.AgentTool;
import com.agent.hopaw.util.InvocationParametersUtil;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.RequestEntity.put;

@Service
public class AgentService {
    private final static Logger logger = LoggerFactory.getLogger(AgentService.class);
    private final Map<String, AgentExecutor> agentExecutors = new HashMap<>();
    private final AgentMapper agentMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final List<AgentTool> allTools;
    private final LongTermMemoryService longTermMemoryService;
    private final AiModelService aiModelService;
    private final ChatHistoryStorageService chatHistoryStorageService;
    public AgentService(AgentMapper agentMapper, ChatMemoryMapper chatMemoryMapper,
                        List<AgentTool> allTools, LongTermMemoryService longTermMemoryService, AiModelService aiModelService, ChatHistoryStorageService chatHistoryStorageService) {
        this.agentMapper = agentMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.allTools = allTools;
        this.longTermMemoryService = longTermMemoryService;
        this.aiModelService = aiModelService;
        this.chatHistoryStorageService = chatHistoryStorageService;
    }

    public List<Agent> getAllAgents() {
        return agentMapper.findAll();
    }

    public Agent getAgentById(Long id) {
        return agentMapper.findById(id);
    }

    public Agent createAgent(String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, String userId) {
        Agent agent = new Agent(name, description, tools, maxMemoryRecords, maxToolInvocations, enableThinking);
        agent.setAiModelId(aiModelId);
        agent.setEnableThinking(enableThinking);
        agent.setUserId(userId);
        agentMapper.insert(agent);
        return agent;
    }

    public void deleteAgent(Long id,String userId) {
        agentMapper.deleteById(id);
        chatMemoryMapper.deleteByAgentId(id);
        stopAndRemoveAgentExecutor(id,userId);
    }

    public void updateAgent(String userId,Long id, String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setName(name);
            agent.setDescription(description);
            agent.setTools(tools);
            agent.setMaxMemoryRecords(maxMemoryRecords);
            agent.setMaxToolInvocations(maxToolInvocations);
            agent.setAiModelId(aiModelId);
            if (enableThinking != null) {
                agent.setEnableThinking(enableThinking);
            }
            agentMapper.update(agent);
            stopAndRemoveAgentExecutor(id,userId);
        }
    }

    public AgentExecutor getAgentExecutor(Long agentId,String userId) {
        return agentExecutors.computeIfAbsent(agentId+"_"+userId, id -> {
            Agent agent = agentMapper.findById(agentId);
            if (agent == null) {
                return null;
            }
            return createAgentExecutor(agent,userId);
        });
    }

    public void stopAgentExecutor(Long agentId,String userId) {
        AgentExecutor agentExecutor = agentExecutors.get(agentId+"_"+userId);
        if (agentExecutor != null) {
            agentExecutor.stop();
        }
    }

    public void updateThinking(Long id, Boolean enabled,String userId) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setEnableThinking(enabled);
            agentMapper.update(agent);
            stopAndRemoveAgentExecutor(id,userId);
        }
    }

    public void stopAndRemoveAgentExecutor(Long agentId,String userId) {
        stopAgentExecutor(agentId,userId);
        agentExecutors.remove(agentId.toString());
    }

    public boolean isAgentExecutorRunning(Long agentId,String userId) {
        AgentExecutor agentExecutor = agentExecutors.get(agentId+"_"+userId);
        return agentExecutor != null && agentExecutor.running();
    }

    private AgentExecutor createAgentExecutor(Agent agent,String userId) {
        ChatModel chatModel = null;
        StreamingChatModel streamingModel = null;
        Map<String, String> metadata=new HashMap<>(){{
           put("agentId",agent.getId().toString());
           put("userId",userId);
           put("source","chat");
        }};

        chatModel = aiModelService.createChatModel(agent.getAiModelId(), agent.getEnableThinking(),metadata);
        streamingModel = aiModelService.createStreamingChatModel(agent.getAiModelId(), agent.getEnableThinking(),metadata);
        List<String> selectedToolNames = parseToolNames(agent.getTools());
        List<AgentTool> selectedTools = allTools.stream()
                .filter(t -> selectedToolNames.contains(t.getName()))
                .collect(Collectors.toList());

        SQLiteChatMemoryStore memoryStore = new SQLiteChatMemoryStore(chatMemoryMapper, agent.getId(), userId);
        return new AgentExecutor(agent,userId, chatModel, streamingModel, selectedTools, memoryStore, a->this.getSystemMessage(a,userId), chatHistoryStorageService);
    }

    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(toolsStr.split(","));
    }

    public interface Assistant {
        String chat(@dev.langchain4j.service.UserMessage List<Content> contents,
                   // ChatRequestParameters requestParameters, // 模型参数
                    InvocationParameters invocationParameters);

        TokenStream streamingChat(@dev.langchain4j.service.UserMessage List<Content> contents,
                                 // ChatRequestParameters requestParameters, // 模型参数
                                  InvocationParameters invocationParameters);
    }

    public String getSystemMessage(Agent agent,String userId){
        String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "," +
                "主要工作是" + agent.getDescription() + "," +
                "你的agentId是" + agent.getId() + "。" +
                "在遇到需要用户提供信息或最新信息不正确的时候，不要一直猜，先查询记忆，记忆中没有就问用户。"+
                "在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作，询问用户。\n";
                String rootMemory = longTermMemoryService.getRootMemory(String.valueOf(agent.getId()),userId);
        if(StringUtils.hasLength(rootMemory)){
            systemMessage+="这是所有记忆分类：\n" + rootMemory+"\n如果需要详细的记忆内容可以根据记忆编号查询所有子记忆。";
        }
        return systemMessage;
    }

    public static class AgentExecutor {
        private final Agent agent;
        private final String userId;
        private final Assistant assistant;
        private final Assistant streamingAssistant;
        private final AgentMessageHandler agentMessageHandler;
        private final AtomicBoolean cancelTask = new AtomicBoolean(false);
        private final Function<Agent, String> systemMessageProvider;
        CountDownLatch latch = new CountDownLatch(0);
        private final ChatHistoryStorageService chatHistoryStorageService;
        public AgentExecutor(Agent agent,String userId,
                             ChatModel chatModel,
                             StreamingChatModel streamingModel,
                             List<AgentTool> selectedTools,
                             SQLiteChatMemoryStore memoryStore,
                             Function<Agent, String> systemMessageProvider, ChatHistoryStorageService chatHistoryStorageService) {
            this.systemMessageProvider = systemMessageProvider;
            this.chatHistoryStorageService = chatHistoryStorageService;
            this.agentMessageHandler = new AgentMessageHandler();
            this.agent = agent;
            this.userId=userId;
            int maxMemoryRecords = agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 20;
            int maxToolInvocations = agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 10;

            String memoryId="window_"+agent.getId()+"_"+userId;
            if (chatModel != null) {

                var aiBuilder = AiServices.builder(Assistant.class)
                        .chatModel(chatModel)
                        .systemMessageProvider(chatMemoryId -> systemMessageProvider.apply(agent));

                MessageWindowChatMemory.Builder memoryBuilder = MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(maxMemoryRecords);
                if (memoryStore != null) {
                    memoryBuilder.chatMemoryStore(memoryStore);
                }else {
                    memoryBuilder.chatMemoryStore(new InMemoryChatMemoryStore());
                }
                aiBuilder.chatMemory(memoryBuilder.build());
                if (!selectedTools.isEmpty()) {
                    aiBuilder.maxSequentialToolsInvocations(maxToolInvocations)
                            .tools(selectedTools.toArray());
                }
                this.assistant = aiBuilder.build();
            } else {
                this.assistant = null;
            }
            if (streamingModel != null) {
                var streamBuilder = AiServices.builder(Assistant.class)
                        .streamingChatModel(streamingModel)
                        .systemMessageProvider(chatMemoryId -> systemMessageProvider.apply(agent));
                MessageWindowChatMemory.Builder memoryBuilder = MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(maxMemoryRecords);
                if (memoryStore != null) {
                    memoryBuilder.chatMemoryStore(memoryStore);
                }else {
                    memoryBuilder.chatMemoryStore(new InMemoryChatMemoryStore());
                }
                streamBuilder.chatMemory(memoryBuilder.build());

                if (!selectedTools.isEmpty()) {
                    streamBuilder
                            .maxSequentialToolsInvocations(maxToolInvocations)
                            .tools(selectedTools.toArray());
                }
                this.streamingAssistant = streamBuilder.build();
            } else {
                this.streamingAssistant = null;
            }
        }


        public void stop(){
            cancelTask.set(true);
            if(!running()){
                agentMessageHandler.done();
            }
        }

        public boolean running(){
            return latch.getCount()>0;
        }

        public String execute(List<Content> contents) {
            if (assistant == null) {
                return getSimulatedResponse();
            }
            try {
                String memoryId=agent.getId()+"_"+userId;
                InvocationParameters invocationParameters = InvocationParameters.from(new HashMap<>());
                InvocationParametersUtil.setUserId(invocationParameters, userId);
                InvocationParametersUtil.setAgentId(invocationParameters, String.valueOf(agent.getId()));
                InvocationParametersUtil.setMemoryId(invocationParameters, memoryId);
                return assistant.chat(contents,invocationParameters);
            } catch (Exception e) {
                return getSimulatedResponse() + "\n(注: " + e.getMessage() + ")";
            }
        }

        public void executeStreaming(List<Content> contents, Consumer<String> messageConsumer) {
            List<ChatHistory> chatHistoryList = new ArrayList<ChatHistory>();
            //todo:等支持多种消息类型后完善存储
            for (Content content : contents) {
                if(content instanceof TextContent){
                    ChatHistory userChat = new ChatHistory(agent.getId(), "user", "text", ((TextContent)content).text());
                    chatHistoryList.add(userChat);
                }else  if(content instanceof ImageContent){
                    ChatHistory userChat = new ChatHistory(agent.getId(), "user", "image", "[一张图片]");
                    chatHistoryList.add(userChat);
                }else  if(content instanceof VideoContent){
                    ChatHistory userChat = new ChatHistory(agent.getId(), "user", "video", "[一段视频]");
                    chatHistoryList.add(userChat);
                }else  if(content instanceof AudioContent){
                    ChatHistory userChat = new ChatHistory(agent.getId(), "user", "audio", "[一段音频]");
                    chatHistoryList.add(userChat);
                }else  if(content instanceof PdfFileContent){
                    ChatHistory userChat = new ChatHistory(agent.getId(), "user", "pdf", "[一个PDF文件]");
                    chatHistoryList.add(userChat);
                }else{
                    logger.info("用户消息 user[{}] agent[{}]: {}", userId,agent.getId(),"未知");
                }
            }
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
                this.latch = new CountDownLatch(1);
                String memoryId=agent.getId()+"_"+userId;
                InvocationParameters invocationParameters = InvocationParameters.from(new HashMap<>());
                InvocationParametersUtil.setUserId(invocationParameters, userId);
                InvocationParametersUtil.setAgentId(invocationParameters, String.valueOf(agent.getId()));
                InvocationParametersUtil.setMemoryId(invocationParameters, memoryId);

                TokenStream tokenStream = streamingAssistant.streamingChat(contents,invocationParameters)
                        .onError(e -> {
                            agentMessageHandler.onErrorHandler(e);
                            latch.countDown();
                        }).onCompleteResponse(response -> {
                            agentMessageHandler.onCompleteResponseHandler(response);
                            latch.countDown();
                        }).onPartialResponseWithContext((r, ctx) -> {
                            if (cancelTask.get()) {
                                agentMessageHandler.partialResponseHandler(r.text());
                                agentMessageHandler.done();
                                ctx.streamingHandle().cancel(); // ✅ 真正中断：关闭流、停止LLM、省token
                                latch.countDown();
                                return;
                            }
                            agentMessageHandler.partialResponseHandler(r.text());
                        })
                        .onPartialThinkingWithContext((thinking,ctx) -> {
                            if (cancelTask.get()) {
                                agentMessageHandler.thinkingHandler(thinking);
                                agentMessageHandler.done();
                                ctx.streamingHandle().cancel(); // ✅ 真正中断：关闭流、停止LLM、省token
                                latch.countDown();
                                return;
                            }
                            agentMessageHandler.thinkingHandler(thinking);
                        })
                        .onPartialToolCallWithContext((toolCall, ctx) -> {
                            //logger.info("Tool call: {}", toolCall.toString());
                            if (cancelTask.get()) {
                                agentMessageHandler.done();
                                ctx.streamingHandle().cancel(); // ✅ 真正中断：关闭流、停止LLM、省token
                                latch.countDown();
                                return;
                            }
                        })
                        .beforeToolExecution(toolExecution -> agentMessageHandler.beforeToolExecutionHandler(toolExecution))
                        .onToolExecuted(toolExecution -> agentMessageHandler.toolExecutionHandler(toolExecution));
                tokenStream.start();

                latch.await(60, TimeUnit.SECONDS);
                agentMessageHandler.done();
            } catch (Exception e) {
                logger.error("\n(注: 流式响应失败: " + e.getMessage() + ")",e);
                agentMessageHandler.onErrorHandler(e);
            }
        }

        private String getSimulatedResponse() {
            return agent.getName() + ": \n这是一个模拟响应，因为API密钥未配置或请求失败。";
        }

        /**
         * 智能体消息处理器
         */
        public class AgentMessageHandler {
            private Consumer<String> messageConsumer;
            public void setMessageConsumer(Consumer<String> messageConsumer) {
                this.messageConsumer = messageConsumer==null?(r)->{}:messageConsumer;
            }
            private Consumer<ChatHistory> chatHistoryConsumer;
            public void setChatHistoryConsumer(Consumer<ChatHistory> chatHistoryConsumer) {
                this.chatHistoryConsumer = chatHistoryConsumer==null?(h)->{}:chatHistoryConsumer;
            }

            private String lastMessageType="";
            private String currentMessageType="";
            private String responseId;
            private StringBuilder messageBuilder = new StringBuilder();
            private StringBuilder thinkingBuilder = new StringBuilder();
            private ToolCallInfo toolCallInfo;
            public AgentMessageHandler() {
                this.responseId = UUID.randomUUID().toString();
            }
            public void done(){
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
                data.put("content", "发生异常："+ex.getMessage());
                data.put("responseId", responseId);
                messageConsumer.accept(JSON.toJSONString(data));
                messageTypeChangedChatHistoryHandler("error");
            }
            private void onCompleteResponseHandler(ChatResponse response) {
                //发送
                done();
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
             * @param currentMessageType
             */
            private void messageTypeChangedChatHistoryHandler(String currentMessageType) {
                this.currentMessageType=currentMessageType;

                if(messageTypeChanged()){
                    //需要处理上个类型的消息
                    if(lastMessageType.equals("message")){
                        ChatHistory textChat = new ChatHistory(agent.getId(), "agent", "text", messageBuilder.toString());
                        chatHistoryConsumer.accept(textChat);
                        messageBuilder=new StringBuilder(100);
                    }else if(lastMessageType.equals("thinking")){

                        //发送
                        ThinkingInfo thinkingInfo = ThinkingInfo.done("", responseId);
                        thinkingInfo.setResponseId(responseId);
                        messageConsumer.accept(JSON.toJSONString(thinkingInfo));

                        ChatHistory textChat = new ChatHistory(agent.getId(), "agent", "thinking", thinkingBuilder.toString());
                        chatHistoryConsumer.accept(textChat);
                        thinkingBuilder=new StringBuilder(100);
                    }
                    lastMessageType=currentMessageType;
                }
                //开始调用 和 结束调用
                if(currentMessageType.equals("tool_call_start") || currentMessageType.equals("tool_call_end")){
                    messageConsumer.accept(JSON.toJSONString(toolCallInfo));
                    //入库
                    ChatHistory toolChat = new ChatHistory(
                            agent.getId(), "agent", "tool_call",
                            toolCallInfo.getToolCallId(), toolCallInfo.getToolName(),
                            toolCallInfo.getArguments().toString(), toolCallInfo.getResult()!=null? (String) toolCallInfo.getResult() : null
                    );
                    toolChat.setToolCallStatus(currentMessageType);
                    chatHistoryConsumer.accept(toolChat);
                }
            }
        }
    }


}
