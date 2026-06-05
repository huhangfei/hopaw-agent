package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.AgentMapper;
import com.agent.hopaw.infra.model.entity.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentService implements IAgentService {
    private final static Logger logger = LoggerFactory.getLogger(AgentService.class);
    private final AgentMapper agentMapper;

    public AgentService(AgentMapper agentMapper) {
        this.agentMapper = agentMapper;
    }

    @Override
    public List<Agent> getAllAgents() {
        return agentMapper.findAll();
    }
    @Override
    public Agent getAgentById(Long id) {
        return agentMapper.findById(id);
    }
    @Override
    public List<Agent> getAgentByIds(List<Long> ids) {
        if(ids==null || ids.isEmpty()){
            return new ArrayList<>(0);
        }
        return agentMapper.findByIds(ids);
    }

    @Override
    public Agent createAgent(String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults, Boolean enableAllTools, String userId) {
        Agent agent = new Agent(name, description, tools, maxMemoryRecords, maxToolInvocations, enableThinking);
        agent.setAiModelId(aiModelId);
        agent.setEnableThinking(enableThinking);
        agent.setVectorToolSearch(vectorToolSearch != null ? vectorToolSearch : true);
        agent.setVectorToolSearchMaxResults(vectorToolSearchMaxResults != null ? vectorToolSearchMaxResults : 5);
        agent.setEnableAllTools(enableAllTools);
        agent.setUserId(userId);
        agentMapper.insert(agent);
        return agent;
    }


    @Override
    public void deleteAgent(Long id, String userId) {
        agentMapper.deleteById(id);
    }

    @Override
    public void updateAgent(String userId, Long id, String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults, Boolean enableAllTools) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setName(name);
            agent.setDescription(description);
            agent.setTools(tools);
            agent.setMaxMemoryRecords(maxMemoryRecords);
            agent.setMaxToolInvocations(maxToolInvocations);
            agent.setAiModelId(aiModelId);
            if (enableThinking != null) {
                agent.setEnableThinking(enableThinking);
            }
            agent.setVectorToolSearch(vectorToolSearch != null ? vectorToolSearch : true);
            agent.setVectorToolSearchMaxResults(vectorToolSearchMaxResults != null ? vectorToolSearchMaxResults : 5);
            agent.setEnableAllTools(enableAllTools);
            agentMapper.update(agent);
        }
    }

    @Override
    public void updateThinking(Long id, Boolean enabled, String userId) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setEnableThinking(enabled);
            agentMapper.update(agent);
        }


    }

    @Override
    public List<Agent> getAgentsPage(String userId, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        return agentMapper.findByUserIdWithKeyword(userId, keyword, offset, size);
    }

    @Override
    public int countAgents(String userId, String keyword) {
        return agentMapper.countByUserIdWithKeyword(userId, keyword);
    }
}