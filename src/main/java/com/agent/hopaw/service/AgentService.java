package com.agent.hopaw.service;

import com.agent.hopaw.config.ChatModelFactoryConfig;
import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.model.ChatModelFactory;
import com.agent.hopaw.tools.AgentTool;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
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

    public AgentExecutor getAgentExecutor(Long agentId) {
        return agentExecutors.computeIfAbsent(agentId.toString(), id -> {
            Agent agent = agentMapper.findById(agentId);
            if (agent == null) {
                return null;
            }
            return createAgentExecutor(agent);
        });
    }

    private AgentExecutor createAgentExecutor(Agent agent) {
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

            return new AgentExecutor(agent, chatModel, streamingModel, selectedTools, memoryStore, maxRecords, maxToolInvocations);
        } catch (Exception e) {
            return new AgentExecutor(agent, null, null, new ArrayList<>(), null, 20, 10);
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

        public AgentExecutor(Agent agent, ChatModel chatModel, StreamingChatModel streamingModel,
                             List<AgentTool> selectedTools, SQLiteChatMemoryStore memoryStore,
                             int maxMemoryRecords, int maxToolInvocations) {
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

        public void executeStreaming(String message, Consumer<String> chunkConsumer) {
            executeStreaming(message, chunkConsumer, null);
        }

        public void executeStreaming(String message, Consumer<String> chunkConsumer, Consumer<Map<String, Object>> toolCallConsumer) {
            if (streamingAssistant == null) {
                String response = execute(message);
                chunkConsumer.accept(response);
                return;
            }

            try {
                CountDownLatch latch = new CountDownLatch(1);
                TokenStream tokenStream = streamingAssistant.streamingChat(message)
                        .onPartialResponse(r -> chunkConsumer.accept(r))
                        .onPartialThinking(thinking -> {
                            try {
//                            Map<String, Object> toolInfo = new HashMap<>();
//                            toolInfo.put("type", "thinking");
//                            toolInfo.put("status", "partial");
//                            toolInfo.put("thinking", thinking.text());
//                            toolCallConsumer.accept(toolInfo);
                                chunkConsumer.accept(thinking.text());
                            } catch (Exception e) {
                            }
                        })
                        .onCompleteResponse(r -> latch.countDown())
                        .onError(e -> latch.countDown());

                if (toolCallConsumer != null) {
                    tokenStream = tokenStream.beforeToolExecution(toolExecution -> {
                        try {
                            Map<String, Object> toolInfo = new HashMap<>();
                            toolInfo.put("type", "tool_call");
                            toolInfo.put("status", "starting");
                            toolInfo.put("toolCallId", toolExecution.request().id());
                            toolInfo.put("toolName", toolExecution.request().name());
                            toolInfo.put("arguments", JSON.parseObject(toolExecution.request().arguments()));
                            toolCallConsumer.accept(toolInfo);
                        } catch (Exception e) {
                        }
                    }).onToolExecuted(toolExecution -> {
                        try {
                            Map<String, Object> toolInfo = new HashMap<>();
                            toolInfo.put("type", "tool_call");
                            toolInfo.put("status", "executed");
                            toolInfo.put("toolCallId", toolExecution.request().id());
                            toolInfo.put("toolName", toolExecution.request().name());
                            toolInfo.put("arguments", JSON.parseObject(toolExecution.request().arguments()));
                            toolInfo.put("result", toolExecution.result());
                            toolCallConsumer.accept(toolInfo);
                        } catch (Exception e) {
                        }
                    });
                }

                tokenStream.start();
                latch.await(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                logger.error("\n(注: 流式响应失败: " + e.getMessage() + ")",e);
                chunkConsumer.accept("\n(注: 流式响应失败: " + e.getMessage() + ")");
            }
        }

        private String getSimulatedResponse(String message) {
            return agent.getName() + ": " + message + "\n这是一个模拟响应，因为API密钥未配置或请求失败。";
        }
    }
}
