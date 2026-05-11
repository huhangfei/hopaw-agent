package com.agent.hopaw.tools;

import com.agent.hopaw.constant.LongTermMemoryTypeEnum;
import com.agent.hopaw.service.LongTermMemoryService;
import com.agent.hopaw.service.SysConfigService;
import com.agent.hopaw.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
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
        return "查询记忆、保存记忆、删除记忆、记忆整理规则等相关操作";
    }

    @Override
    public String getIcon() {
        return "memory-tool";
    }

    @Override
    public String getKeyword() {
        return "记忆";
    }

    @Tool("获取用户记忆整理规则")
    public String getMemoryOrganizingRules() {
        return longTermMemoryService.getMemoryOrganizingRules();
    }

    @Tool("查询用户画像记忆内容")
    public String queryUserProfileMemory(InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        return longTermMemoryService.queryUserProfileMemoryContent(invocationParametersWrapper.getUserId());
    }
    @Tool("查询用户任务记录记忆，如果不是特别需要不要包含详情")
    public String queryUserTaskRecordsMemory(@P(description="是否包括详情",required = false) Boolean includeDetail, InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        String memory = longTermMemoryService.queryUserTaskRecordsMemoryContent(invocationParametersWrapper.getAgentId(), invocationParametersWrapper.getUserId(), includeDetail);
        return memory;
    }
    @Tool("查询用户扩展知识记忆，如果不是特别需要不要包含详情")
    public String queryUserExpandKnowledgeMemory(@P(description="是否包括详情",required = false) Boolean includeDetail, InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        String memory = longTermMemoryService.queryUserExpandKnowledgeMemoryContent(invocationParametersWrapper.getAgentId(), invocationParametersWrapper.getUserId(), includeDetail);
        return memory;
    }
    @Tool("查询用户记忆详细内容,根据指定Id查询")
    public String queryUserMemoryById(@P(description="记忆Id")Long id){
        return longTermMemoryService.getMemoryContentById(id);
    }
    @Tool("删除用户记忆内容，根据指定id删除")
    public String deleteUserMemoryById(@P(description="记忆Id") Long id){
        longTermMemoryService.deleteMemory(id);
        return "成功";
    }
    @Tool("保存用户记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。")
    public String saveUserMemory(@P(description = "记忆类型:userProfile、taskRecords、expandKnowledge",required = false) String memoryType,
                                 @P(description = "记忆概要") String summary,
                                 @P(description = "记忆内容") String memory,
                                 @P(description = "记忆Id，如果传入记忆Id则为更新，如果不传记忆Id则为新增。",required = false) Long id,
                                 InvocationParameters invocationParameters) {
        return longTermMemoryService.saveMemory(memoryType,summary,memory,id,invocationParameters);
    }

}
