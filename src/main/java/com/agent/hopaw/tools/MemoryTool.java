package com.agent.hopaw.tools;

import com.agent.hopaw.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.model.LongTermMemory;
import com.agent.hopaw.service.LongTermMemoryService;
import com.agent.hopaw.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Tool("查询用户画像记忆内容")
    public String queryUserProfileMemory(InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        LongTermMemory memory = longTermMemoryService.getMemoryByAgentIdAndUserIdAndMemoryType(invocationParametersWrapper.getAgentId(), invocationParametersWrapper.getUserId(), LongTermMemoryTypeEnum.USER_PROFILE);
        return longTermMemoryService.buildMemoryContent(memory, true);
    }
    @Tool("查询所有记忆，如果不是特别需要不要包含详情")
    public String queryAllMemory(@P(description="是否包括详情",required = false) Boolean includeDetail, InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);

        List<LongTermMemory> memories = longTermMemoryService.getRecentActivityMemoriesByAgentIdAndUserId(invocationParametersWrapper.getAgentId(), invocationParametersWrapper.getUserId());
        return longTermMemoryService.buildMemoryContent(memories,includeDetail);
    }
    @Tool("查询记忆详细内容,根据指定Id查询")
    public String queryMemoryById(@P(description="记忆Id")Long id,InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        LongTermMemory memory = longTermMemoryService.getMemoryById(id);
        String memoryContent = longTermMemoryService.buildMemoryContent(memory, true);
        if(memory!=null){
            List<LongTermMemory> childMemories = longTermMemoryService.getChildMemories(memory.getId());
            if(childMemories.isEmpty()){
                memoryContent+="\n子记忆:\n"+longTermMemoryService.buildMemoryContent(childMemories,false);
            }
        }
        return memoryContent;
    }
    @Tool("删除记忆内容，根据指定id删除")
    public String deleteAgentMemory(@P(description="记忆Id") Long id){
        longTermMemoryService.deleteMemory(id);
        return "成功";
    }
    @Tool("保存记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。")
    public String saveMemory(@P(description = "记忆类型:userProfile、taskRecords、expandKnowledge",required = false) String memoryType,
                             @P(description = "记忆概要") String summary,
                             @P(description = "记忆内容") String memory,
                             @P(description = "记忆Id，如果传入记忆Id则为更新，如果不传记忆Id则为新增。",required = false) Long id,
                             InvocationParameters invocationParameters) {
        return longTermMemoryService.saveMemory(memoryType,summary,memory,id,invocationParameters);
    }

}
