package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.UserRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.tool.AgentTool;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface IAgentExecutorService {
    void addToolStopHook(String sessionId, String callId, Consumer<String> hook);

    void sendToolRunningContent(String sessionId, String callId, Object resultPartial);

    void stopTool(String sessionId, String callId);

    boolean toolIsCancelled(String sessionId, String callId);

    void clearAndStopAgentExecutorByAiModel(Long aiModelId);

    void stopAgentExecutor(String sessionId);

    void stopAndRemoveAgentExecutor(String sessionId);

    boolean isAgentExecutorRunning(String sessionId);

    IAgentExecutor getAgentExecutor(String sessionId);

    IAgentExecutor createAgentExecutor(UserRequest userRequest, BiConsumer<String, String> messageConsumer);

}
