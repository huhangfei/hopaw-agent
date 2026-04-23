package com.agent.hopaw.service;

import com.agent.hopaw.model.LongTermMemory;
import com.agent.hopaw.model.Agent;
import com.agent.hopaw.mapper.AgentMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryManageService {

    private final LongTermMemoryService longTermMemoryService;
    private final AgentMapper agentMapper;

    public MemoryManageService(LongTermMemoryService longTermMemoryService, AgentMapper agentMapper) {
        this.longTermMemoryService = longTermMemoryService;
        this.agentMapper = agentMapper;
    }

    public String getGlobalMemorySummary() {
        return longTermMemoryService.getMemorySummary(LongTermMemoryService.GLOBAL_IDENTITY);
    }

    public String getAgentMemorySummary(Long agentId) {
        return longTermMemoryService.getMemorySummary(agentId.toString());
    }

    public List<LongTermMemory> getGlobalMemories() {
        return longTermMemoryService.getMemoriesByIdentity(LongTermMemoryService.GLOBAL_IDENTITY);
    }

    public List<LongTermMemory> getAgentMemories(Long agentId) {
        return longTermMemoryService.getMemoriesByIdentity(agentId.toString());
    }

    public void initializeGlobalMemory() {
        String identity = LongTermMemoryService.GLOBAL_IDENTITY;
        List<LongTermMemory> existing = longTermMemoryService.getRootMemories(identity);
        if (existing.isEmpty()) {
            longTermMemoryService.createRootMemory(identity, "主人属性");
            longTermMemoryService.createRootMemory(identity, "沟通风格");
            longTermMemoryService.createRootMemory(identity, "昵称");
            longTermMemoryService.createRootMemory(identity, "语言风格");
        }
    }

    public void initializeAgentMemory(Long agentId) {
        String identity = agentId.toString();
        List<LongTermMemory> existing = longTermMemoryService.getRootMemories(identity);
        if (existing.isEmpty()) {
            longTermMemoryService.createRootMemory(identity, "主人属性");
            longTermMemoryService.createRootMemory(identity, "沟通风格");
            longTermMemoryService.createRootMemory(identity, "开发习惯");
        }
    }

    public void initializeAllAgentsMemory() {
        List<Agent> agents = agentMapper.findAll();
        for (Agent agent : agents) {
            initializeAgentMemory(agent.getId());
        }
    }

    public LongTermMemory addGlobalChildMemory(Long parentId, String memory) {
        return longTermMemoryService.createChildMemory(LongTermMemoryService.GLOBAL_IDENTITY, memory, parentId);
    }

    public LongTermMemory addAgentChildMemory(Long agentId, Long parentId, String memory) {
        return longTermMemoryService.createChildMemory(agentId.toString(), memory, parentId);
    }

    public LongTermMemory addGlobalRootMemory(String memory) {
        return longTermMemoryService.createRootMemory(LongTermMemoryService.GLOBAL_IDENTITY, memory);
    }

    public LongTermMemory addAgentRootMemory(Long agentId, String memory) {
        return longTermMemoryService.createRootMemory(agentId.toString(), memory);
    }

    public void updateMemory(Long memoryId, String newContent) {
        longTermMemoryService.updateMemoryContent(memoryId, newContent);
    }

    public void deleteMemory(Long memoryId) {
        longTermMemoryService.deleteMemory(memoryId);
    }

    public void clearGlobalMemory() {
        longTermMemoryService.deleteAllMemories(LongTermMemoryService.GLOBAL_IDENTITY);
    }

    public void clearAgentMemory(Long agentId) {
        longTermMemoryService.deleteAllMemories(agentId.toString());
    }
}
