package com.agent.hopaw.infra.executor;

import com.agent.hopaw.infra.model.dto.UserRequest;
import com.agent.hopaw.infra.model.entity.Agent;

import java.util.function.Consumer;

public interface IAgentExecutor {
    Agent getAgent();

    String getUserId();

    void stop();

    void addToolStopHook(String callId, Consumer<String> hook);

    void stopTool(String callId);

    boolean toolHaveCall(String callId);

    boolean toolIsCancelled(String callId);

    void sendToolRunningContent(String callId, Object resultPartial);

    boolean running();

    String execute(UserRequest userRequest);

    void executeStreaming(UserRequest userRequest, Consumer<String> messageConsumer);
}
