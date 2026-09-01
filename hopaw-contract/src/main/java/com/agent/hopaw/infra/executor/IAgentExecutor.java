package com.agent.hopaw.infra.executor;

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

    /** 本执行器生命周期内已开始的工具调用次数 */
    int getExecutedToolCount();

    /** 本执行器允许的最大工具调用次数（0表示不限制） */
    int getMaxToolInvocations();

    /**
     * 执行
     * 超时时间默认600秒
     * @param contents 请求内容
     */
    void execute(List<Content> contents);

    /**
     * 执行
     * @param contents 请求内容
     * @param timeout 超时时间（秒）
     */
    void execute(List<Content> contents,long timeout);
}
