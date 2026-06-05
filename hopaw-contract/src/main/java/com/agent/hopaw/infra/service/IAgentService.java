package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.Agent;

import java.util.List;

public interface IAgentService {
    List<Agent> getAllAgents();
    Agent getAgentById(Long id);
    List<Agent> getAgentByIds(List<Long> ids);
    Agent createAgent(Agent agent);
    void deleteAgent(Long id, String userId);
    void updateAgent(Agent agent);
    void updateThinking(Long id, Boolean enabled, String userId);
    List<Agent> getAgentsPage(String userId, String keyword, int page, int size);
    int countAgents(String userId, String keyword);
}
