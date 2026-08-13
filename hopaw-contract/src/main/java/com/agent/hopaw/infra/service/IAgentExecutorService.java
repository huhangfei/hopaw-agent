package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.TaskComment;
import com.agent.hopaw.infra.model.entity.WorkflowTask;

import java.util.List;
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

    /**
     * 创建聊天代理执行器
     * @param userChatRequest
     * @return
     */
    IAgentExecutor createChatAgentExecutor(UserChatRequest userChatRequest);

    /**
     * 创建任务执行代理执行器
     * @param task 工作流任务
     * @param agent 关联智能体
     * @param comments 任务评论历史
     * @param existingSessionId 已关联的会话编号（打回重做时复用，传 null 表示新建会话）
     * @return
     */
    IAgentExecutor createTaskExecutor(WorkflowTask task, Agent agent, List<TaskComment> comments, String existingSessionId);

}
