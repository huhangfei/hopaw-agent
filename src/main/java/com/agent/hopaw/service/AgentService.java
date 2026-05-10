package com.agent.hopaw.service;

import com.agent.hopaw.mapper.AgentMapper;
import com.agent.hopaw.mapper.ChatMemoryMapper;
import com.agent.hopaw.model.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {
    private final static Logger logger = LoggerFactory.getLogger(AgentService.class);
    private final Map<String, AgentExecutor> agentExecutors = new HashMap<>();
    private final AgentMapper agentMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final AgentExecutorManager agentExecutorManager;

    public AgentService(AgentMapper agentMapper, ChatMemoryMapper chatMemoryMapper, AgentExecutorManager agentExecutorManager) {
        this.agentMapper = agentMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentExecutorManager = agentExecutorManager;
    }

    public List<Agent> getAllAgents() {
        return agentMapper.findAll();
    }

    public Agent getAgentById(Long id) {
        return agentMapper.findById(id);
    }

    public Agent createAgent(String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults, String userId) {
        Agent agent = new Agent(name, description, tools, maxMemoryRecords, maxToolInvocations, enableThinking);
        agent.setAiModelId(aiModelId);
        agent.setEnableThinking(enableThinking);
        agent.setVectorToolSearch(vectorToolSearch != null ? vectorToolSearch : true);
        agent.setVectorToolSearchMaxResults(vectorToolSearchMaxResults != null ? vectorToolSearchMaxResults : 5);
        agent.setUserId(userId);
        agentMapper.insert(agent);
        return agent;
    }


    public void deleteAgent(Long id, String userId) {
        agentMapper.deleteById(id);
        chatMemoryMapper.deleteByAgentId(id);
        agentExecutorManager.stopAndRemoveAgentExecutor(id, userId);
    }

    public void updateAgent(String userId, Long id, String name, String description, String tools, Integer maxMemoryRecords, Integer maxToolInvocations, Long aiModelId, Boolean enableThinking, Boolean vectorToolSearch, Integer vectorToolSearchMaxResults) {
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
            agentMapper.update(agent);
            agentExecutorManager.stopAndRemoveAgentExecutor(id, userId);
        }
    }


    public void updateThinking(Long id, Boolean enabled, String userId) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setEnableThinking(enabled);
            agentMapper.update(agent);
            agentExecutorManager.stopAndRemoveAgentExecutor(id, userId);
        }


    }
    public boolean isAgentExecutorRunning(Long agentId, String userId) {
        return agentExecutorManager.isAgentExecutorRunning(agentId, userId);
    }

    public void stopAgentExecutor(Long agentId, String userId) {
        agentExecutorManager.stopAndRemoveAgentExecutor(agentId, userId);
    }


    public AgentExecutor getAgentExecutor(Long agentId, String userId){
        Agent agent = agentMapper.findById(agentId);
        if(agent==null){
            return null;
        }
        return agentExecutorManager.getAgentExecutor(agent, userId);
    }

}