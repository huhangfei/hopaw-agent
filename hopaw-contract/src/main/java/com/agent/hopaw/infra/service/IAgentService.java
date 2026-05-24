package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.model.dto.UserRequest;
import com.agent.hopaw.infra.model.entity.Agent;

import java.util.List;

public interface IAgentService {
    List<Agent> getAllAgents();
    Agent getAgentById(Long id);
    Agent createAgent(String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults, String userId);
    void deleteAgent(Long id, String userId);
    void updateAgent(String userId, Long id, String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults);
    void updateThinking(Long id, Boolean enabled, String userId);
    boolean isAgentExecutorRunning(Long agentId, String userId);
    void stopAgentExecutor(Long agentId, String userId);

    IAgentExecutor getAgentExecutor(UserRequest userRequest);
}
