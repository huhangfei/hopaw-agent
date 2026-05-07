package com.agent.hopaw.tools;

import com.agent.hopaw.service.LongTermMemoryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import com.agent.hopaw.util.InvocationParametersUtil;
import dev.langchain4j.invocation.InvocationParameters;
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

    @Override
    public String getIcon() {
        return "memory-tool";
    }

    @Tool("查询智能体所有记忆内容，此方法一次性查询所有记忆内容，可能内容较多，可以先查询记忆分类，再查询具体记忆内容。")
    public String queryAgentMemory(InvocationParameters invocationParameters){
        return longTermMemoryService.getMemoryTree(InvocationParametersUtil.getAgentId(invocationParameters),InvocationParametersUtil.getUserId(invocationParameters));
    }
    @Tool("查询智能体记忆分类。")
    public String queryAgentRootMemory(InvocationParameters invocationParameters){
        return longTermMemoryService.getRootMemory(InvocationParametersUtil.getAgentId(invocationParameters),InvocationParametersUtil.getUserId(invocationParameters));
    }
    @Tool("此方法查询指定父记忆ID的记忆内容。")
    public String queryAgentMemoryByParentId(@P(description="父记忆ID")Long parentId,InvocationParameters invocationParameters){
        return longTermMemoryService.getMemoryTree(InvocationParametersUtil.getMemoryId(invocationParameters),parentId);
    }
    @Tool("保存智能体记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增")
    public String saveAgentMemory(@P(description="记忆内容")String memory,@P(description="父记忆ID",required = false)Long parentId,@P(description = "记忆Id",required = false) Long id,InvocationParameters invocationParameters){
        return longTermMemoryService.saveMemory(memory,parentId,id,invocationParameters);
    }
    @Tool("删除智能体记忆内容，此方法删除指定id的记忆内容。")
    public String deleteAgentMemory(@P(description="记忆ID") Long id){
        longTermMemoryService.deleteMemory(id);
        return "成功";
    }
}
