package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.mapper.AgentMapper;
import com.agent.hopaw.infra.mapper.ChatMemoryMapper;
import com.agent.hopaw.infra.model.dto.UserRequest;
import com.agent.hopaw.infra.model.entity.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentService implements IAgentService {
    private final static Logger logger = LoggerFactory.getLogger(AgentService.class);
    private final AgentMapper agentMapper;
    private final ChatMemoryMapper chatMemoryMapper;
    private final IAgentExecutorService agentExecutorService;

    public AgentService(AgentMapper agentMapper, ChatMemoryMapper chatMemoryMapper, IAgentExecutorService agentExecutorService) {
        this.agentMapper = agentMapper;
        this.chatMemoryMapper = chatMemoryMapper;
        this.agentExecutorService = agentExecutorService;
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
        agentExecutorService.stopAndRemoveAgentExecutor(id, userId);
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
            agentExecutorService.stopAndRemoveAgentExecutor(id, userId);
        }
    }


    public void updateThinking(Long id, Boolean enabled, String userId) {
        Agent agent = agentMapper.findById(id);
        if (agent != null) {
            agent.setEnableThinking(enabled);
            agentMapper.update(agent);
            agentExecutorService.stopAndRemoveAgentExecutor(id, userId);
        }


    }
    public boolean isAgentExecutorRunning(Long agentId, String userId) {
        return agentExecutorService.isAgentExecutorRunning(agentId, userId);
    }

    public void stopAgentExecutor(Long agentId, String userId) {
        agentExecutorService.stopAndRemoveAgentExecutor(agentId, userId);
    }


    public IAgentExecutor getAgentExecutor(UserRequest userRequest){
        Agent agent = agentMapper.findById(userRequest.getAgentId());
        if(agent==null){
            return null;
        }
        userRequest.setAgent(agent);
        return agentExecutorService.getAgentExecutor(userRequest);
    }

}