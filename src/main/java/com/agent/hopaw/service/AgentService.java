package com.agent.hopaw.service;

import com.agent.hopaw.config.ChatModelFactoryConfig;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.*;
import com.agent.hopaw.tools.AgentTool;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.BeforeToolExecution;
import dev.langchain4j.service.tool.ToolExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AgentService {
    private final static Logger logger = LoggerFactory.getLogger(AgentService.class);
    private final Map<String, AgentExecutor> agentExecutors = new HashMap<>();
    private final AgentMapper agentMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final List<AgentTool> allTools;
    private final ChatModelFactory chatModelFactory;

    public AgentService(AgentMapper agentMapper, ChatMemoryMapper chatMemoryMapper,
                       List<AgentTool> allTools, ChatModelFactoryConfig chatModelFactoryConfig) {
        this.agentMapper = agentMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.allTools = allTools;
        this.chatModelFactory = chatModelFactoryConfig.getFactory();
    }

    public List<Agent> getAllAgents() {
        return agentMapper.findAll();
    }

    public Agent getAgentById(Long id) {
        return agentMapper.findById(id);
    }

    public Agent createAgent(String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations) {
        Agent agent = new Agent(name, description, tools, maxMemoryRecords, maxToolInvocations);
        agentMapper.insert(agent);
        return agent;
    }

    public void deleteAgent(Long id) {
        agentMapper.deleteById(id);
        chatMemoryMapper.deleteByAgentId(id);
    }

    public void updateAgent(Long id, String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setName(name);
            agent.setDescription(description);
            agent.setTools(tools);
            agent.setMaxMemoryRecords(maxMemoryRecords);
            agent.setMaxToolInvocations(maxToolInvocations);
            agentMapper.update(agent);
            agentExecutors.remove(id.toString());
        }
    }

    public AgentExecutor getAgentExecutor(Long agentId, Consumer<String> stringConsumer, Consumer<ChatHistory> chatHistoryConsumer) {
        return agentExecutors.computeIfAbsent(agentId.toString(), id -> {
            Agent agent = agentMapper.findById(agentId);
            if (agent == null) {
                return null;
            }
            return createAgentExecutor(agent, stringConsumer, chatHistoryConsumer);
        });
    }
    public AgentExecutor getAgentExecutor(Long agentId) {
        return getAgentExecutor(agentId, null, null);
    }

    private AgentExecutor createAgentExecutor(Agent agent, Consumer<String> messageConsumer, Consumer<ChatHistory> chatHistoryConsumer) {
        try {
            ChatModel chatModel = null;
            StreamingChatModel streamingModel = null;
            try {
                chatModel = chatModelFactory.createChatModel();
                streamingModel = chatModelFactory.createStreamingChatModel();
            } catch (Exception e) {
            }

            List<String> selectedToolNames = parseToolNames(agent.getTools());
            List<AgentTool> selectedTools = allTools.stream()
                    .filter(t -> selectedToolNames.contains(t.getName()))
                    .collect(Collectors.toList());

            SQLiteChatMemoryStore memoryStore = new SQLiteChatMemoryStore(chatMemoryMapper, agent.getId());
            int maxRecords = agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 20;
            int maxToolInvocations = agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 10;

            return new AgentExecutor(agent, chatModel, streamingModel, selectedTools, memoryStore, maxRecords, maxToolInvocations, messageConsumer, chatHistoryConsumer);
        } catch (Exception e) {
           logger.error("Error creating agent executor: ", e);
           return null;
        }
    }

    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(toolsStr.split(","));
    }

    public interface Assistant {
        String chat(@UserMessage String userMessage);

        TokenStream streamingChat(@UserMessage String userMessage);
    }

    public static class AgentExecutor {
        private final Agent agent;
        private final Assistant assistant;
        private final Assistant streamingAssistant;
        private final AgentMessageHandler agentMessageHandler;

        public AgentExecutor(Agent agent, ChatModel chatModel, StreamingChatModel streamingModel,
                             List<AgentTool> selectedTools, SQLiteChatMemoryStore memoryStore,
                             int maxMemoryRecords, int maxToolInvocations, Consumer<String> messageConsumer, Consumer<ChatHistory> chatHistoryConsumer) {
            this.agentMessageHandler = new AgentMessageHandler(messageConsumer, chatHistoryConsumer);
            this.agent = agent;
            String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "。" +
                    "你的主要工作是" + agent.getDescription() + "。" +
                    "在你判断需要时，你可以调用一系列工具。" +
                    "请认真回答用户问题。";

            if (chatModel != null) {
                var aiBuilder = AiServices.builder(Assistant.class)
                        .chatModel(chatModel)
                        .maxSequentialToolsInvocations(maxToolInvocations)
                        .systemMessageProvider(chatMemoryId -> systemMessage);

                if (memoryStore != null) {
                    aiBuilder.chatMemory(MessageWindowChatMemory.builder()
                            .id("agent-" + agent.getId())
                            .maxMessages(maxMemoryRecords)
                            .chatMemoryStore(memoryStore)
                            .build());
                }

                if (!selectedTools.isEmpty()) {
                    aiBuilder.tools(selectedTools.toArray());
                }

                this.assistant = aiBuilder.build();
            } else {
                this.assistant = null;
            }

            if (streamingModel != null) {
                var streamBuilder = AiServices.builder(Assistant.class)
                        .streamingChatModel(streamingModel)
                        .maxSequentialToolsInvocations(maxToolInvocations)
                        .systemMessageProvider(chatMemoryId -> systemMessage);

                if (memoryStore != null) {
                    streamBuilder.chatMemory(MessageWindowChatMemory.builder()
                            .id("agent-" + agent.getId())
                            .maxMessages(maxMemoryRecords)
                            .chatMemoryStore(memoryStore)
                            .build());
                }

                if (!selectedTools.isEmpty()) {
                    streamBuilder.tools(selectedTools.toArray());
                }

                this.streamingAssistant = streamBuilder.build();
            } else {
                this.streamingAssistant = null;
            }
        }

        public String execute(String message) {
            if (assistant == null) {
                return getSimulatedResponse(message);
            }

            try {
                return assistant.chat(message);
            } catch (Exception e) {
                return getSimulatedResponse(message) + "\n(注: " + e.getMessage() + ")";
            }
        }



        public void executeStreaming(String userMessage) {
            if (streamingAssistant == null) {
                String response = execute(userMessage);
                agentMessageHandler.partialResponseHandler(response);
                agentMessageHandler.down();
                return;
            }
            try {
                CountDownLatch latch = new CountDownLatch(1);
                TokenStream tokenStream = streamingAssistant.streamingChat(userMessage)
                        .onError(e -> {
                            agentMessageHandler.onErrorHandler(e);
                            latch.countDown();
                        })
                        .onCompleteResponse(response -> {
                            agentMessageHandler.onCompleteResponseHandler(response);
                            latch.countDown();
                        })
                        .onPartialResponse(r -> agentMessageHandler.partialResponseHandler(r))
                        .onPartialThinking(thinking -> agentMessageHandler.thinkingHandler(thinking))
                        .beforeToolExecution(toolExecution -> agentMessageHandler.beforeToolExecutionHandler(toolExecution))
                        .onToolExecuted(toolExecution -> agentMessageHandler.toolExecutionHandler(toolExecution));
                tokenStream.start();
                latch.await(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("\n(注: 流式响应失败: " + e.getMessage() + ")",e);
                agentMessageHandler.onErrorHandler(e);
            }
        }

        private String getSimulatedResponse(String message) {
            return agent.getName() + ": " + message + "\n这是一个模拟响应，因为API密钥未配置或请求失败。";
        }
        public class AgentMessageHandler {
            private Consumer<String> messageConsumer;
            private Consumer<ChatHistory> chatHistoryConsumer;
            private String lastMessageType="";
            private String currentMessageType="";
            private String responseId;
            private StringBuilder messageBuilder = new StringBuilder();
            private StringBuilder thinkingBuilder = new StringBuilder();
            private ToolCallInfo toolCallInfo;
            public AgentMessageHandler(Consumer<String> messageConsumer, Consumer<ChatHistory> chatHistoryConsumer) {
                this.messageConsumer = messageConsumer;
                this.chatHistoryConsumer = chatHistoryConsumer;
                this.responseId = UUID.randomUUID().toString();
            }
            public void down(){
                Map<String, Object> data = new HashMap<>(3);
                data.put("type", "down");
                data.put("responseId", responseId);
                messageConsumer.accept(JSON.toJSONString(data));
                messageTypeChangedChatHistoryHandler("down");
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
                down();
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
