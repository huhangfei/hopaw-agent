package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.infra.executor.AgentExecutor;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.memory.SQLiteChatMemoryStore;
import com.agent.hopaw.infra.monitor.LangChain4jMonitor;
import com.agent.hopaw.infra.storage.ChatHistoryStore;
import com.agent.hopaw.infra.memory.LongTermMemoryService;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.IAgentToolService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AgentExecutorService implements IAgentExecutorService {
    private final AiModelService aiModelService;
    private final ChatHistoryStore chatHistoryStore;

    private final TokenUsageService tokenUsageService;
    private final SQLiteChatMemoryStore memoryStore;
    private final IAgentToolService agentToolService;

    private final LongTermMemoryService longTermMemoryService;
    private final EmbeddingModel embeddingModel;
    private final Map<String, IAgentExecutor> agentExecutors = new HashMap<>();

    public AgentExecutorService(AiModelService aiModelService, ChatHistoryStore chatHistoryStore, TokenUsageService tokenUsageService, SQLiteChatMemoryStore memoryStore, IAgentToolService agentToolService, LongTermMemoryService longTermMemoryService, EmbeddingModel embeddingModel) {
        this.aiModelService = aiModelService;
        this.chatHistoryStore = chatHistoryStore;
        this.tokenUsageService = tokenUsageService;
        this.memoryStore = memoryStore;
        this.agentToolService = agentToolService;
        this.longTermMemoryService = longTermMemoryService;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void addToolStopHook(Long agentId, String userId, String callId, Consumer<String> hook) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (IAgentExecutor != null) {
            IAgentExecutor.addToolStopHook(callId, hook);
        }
    }

    @Override
    public void sendToolRunningContent(Long agentId, String userId, String callId, Object resultPartial) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (IAgentExecutor != null) {
            IAgentExecutor.sendToolRunningContent(callId, resultPartial);
        }
    }

    @Override
    public void stopTool(Long agentId, String userId, String callId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (IAgentExecutor != null) {
            IAgentExecutor.stopTool(callId);
        }
    }

    @Override
    public boolean toolIsCancelled(Long agentId, String userId, String callId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (IAgentExecutor != null) {
            return IAgentExecutor.toolIsCancelled(callId);
        }
        return false;
    }

    @Override
    public void clearAndStopAgentExecutorByAiModel(Long aiModelId) {
        List<IAgentExecutor> list = agentExecutors.values().stream().collect(Collectors.toList());
        for (IAgentExecutor IAgentExecutor : list) {
            Agent agent1 = IAgentExecutor.getAgent();
            if (agent1.getAiModelId() != null && agent1.getAiModelId().equals(aiModelId)) {
                stopAndRemoveAgentExecutor(agent1.getId(), agent1.getUserId());
            }
        }
    }

    @Override
    public void stopAgentExecutor(Long agentId, String userId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (IAgentExecutor != null) {
            IAgentExecutor.stop();
        }
    }

    @Override
    public void stopAndRemoveAgentExecutor(Long agentId, String userId) {
        stopAgentExecutor(agentId, userId);
        agentExecutors.remove(agentId + "_" + userId);
    }

    @Override
    public boolean isAgentExecutorRunning(Long agentId, String userId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(agentId + "_" + userId);
        return IAgentExecutor != null && IAgentExecutor.running();
    }

    @Override
    public IAgentExecutor getAgentExecutor(Agent agent, String userId) {
        return agentExecutors.computeIfAbsent(agent.getId() + "_" + userId, id -> {
            return createAgentExecutor(agent, userId);
        });
    }

    @Override
    public IAgentExecutor createAgentExecutor(Agent agent, String userId) {
        LangChain4jMonitor langChain4jMonitor = new LangChain4jMonitor(AiModelCallSourceEnum.Chat)
                .setAgentId(agent.getId())
                .setUserId(userId)
                .setTokenUsageService(tokenUsageService);
        if(agent.getAiModelId() == null){
            throw new RuntimeException("智能体没有设置AI模型");
        }
        ChatModel chatModel = aiModelService.createChatModel(agent.getAiModelId(), agent.getEnableThinking(), langChain4jMonitor);
        StreamingChatModel streamingModel = aiModelService.createStreamingChatModel(agent.getAiModelId(), agent.getEnableThinking(), langChain4jMonitor);
        List<String> selectedToolNames = parseToolNames(agent.getTools());
        List<AgentTool> selectedTools = agentToolService.getAgentTools().stream()
                .filter(t -> selectedToolNames.contains(t.getName()))
                .collect(Collectors.toList());
        return new AgentExecutor(UUID.randomUUID().toString(),agent, userId, chatModel, streamingModel,selectedTools, memoryStore, embeddingModel, a -> this.getSystemMessage(a, userId, selectedTools), chatHistoryStore);
    }

    @Override
    public String getSystemMessage(Agent agent, String userId, List<AgentTool> selectedTools) {
        String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "," +
                "主要工作是" + agent.getDescription() + "," +
                "你的agentId是" + agent.getId() + "。\n" +
                "在遇到需要用户提供信息或最新信息不正确的时候，不要一直猜，先查询记忆，记忆中没有就问用户。\n" +
                "在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作，询问用户。\n" +
                "你只能使用用户提供的工具，绝对不能调用不存在的工具。\n" +
                "不要编造工具！\n";
        if (agent.getVectorToolSearch() != null && agent.getVectorToolSearch() && selectedTools != null && !selectedTools.isEmpty()) {
            systemMessage += "当需要[" + getToolKeywords(selectedTools) + "]这些能力时，先使用tool_search_tool搜一下对应关键词，拿到工具详情再做决定使用。\n";
        }
        String memoryContent = longTermMemoryService.queryUserAllMemoriesContent(agent.getId(), userId, memory -> {
            if (LongTermMemoryTypeEnum.USER_PROFILE.getCode().equals(memory.getMemoryType())) {
                return true;
            }
            return false;
        });

        if (StringUtils.hasLength(memoryContent)) {
            systemMessage += "这是所有用户记忆，如果需要详细的记忆内容可以根据记忆编号查询记忆详情：\n" + memoryContent + "\n";
        }
        return systemMessage;
    }

}
