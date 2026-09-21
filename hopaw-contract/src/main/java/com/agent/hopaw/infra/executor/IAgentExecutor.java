package com.agent.hopaw.infra.executor;

import com.agent.hopaw.infra.model.dto.AgentExecutorResult;
import dev.langchain4j.data.message.Content;

import java.util.List;
import java.util.function.Consumer;

public interface IAgentExecutor {
    String getSessionId();
    Long getAgentId();
    String getUserId();
    Long getAiModelId();
    void stop();
    void addToolStopHook(String callId, Consumer<String> hook);
    void stopTool(String callId);
    boolean toolHaveCall(String callId);
    boolean toolIsCancelled(String callId);
    void sendToolRunningContent(String callId, Object resultPartial);
    void toolApprovalComplete(String callId,Boolean allowed);
    boolean running();

    /** 看门狗剩余等待时间（秒）：执行器运行中且持续无活动超过超时时间才会结束；未运行返回0 */
    long getWatchdogRemainingSeconds();

    /** 本次任务已运行时长（秒）：执行器运行中返回开始至今的秒数；未运行返回0 */
    long getElapsedSeconds();

    /**
     * 手动延长看门狗截止时间（秒）：用于任务仍在进行但即将超时时用户主动续时。
     * 仅执行器运行中且看门狗已启用时有效；返回延长后的剩余秒数
     */
    long extendWatchdog(long seconds);

    /** 本执行器生命周期内已开始的工具调用次数 */
    int getExecutedToolCount();

    /** 本执行器允许的最大工具调用轮次（0 或负数表示不限制）；未配置时返回 Agent.DEFAULT_MAX_TOOL_INVOCATIONS */
    int getMaxToolInvocations();

    /**
     * 执行
     * 超时时间默认360秒
     * @param contents 请求内容
     */
    AgentExecutorResult execute(List<Content> contents);

    /**
     * 执行
     * @param contents 请求内容
     * @param timeout 超时时间（秒）
     */
    AgentExecutorResult execute(List<Content> contents,long timeout);
}
