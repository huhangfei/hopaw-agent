package com.agent.hopaw.service;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.tools.AgentTool;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AgentService {
    private final Map<String, AgentExecutor> agentExecutors = new HashMap<>();
    private final AgentMapper agentMapper;
    private final List<AgentTool> allTools;

    @Value("${openai.api.key:demo-key}")
    private String openaiApiKey;

    @Value("${openai.base.url:}")
    private String openaiBaseUrl;

    @Value("${openai.model.name:gpt-3.5-turbo}")
    private String modelName;

    public AgentService(AgentMapper agentMapper, List<AgentTool> allTools) {
        this.agentMapper = agentMapper;
        this.allTools = allTools;
    }

    public List<Agent> getAllAgents() {
        return agentMapper.findAll();
    }

    public Agent getAgentById(Long id) {
        return agentMapper.findById(id);
    }

    public Agent createAgent(String name, String description, String tools) {
        Agent agent = new Agent(name, description, tools);
        agentMapper.insert(agent);
        return agent;
    }

    public void deleteAgent(Long id) {
        agentMapper.deleteById(id);
    }

    public void updateAgent(Long id, String name, String description, String tools) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setName(name);
            agent.setDescription(description);
            agent.setTools(tools);
            agentMapper.update(agent);
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
            var builder = OpenAiChatModel.builder()
                    .apiKey(openaiApiKey)
                    .modelName(modelName)
                    .temperature(0.7);
            
            if (openaiBaseUrl != null && !openaiBaseUrl.isEmpty()) {
                builder.baseUrl(openaiBaseUrl);
            }
            
            OpenAiChatModel chatModel = builder.build();

            OpenAiStreamingChatModel streamingModel = null;
            try {
                var streamBuilder = OpenAiStreamingChatModel.builder()
                        .apiKey(openaiApiKey)
                        .modelName(modelName)
                        .temperature(0.7);
                
                if (openaiBaseUrl != null && !openaiBaseUrl.isEmpty()) {
                    streamBuilder.baseUrl(openaiBaseUrl);
                }
                
                streamingModel = streamBuilder.build();
            } catch (Exception e) {
                // streaming model not available
            }

            List<String> selectedToolNames = parseToolNames(agent.getTools());
            List<AgentTool> selectedTools = allTools.stream()
                    .filter(t -> selectedToolNames.contains(t.getName()))
                    .collect(Collectors.toList());

            return new AgentExecutor(agent, chatModel, streamingModel, selectedTools);
        } catch (Exception e) {
            return new AgentExecutor(agent, null, null, new ArrayList<>());
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

        public AgentExecutor(Agent agent, OpenAiChatModel chatModel, OpenAiStreamingChatModel streamingModel,
                           List<AgentTool> selectedTools) {
            this.agent = agent;
            String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "。" +
                    "你的主要工作是" + agent.getDescription() + "。" +
                    "在你判断需要时，你可以调用一系列工具。" +
                    "请认真回答用户问题。";

            if (chatModel != null && !selectedTools.isEmpty()) {
                this.assistant = AiServices.builder(Assistant.class)
                        .chatModel(chatModel)
                        .systemMessageProvider(chatMemoryId -> systemMessage)
                        .tools(selectedTools.toArray())
                        .build();
            } else if (chatModel != null) {
                this.assistant = AiServices.builder(Assistant.class)
                        .chatModel(chatModel)
                        .systemMessageProvider(chatMemoryId -> systemMessage)
                        .build();
            } else {
                this.assistant = null;
            }

            if (streamingModel != null && !selectedTools.isEmpty()) {
                this.streamingAssistant = AiServices.builder(Assistant.class)
                        .streamingChatModel(streamingModel)
                        .systemMessageProvider(chatMemoryId -> systemMessage)
                        .tools(selectedTools.toArray())
                        .build();
            } else if (streamingModel != null) {
                this.streamingAssistant = AiServices.builder(Assistant.class)
                        .streamingChatModel(streamingModel)
                        .systemMessageProvider(chatMemoryId -> systemMessage)
                        .build();
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
                        .onPartialResponse(chunkConsumer::accept)
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
                            toolInfo.put("arguments", toolExecution.request().arguments());
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
                            toolInfo.put("arguments", toolExecution.request().arguments());
                            toolInfo.put("result", toolExecution.result());
                            toolCallConsumer.accept(toolInfo);
                        } catch (Exception e) {
                        }
                    });
                }

                tokenStream.start();
                latch.await(60, TimeUnit.SECONDS);
            } catch (Exception e) {
                chunkConsumer.accept("\n(注: 流式响应失败: " + e.getMessage() + ")");
            }
        }

        private String getSimulatedResponse(String message) {
            return agent.getName() + ": " + message + "\n这是一个模拟响应，因为API密钥未配置或请求失败。";
        }
    }
}
