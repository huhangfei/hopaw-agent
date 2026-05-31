package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.executor.AgentExecutor;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.model.dto.AgentExecutorParams;
import com.agent.hopaw.infra.model.dto.SkillInfo;
import com.agent.hopaw.infra.model.dto.UserRequest;
import com.agent.hopaw.infra.monitor.LangChain4jMonitor;
import com.agent.hopaw.infra.storage.ChatHistoryStore;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.IAgentToolService;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentExecutorService implements IAgentExecutorService {
    private final IAgentService agentService;
    private final AiModelService aiModelService;
    private final ChatHistoryStore chatHistoryStore;
    private final TokenUsageService tokenUsageService;
    private final IChatMemoryService chatMemoryService;
    private final IAgentToolService agentToolService;
    private final EmbeddingModel embeddingModel;
    private final ISkillService ISkillService;
    private final IChatSessionService chatSessionService;

    private final Map<String, IAgentExecutor> agentExecutors = new HashMap<>();

    public AgentExecutorService(IAgentService agentService, AiModelService aiModelService, ChatHistoryStore chatHistoryStore, TokenUsageService tokenUsageService, IChatMemoryService chatMemoryService, IAgentToolService agentToolService, EmbeddingModel embeddingModel, ISkillService ISkillService, IChatSessionService chatSessionService) {
        this.agentService = agentService;
        this.aiModelService = aiModelService;
        this.chatHistoryStore = chatHistoryStore;
        this.tokenUsageService = tokenUsageService;
        this.chatMemoryService = chatMemoryService;
        this.agentToolService = agentToolService;
        this.embeddingModel = embeddingModel;
        this.ISkillService = ISkillService;
        this.chatSessionService = chatSessionService;
    }

    @Override
    public void addToolStopHook(String sessionId, String callId, Consumer<String> hook) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.addToolStopHook(callId, hook);
        }
    }

    @Override
    public void sendToolRunningContent(String sessionId, String callId, Object resultPartial) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.sendToolRunningContent(callId, resultPartial);
        }
    }

    @Override
    public void stopTool(String sessionId, String callId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.stopTool(callId);
        }
    }

    @Override
    public boolean toolIsCancelled(String sessionId, String callId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            return IAgentExecutor.toolIsCancelled(callId);
        }
        return false;
    }

    @Override
    public void clearAndStopAgentExecutorByAiModel(Long aiModelId) {
        List<IAgentExecutor> list = agentExecutors.values().stream().collect(Collectors.toList());
        for (IAgentExecutor agentExecutor : list) {
            if (agentExecutor.getAiModelId() != null && agentExecutor.getAiModelId().equals(aiModelId)) {
                stopAndRemoveAgentExecutor(agentExecutor.getSessionId());
            }
        }
    }

    @Override
    public void stopAgentExecutor(String sessionId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.stop();
        }
    }

    @Override
    public void stopAndRemoveAgentExecutor(String sessionId) {
        stopAgentExecutor(sessionId);
        agentExecutors.remove(sessionId);
    }

    @Override
    public boolean isAgentExecutorRunning(String sessionId) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        return IAgentExecutor != null && IAgentExecutor.running();
    }

    @Override
    public IAgentExecutor getAgentExecutor(String sessionId) {
        return agentExecutors.get(sessionId);
    }

    @Override
    public IAgentExecutor createAgentExecutor(UserRequest userRequest, BiConsumer<String, String> messageConsumer) {
        Agent agent = userRequest.getAgentId() != null ? agentService.getAgentById(userRequest.getAgentId()) : null;
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        if (userRequest.getAiModelId() == null) {
            throw new RuntimeException("智能体没有设置AI模型");
        }
        LangChain4jMonitor langChain4jMonitor = new LangChain4jMonitor(AiModelCallSourceEnum.Chat)
                .setSessionId(userRequest.getSessionId())
                .setAgentId(userRequest.getAgentId())
                .setUserId(userRequest.getUserId())
                .setTokenUsageService(tokenUsageService);
        List<String> selectedToolNames = parseToolNames(agent.getTools());
        List<AgentTool> selectedTools = agentToolService.getAgentTools().stream()
                .filter(t -> selectedToolNames.contains(t.getName()))
                .collect(Collectors.toList());
        AgentExecutorParams agentExecutorParams = new AgentExecutorParams();
        agentExecutorParams.setSessionId(userRequest.getSessionId());
        agentExecutorParams.setAgentId(agent.getId());
        agentExecutorParams.setUserId(userRequest.getUserId());
        agentExecutorParams.setAiModelId(userRequest.getAiModelId());
        agentExecutorParams.setMaxMemoryRecords(agent.getMaxMemoryRecords() != null ? agent.getMaxMemoryRecords() : 10);
        agentExecutorParams.setMaxToolInvocations(agent.getMaxToolInvocations() != null ? agent.getMaxToolInvocations() : 3);
        agentExecutorParams.setEnableThinking(userRequest.getEnableThinking());
        agentExecutorParams.setVectorToolSearch(agent.getVectorToolSearch() != null ? agent.getVectorToolSearch() : false);
        agentExecutorParams.setVectorToolSearchMaxResults(agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 5);
        agentExecutorParams.setSkillNames(userRequest.getSkillNames());
        agentExecutorParams.setToolSets(selectedTools);
        agentExecutorParams.setContents(Arrays.asList(new TextContent(userRequest.getMessage())));
        Function<Long, String> systemMessageProvider = aId -> {
            return getSystemMessage(userRequest.getSessionId(), agent, userRequest.getUserId(), selectedTools, userRequest.getSkillNames());
        };
        AgentExecutor agentExecutor = new AgentExecutor(agentExecutorParams, chatMemoryService, embeddingModel, systemMessageProvider, chatHistoryStore, aiModelService, langChain4jMonitor, messageConsumer, chatSessionService);
        agentExecutors.put(userRequest.getSessionId(), agentExecutor);
        return agentExecutor;
    }

    private String getSystemMessage(String sessionId, Agent agent, String userId, List<AgentTool> selectedTools, List<String> skillNames) {
        String systemMessage = "你是一个智能助手，名字叫" + agent.getName() + "," +
                "主要工作是" + agent.getDescription() + "," +
                "你的agentId是" + agent.getId() + "。\n" +
                "在遇到需要用户提供信息或最新信息不正确的时候，不要猜，先查询记忆，记忆中没有就问用户。\n" +
                "在判断有需要调用工具就去调用，遇到危险操作，立刻停止操作，询问用户。\n" +
                "你只能使用用户提供的工具，绝对不能调用不存在的工具。\n" +
                "不要编造工具！\n";
        if (agent.getVectorToolSearch() != null && agent.getVectorToolSearch() && selectedTools != null && !selectedTools.isEmpty()) {
            systemMessage += "当需要[" + getToolKeywords(selectedTools) + "]这些能力时，先使用tool_search_tool搜一下对应关键词，拿到工具详情再做决定使用。\n";
        }
        if (skillNames != null && !skillNames.isEmpty()) {
            String skillContext = buildSkillContext(skillNames);
            systemMessage += skillContext;
        }
        return systemMessage;
    }

    private String buildSkillContext(List<String> skillNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("你将使用以下技能完成任务，请严格遵循技能中定义的指令：\n\n");
        for (String name : skillNames) {
            SkillInfo skill = ISkillService.getSkill(name);
            if (skill == null || skill.getContent() == null) {
                continue;
            }
            String content = skill.getContent().trim();
            sb.append("--- 技能: ").append(name).append(" ---\n");
            sb.append(content);
            if (!content.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("--- 结束 ---\n\n");
        }
        return sb.toString();
    }

    private String getToolKeywords(List<AgentTool> selectedTools) {
        return selectedTools.stream().map(AgentTool::getKeyword).collect(Collectors.joining(","));
    }

    private List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(toolsStr.split(","));
    }
}
