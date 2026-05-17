package com.agent.hopaw.infra.executor;

import com.agent.hopaw.infra.model.entity.Agent;
import dev.langchain4j.data.message.Content;

import java.util.List;
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

    String execute(List<Content> contents);

    void executeStreaming(List<Content> contents, Consumer<String> messageConsumer);
}
