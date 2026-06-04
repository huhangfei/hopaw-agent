package com.agent.hopaw.biz.tool.memory;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.memory.ILongTermMemoryProvider;
import com.agent.hopaw.infra.memory.ILongTermMemoryService;
import com.agent.hopaw.infra.memory.IVectorMemoryService;
import com.agent.hopaw.infra.model.dto.MemorySearchResult;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
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

    private final ILongTermMemoryProvider longTermMemoryProvider;

    public MemoryTool(ILongTermMemoryProvider longTermMemoryProvider) {
        this.longTermMemoryProvider = longTermMemoryProvider;
    }

    @Override
    public String getName() {
        return "memoryTool";
    }

    @Override
    public String getDescription() {
        return "查询记忆、保存记忆等相关操作";
    }

    @Override
    public String getIcon() {
        return "memory-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "用户记忆";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"保存用户记忆", "保存用户记忆,如果有记忆Id则为更新，如果记忆Id不存在则为新增。"},searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String saveUserMemory(@P(description = "记忆类型:userProfile、taskRecords、empiricalKnowledge",required = false) String memoryType,
                                 @P(description = "记忆概要") String summary,
                                 @P(description = "记忆内容") String memory,
                                 @P(description = "记忆Id，如果传入记忆Id则为更新，如果不传记忆Id则为新增。",required = false) Long id,
                                 InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        longTermMemoryProvider.saveUserMemory(wrapper.getSessionId(),wrapper.getUserId(), UserMemoryTypeEnum.fromCode(memoryType),summary,memory);
        return "记忆保存成功";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"搜索用户记忆", "语义搜索历史记忆，根据查询关键词查找最相关的记忆内容"},searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String searchUserMemory(@P(description = "搜索查询关键词") String query,
                                   @P(description = "最大返回结果数量，默认5", required = false) Integer maxResults,
                                   InvocationParameters invocationParameters) {
        InvocationParametersWrapper wrapper = InvocationParametersWrapper.create(invocationParameters);
        String userId = wrapper.getUserId();
        int maxResultsVal = maxResults != null ? maxResults : 5;
        List<MemorySearchResult> results =longTermMemoryProvider.queryUserMemory(userId,query, maxResultsVal);
        if (results.isEmpty()) {
            return "未找到相关历史记忆";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("语义搜索找到 ").append(results.size()).append(" 条相关历史记忆：\n\n");

        int idx = 1;
        for (MemorySearchResult result : results) {
            sb.append("[").append(idx++).append("] 相似度: ").append(String.format("%.4f", result.getScore())).append("\n");
            sb.append("记忆类型: ").append(result.getMemoryType()).append("\n");
            sb.append("内容: ").append(result.getText()).append("\n");
            sb.append("时间: ").append(result.getMemoryDate()).append("\n");
            sb.append("---\n");
        }

        return sb.toString();
    }

}
