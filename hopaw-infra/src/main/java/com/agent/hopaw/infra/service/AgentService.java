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
    public Agent createAgent(Agent agent) {
        if (agent.getMaxMemoryRecords() == null) {
            agent.setMaxMemoryRecords(20);
        }
        if (agent.getMaxToolInvocations() == null) {
            agent.setMaxToolInvocations(10);
        }
        if (agent.getVectorToolSearch() == null) {
            agent.setVectorToolSearch(true);
        }
        if (agent.getVectorToolSearchMaxResults() == null) {
            agent.setVectorToolSearchMaxResults(5);
        }
        agentMapper.insert(agent);
        return agent;
    }


    @Override
    public void deleteAgent(Long id, String userId) {
        Agent existing = agentMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("智能体不存在");
        }
        // 删除接口需校验创建人：仅智能体创建人可删除
        if (userId == null || !userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该智能体：仅创建人可删除");
        }
        agentMapper.deleteById(id);
    }

    @Override
    public void updateAgent(Agent agent) {
        Agent existing = agentMapper.findById(agent.getId());
        if (existing != null) {
            existing.setName(agent.getName());
            existing.setDescription(agent.getDescription());
            existing.setTools(agent.getTools());
            existing.setMaxMemoryRecords(agent.getMaxMemoryRecords());
            existing.setMaxToolInvocations(agent.getMaxToolInvocations());
            existing.setAiModelId(agent.getAiModelId());
            if (agent.getEnableThinking() != null) {
                existing.setEnableThinking(agent.getEnableThinking());
            }
            existing.setVectorToolSearch(agent.getVectorToolSearch() != null ? agent.getVectorToolSearch() : true);
            existing.setVectorToolSearchMaxResults(agent.getVectorToolSearchMaxResults() != null ? agent.getVectorToolSearchMaxResults() : 5);
            existing.setEnableAllTools(agent.getEnableAllTools());
            agentMapper.update(existing);
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
        // 智能体数据不分用户，查询不过滤用户
        int offset = (page - 1) * size;
        return agentMapper.findByUserIdWithKeyword(null, keyword, offset, size);
    }

    @Override
    public int countAgents(String userId, String keyword) {
        // 智能体数据不分用户，统计不过滤用户
        return agentMapper.countByUserIdWithKeyword(null, keyword);
    }
}