package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.AgentExecutorParams;

import java.util.function.Consumer;
import java.util.function.Function;

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

    /**
     * 公共执行器创建方法，由各业务服务（聊天/任务）生成参数和系统提示词后调用
     * @param params 执行器参数
     * @param systemMessageProvider 系统提示词生成器，参数为 agentId
     * @return
     */
    IAgentExecutor createAgentExecutor(AgentExecutorParams params, Function<Long, String> systemMessageProvider);

}
