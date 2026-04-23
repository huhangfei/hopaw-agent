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

    @Tool("查询智能体所有记忆内容")
    public String queryGlobalMemory(){
        return longTermMemoryService.buildMemoryTree(LongTermMemoryService.GLOBAL_IDENTITY);
    }
    @Tool("保存智能体记忆内容，要先拿到所有记忆内容，找到最适合的分类进行存储，如果没有适合分类，先新增一个分类。(memory 记忆内容，parentId 父记忆ID)")
    public String saveGlobalMemory(String memory,Long parentId){
        return longTermMemoryService.saveMemory(LongTermMemoryService.GLOBAL_IDENTITY,memory,parentId);
    }
}
