package com.agent.hopaw.service;

import com.agent.hopaw.constant.AiModelCallSourceEnum;
import com.agent.hopaw.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.tools.AgentTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AgentExecutorManager {
    private final AiModelService aiModelService;
    private final ChatHistoryStorageService chatHistoryStorageService;
    private final TokenUsageService tokenUsageService;
    private final SQLiteChatMemoryStore memoryStore;
    private final AgentToolService agentToolService;

    private final LongTermMemoryService longTermMemoryService;
    private final Map<String, AgentExecutor> agentExecutors = new HashMap<>();

    public AgentExecutorManager(AiModelService aiModelService, ChatHistoryStorageService chatHistoryStorageService, TokenUsageService tokenUsageService, SQLiteChatMemoryStore memoryStore, AgentToolService agentToolService, LongTermMemoryService longTermMemoryService) {
        this.aiModelService = aiModelService;
        this.chatHistoryStorageService = chatHistoryStorageService;
        this.tokenUsageService = tokenUsageService;
        this.memoryStore = memoryStore;
        this.agentToolService = agentToolService;
        this.longTermMemoryService = longTermMemoryService;
    }
    public void addToolStopHook(Long agentId, String userId, String callId, Consumer<String> hook){
        AgentExecutor agentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (agentExecutor != null) {
            agentExecutor.addToolStopHook(callId, hook);
        }
    }
    public void sendToolRunningContent(Long agentId, String userId,String callId,Object resultPartial){
        AgentExecutor agentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (agentExecutor != null) {
            agentExecutor.sendToolRunningContent(callId, resultPartial);
        }
    }
    public void stopTool(Long agentId, String userId,String callId) {
        AgentExecutor agentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (agentExecutor != null) {
            agentExecutor.stopTool(callId);
        }
    }
    public boolean toolIsCancelled(Long agentId, String userId,String callId) {
        AgentExecutor agentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (agentExecutor != null) {
            return agentExecutor.toolIsCancelled(callId);
        }
        return false;
    }

    public void clearAndStopAgentExecutorByAiModel(Long aiModelId) {
        List<AgentExecutor> list = agentExecutors.values().stream().collect(Collectors.toList());
        for (AgentExecutor agentExecutor : list) {
            Agent agent1 = agentExecutor.getAgent();
            if (agent1.getAiModelId() != null && agent1.getAiModelId().equals(aiModelId)) {
                stopAndRemoveAgentExecutor(agent1.getId(), agent1.getUserId());
            }
        }
    }
    public void stopAgentExecutor(Long agentId, String userId) {
        AgentExecutor agentExecutor = agentExecutors.get(agentId + "_" + userId);
        if (agentExecutor != null) {
            agentExecutor.stop();
        }
    }
    public void stopAndRemoveAgentExecutor(Long agentId, String userId) {
        stopAgentExecutor(agentId, userId);
        agentExecutors.remove(agentId + "_" + userId);
    }

    public boolean isAgentExecutorRunning(Long agentId, String userId) {
        AgentExecutor agentExecutor = agentExecutors.get(agentId + "_" + userId);
        return agentExecutor != null && agentExecutor.running();
    }
    public AgentExecutor getAgentExecutor(Agent agent, String userId) {
        return agentExecutors.computeIfAbsent(agent.getId() + "_" + userId, id -> {
            return createAgentExecutor(agent, userId);
        });
    }
    private AgentExecutor createAgentExecutor(Agent agent, String userId) {
        ChatModel chatModel = null;
        StreamingChatModel streamingModel = null;

        LangChain4jMonitor langChain4jMonitor = new LangChain4jMonitor(AiModelCallSourceEnum.Chat)
                .setAgentId(agent.getId())
                .setUserId(userId)
                .setTokenUsageService(tokenUsageService);
        List<AgentTool> agentTools = agentToolService.getAgentTools();
        chatModel = aiModelService.createChatModel(agent.getAiModelId(), agent.getEnableThinking(), langChain4jMonitor);
        streamingModel = aiModelService.createStreamingChatModel(agent.getAiModelId(), agent.getEnableThinking(), langChain4jMonitor);
        List<String> selectedToolNames = parseToolNames(agent.getTools());
        List<AgentTool> selectedTools = agentTools.stream()
                .filter(t -> selectedToolNames.contains(t.getName()))
                .collect(Collectors.toList());

        return new AgentExecutor(agent, userId, chatModel, streamingModel, selectedTools, memoryStore, a -> this.getSystemMessage(a, userId, selectedTools), chatHistoryStorageService);
    }

    public String getSystemMessage(Agent agent, String userId,List<AgentTool> selectedTools) {
        String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "," +
                "主要工作是" + agent.getDescription() + "," +
                "你的agentId是" + agent.getId() + "。\n" +
                "在遇到需要用户提供信息或最新信息不正确的时候，不要一直猜，先查询记忆，记忆中没有就问用户。\n" +
                "在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作，询问用户。\n" +
                "你只能使用用户提供的工具，绝对不能调用不存在的工具。\n" +
                "不要编造工具！\n";
        if(agent.getVectorToolSearch()!=null && agent.getVectorToolSearch() && selectedTools != null && !selectedTools.isEmpty()){
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

    private String getToolKeywords(List<AgentTool> selectedTools){
        return selectedTools.stream().map(AgentTool::getKeyword).collect(Collectors.joining(","));
    }

    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(toolsStr.split(","));
    }
}
