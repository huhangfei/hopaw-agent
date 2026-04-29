package com.agent.hopaw.tools;

import com.agent.hopaw.service.LongTermMemoryService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

@Service("memoryTool")
public class MemoryTool implements AgentTool {

    private final LongTermMemoryService longTermMemoryService;

    public MemoryTool(LongTermMemoryService longTermMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
    }

    @Override
    public String getName() {
        return "memoryTool";
    }

    @Override
    public String getDescription() {
        return "查询、保存智能体的记忆内容";
    }

    @Tool("查询智能体所有记忆内容，此方法一次性查询所有记忆内容，可能内容较多，可以先查询记忆分类，再查询具体记忆内容。(agentId 智能体ID)")
    public String queryAgentMemory(Long agentId){
        return longTermMemoryService.getMemoryTree(agentId.toString());
    }
    @Tool("查询智能体记忆分类。(agentId 智能体ID)")
    public String queryAgentRootMemory(Long agentId){
        return longTermMemoryService.getRootMemory(agentId.toString());
    }
    @Tool("此方法查询指定父记忆ID的记忆内容。(agentId 智能体ID，parentId 父记忆ID)")
    public String queryAgentMemoryByParentId(Long agentId,Long parentId){
        return longTermMemoryService.getMemoryTree(agentId.toString(),parentId);
    }
    @Tool("保存智能体记忆内容，要先拿到所有记忆内容，找到最适合的分类进行存储，如果没有适合分类，先新增一个分类。(agentId 智能体ID，memory 记忆内容，parentId 父记忆ID)")
    public String saveAgentMemory(Long agentId,String memory,Long parentId){
        return longTermMemoryService.saveMemory(agentId.toString(),memory,parentId);
    }
    @Tool("删除智能体记忆内容，此方法删除指定id的记忆内容。(id 记忆ID)")
    public String deleteAgentMemory(Long id){
        longTermMemoryService.deleteMemory(id);
        return "成功";
    }
}
