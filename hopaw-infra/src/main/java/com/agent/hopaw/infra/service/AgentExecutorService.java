package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.AgentExecutor;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.memory.IChatMemoryService;
import com.agent.hopaw.infra.model.dto.AgentExecutorParams;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AgentExecutorService implements IAgentExecutorService {
    private static final Logger logger = LoggerFactory.getLogger(AgentExecutorService.class);
    private final AiModelService aiModelService;
    private final IChatMemoryService chatMemoryService;
    private final EmbeddingModel embeddingModel;
    private final IChatSessionService chatSessionService;
    private final IChatModelListenerProvider chatModelListenerProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, IAgentExecutor> agentExecutors = new HashMap<>();

    public AgentExecutorService(AiModelService aiModelService, IChatMemoryService chatMemoryService, EmbeddingModel embeddingModel, IChatSessionService chatSessionService, IChatModelListenerProvider chatModelListenerProvider, ApplicationEventPublisher eventPublisher) {
        this.aiModelService = aiModelService;
        this.chatMemoryService = chatMemoryService;
        this.embeddingModel = embeddingModel;
        this.chatSessionService = chatSessionService;
        this.chatModelListenerProvider = chatModelListenerProvider;
        this.eventPublisher = eventPublisher;
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
    public void toolApprovalComplete(String sessionId, String callId, Boolean allowed) {
        IAgentExecutor IAgentExecutor = agentExecutors.get(sessionId);
        if (IAgentExecutor != null) {
            IAgentExecutor.toolApprovalComplete(callId, allowed);
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
    public IAgentExecutor createAgentExecutor(AgentExecutorParams params, Function<Long, String> systemMessageProvider) {
        if (params == null) {
            throw new RuntimeException("执行器参数不能为空");
        }
        if (params.getAgentId() == null) {
            throw new RuntimeException("智能体ID不能为空");
        }
        if (params.getSessionId() == null || params.getSessionId().isEmpty()) {
            throw new RuntimeException("会话ID不能为空");
        }
        // 复用前先清理可能残留的旧执行器，避免覆盖正在运行的实例
        if (agentExecutors.containsKey(params.getSessionId())) {
            stopAndRemoveAgentExecutor(params.getSessionId());
        }
        AgentExecutor agentExecutor = new AgentExecutor(params, chatMemoryService, embeddingModel, systemMessageProvider, aiModelService, chatModelListenerProvider, eventPublisher, chatSessionService);
        agentExecutors.put(params.getSessionId(), agentExecutor);
        return agentExecutor;
    }
}
