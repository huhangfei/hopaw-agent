package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.Agent;

import java.util.List;

public interface IAgentService {
    List<Agent> getAllAgents();
    Agent getAgentById(Long id);
    List<Agent> getAgentByIds(List<Long> ids);
    Agent createAgent(String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults, String userId);
    void deleteAgent(Long id, String userId);
    void updateAgent(String userId, Long id, String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults);
    void updateThinking(Long id, Boolean enabled, String userId);
}
