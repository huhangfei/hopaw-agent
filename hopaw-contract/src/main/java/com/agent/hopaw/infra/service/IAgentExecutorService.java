package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.UserRequest;

import java.util.function.Consumer;

public interface IAgentExecutorService {
    void addToolStopHook(String sessionId, String callId, Consumer<String> hook);

    void sendToolRunningContent(String sessionId, String callId, Object resultPartial);
    void toolApprovalComplete(String sessionId, String callId, Boolean allowed);
    void stopTool(String sessionId, String callId);

    boolean toolIsCancelled(String sessionId, String callId);

    void clearAndStopAgentExecutorByAiModel(Long aiModelId);

    void stopAgentExecutor(String sessionId);

    void stopAndRemoveAgentExecutor(String sessionId);

    boolean isAgentExecutorRunning(String sessionId);

    IAgentExecutor getAgentExecutor(String sessionId);

    IAgentExecutor createAgentExecutor(UserRequest userRequest);

}
