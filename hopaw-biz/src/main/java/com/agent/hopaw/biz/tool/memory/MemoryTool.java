package com.agent.hopaw.biz.tool.memory;

import com.agent.hopaw.infra.constant.VectorMemoryTypeEnum;
import com.agent.hopaw.infra.memory.LongTermMemoryService;
import com.agent.hopaw.infra.memory.IVectorMemoryService;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体记忆工具，所有工具都是不需要通过搜索就永远可见的
 * @author hhf
 */
@Component("memoryTool")
public class MemoryTool implements AgentTool {

    private final LongTermMemoryService longTermMemoryService;
    private final IVectorMemoryService vectorMemoryService;

    public MemoryTool(LongTermMemoryService longTermMemoryService, IVectorMemoryService vectorMemoryService) {
        this.longTermMemoryService = longTermMemoryService;
        this.vectorMemoryService = vectorMemoryService;
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
        return "用户记忆";
    }

    @Tool(value = "获取用户记忆整理规则",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String getMemoryOrganizingRules() {
        return longTermMemoryService.getMemoryOrganizingRules();
    }

    @Tool(value = "查询用户画像记忆内容",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String queryUserProfileMemory(InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        return longTermMemoryService.queryUserProfileMemoryContent(invocationParametersWrapper.getUserId());
    }
    @Tool(value = "查询用户任务记录记忆，如果不是特别需要不要包含详情",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String queryUserTaskRecordsMemory(@P(description="是否包括详情",required = false) Boolean includeDetail, InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        String memory = longTermMemoryService.queryUserTaskRecordsMemoryContent(invocationParametersWrapper.getAgentId(), invocationParametersWrapper.getUserId(), includeDetail);
        return memory;
    }
    @Tool(value = "查询用户扩展知识记忆，如果不是特别需要不要包含详情",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String queryUserExpandKnowledgeMemory(@P(description="是否包括详情",required = false) Boolean includeDetail, InvocationParameters invocationParameters){
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        String memory = longTermMemoryService.queryUserExpandKnowledgeMemoryContent(invocationParametersWrapper.getAgentId(), invocationParametersWrapper.getUserId(), includeDetail);
        return memory;
    }
    @Tool(value = "查询用户记忆详细内容,根据指定Id查询",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String queryUserMemoryById(@P(description="记忆Id")Long id){
        return longTermMemoryService.getMemoryContentById(id);
    }
    @Tool(value = "删除用户记忆内容，根据指定id删除",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String deleteUserMemoryById(@P(description="记忆Id") Long id){
        longTermMemoryService.deleteMemory(id);
        return "成功";
    }
    @Tool(value = "保存用户记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String saveUserMemory(@P(description = "记忆类型:userProfile、taskRecords、expandKnowledge",required = false) String memoryType,
                                 @P(description = "记忆概要") String summary,
                                 @P(description = "记忆内容") String memory,
                                 @P(description = "记忆Id，如果传入记忆Id则为更新，如果不传记忆Id则为新增。",required = false) Long id,
                                 InvocationParameters invocationParameters) {
        return longTermMemoryService.saveMemory(memoryType,summary,memory,id,invocationParameters);
    }

    @Tool(value = "语义搜索历史记忆，根据查询关键词在向量库中查找最相关的历史对话和记忆内容",searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String searchHistoricalMemory(@P(description = "搜索查询关键词") String query,
                                         @P(description = "最大返回结果数量，默认5", required = false) Integer maxResults,
                                         @P(description = "最低相似度阈值(0-1)，默认0.5", required = false) Double minScore,
                                         @P(description = "限定记忆类型(taskRecords/chatHistory)，不限定不填", required = false) String memoryType,
                                         InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        Long agentId = wrapper.getAgentId();
        String userId = wrapper.getUserId();

        int maxResultsVal = maxResults != null ? maxResults : 5;
        double minScoreVal = minScore != null ? minScore : 0.5;

        List<VectorSearchResult> results =
                vectorMemoryService.search(query, agentId, userId, VectorMemoryTypeEnum.fromCode(memoryType), maxResultsVal, minScoreVal);

        if (results.isEmpty()) {
            return "向量库中未找到相关历史记忆";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("语义搜索找到 ").append(results.size()).append(" 条相关历史记忆：\n\n");

        int idx = 1;
        for (VectorSearchResult result : results) {
            sb.append("[").append(idx++).append("] 相似度: ").append(String.format("%.4f", result.getScore())).append("\n");
            sb.append("记忆类型: ").append(result.getMemoryType()).append("\n");
            sb.append("内容: ").append(result.getText()).append("\n");
            sb.append("---\n");
        }

        return sb.toString();
    }

}
