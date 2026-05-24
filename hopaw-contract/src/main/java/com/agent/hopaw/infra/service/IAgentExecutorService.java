package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.UserRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.tool.AgentTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface IAgentExecutorService {
    void addToolStopHook(Long agentId, String userId, String callId, Consumer<String> hook);

    void sendToolRunningContent(Long agentId, String userId, String callId, Object resultPartial);

    void stopTool(Long agentId, String userId, String callId);

    boolean toolIsCancelled(Long agentId, String userId, String callId);

    void clearAndStopAgentExecutorByAiModel(Long aiModelId);

    void stopAgentExecutor(Long agentId, String userId);

    void stopAndRemoveAgentExecutor(Long agentId, String userId);

    boolean isAgentExecutorRunning(Long agentId, String userId);

    IAgentExecutor getAgentExecutor(UserRequest userRequest);

    IAgentExecutor createAgentExecutor(UserRequest userRequest);

    String getSystemMessage(Agent agent, String userId, List<AgentTool> selectedTools, List<String> skillNames);

    default String getToolKeywords(List<AgentTool> selectedTools) {
        return selectedTools.stream().map(AgentTool::getKeyword).collect(Collectors.joining(","));
    }

    default List<String> parseToolNames(String toolsStr) {
        if (toolsStr == null || toolsStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(toolsStr.split(","));
    }
}
