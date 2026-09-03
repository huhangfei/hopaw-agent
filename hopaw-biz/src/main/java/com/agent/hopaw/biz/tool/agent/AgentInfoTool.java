package com.agent.hopaw.biz.tool.agent;

import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.AiModel;
import com.agent.hopaw.infra.service.IAgentService;
import com.agent.hopaw.infra.service.IAiModelService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能体信息工具：查询当前系统中的智能体清单（编号、名称、描述、AI模型、可用工具等）。
 * 便于智能体在创建/更新工作流任务或项目时选择合适的执行智能体。
 * 智能体数据不做用户归属隔离（跨用户共享协作）。
 */
@Component("agentInfoTool")
public class AgentInfoTool implements AgentTool {

    private final IAgentService agentService;
    private final IAiModelService aiModelService;

    public AgentInfoTool(IAgentService agentService, IAiModelService aiModelService) {
        this.agentService = agentService;
        this.aiModelService = aiModelService;
    }

    @Override
    public String getName() {
        return "agentInfoTool";
    }

    @Override
    public String getDescription() {
        return "智能体信息查询：分页查询当前系统中的智能体清单（编号、名称、描述），可按名称关键字过滤";
    }

    @Override
    public String getIcon() {
        return "agent-info-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "智能体列表,查询智能体,智能体信息";
    }

    /**
     * 分页查询智能体列表，支持名称/描述关键字过滤（不做用户归属隔离）。
     * 返回编号、名称、描述、AI模型、可用工具等信息。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询智能体列表", "分页查询当前系统中的智能体清单（编号、名称、描述、AI模型、可用工具），可按名称或描述关键字过滤，用于选择任务执行智能体等场景"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String findAgents(@P(value = "名称或描述关键字，空表示不过滤", required = false) String keyword,
                             @P(value = "页码，从1开始，默认1", required = false) Integer page,
                             @P(value = "每页数量，默认20，最大100", required = false) Integer size,
                             InvocationParameters invocationParameters) {
        int pageNo = (page == null || page < 1) ? 1 : page;
        int pageSize = (size == null || size < 1) ? 20 : Math.min(size, 100);
        String kw = keyword == null ? "" : keyword.trim();

        List<Agent> agents = agentService.getAllAgents();
        if (agents == null || agents.isEmpty()) {
            return "成功：当前系统中没有智能体";
        }

        // 关键字过滤：匹配名称或描述（不区分大小写）
        List<Agent> filtered = agents;
        if (!kw.isEmpty()) {
            String lower = kw.toLowerCase();
            filtered = agents.stream()
                    .filter(a -> (a.getName() != null && a.getName().toLowerCase().contains(lower))
                            || (a.getDescription() != null && a.getDescription().toLowerCase().contains(lower)))
                    .collect(Collectors.toList());
        }
        if (filtered.isEmpty()) {
            return "成功：没有匹配「" + kw + "」的智能体";
        }

        // 内存分页（智能体数量有限）
        int total = filtered.size();
        int from = (pageNo - 1) * pageSize;
        if (from >= total) {
            return "成功：共 " + total + " 个智能体，页码 " + pageNo + " 超出范围（最大页码 " + ((total + pageSize - 1) / pageSize) + "）";
        }
        int to = Math.min(from + pageSize, total);
        List<Agent> pageList = filtered.subList(from, to);

        // 模型编号->名称缓存，避免重复查询
        Map<Long, String> modelNames = new HashMap<>();
        for (Agent a : pageList) {
            if (a.getAiModelId() != null && !modelNames.containsKey(a.getAiModelId())) {
                try {
                    AiModel model = aiModelService.findById(a.getAiModelId());
                    modelNames.put(a.getAiModelId(), model != null && model.getModelAlias() != null && !model.getModelAlias().isEmpty()
                            ? model.getModelAlias() : (model != null ? model.getModelName() : null));
                } catch (Exception e) {
                    modelNames.put(a.getAiModelId(), null);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(total).append(" 个智能体，当前第 ").append(pageNo).append(" 页（每页 ").append(pageSize).append(" 条）：\n");
        for (Agent a : pageList) {
            sb.append("智能体编号：").append(a.getId())
                    .append("，名称：").append(a.getName() != null ? a.getName() : "")
                    .append("，描述：").append(a.getDescription() != null ? a.getDescription() : "（无）")
                    .append("\n");
            // AI模型
            String modelName = a.getAiModelId() != null ? modelNames.get(a.getAiModelId()) : null;
            sb.append("  AI模型：").append(modelName != null ? modelName + "（#" + a.getAiModelId() + "）" : "未配置").append("\n");
            // 可用工具
            if (Boolean.TRUE.equals(a.getEnableAllTools())) {
                sb.append("  可用工具：全部工具\n");
            } else {
                sb.append("  可用工具：").append(a.getTools() != null && !a.getTools().trim().isEmpty() ? a.getTools() : "无").append("\n");
            }
            // 思考模式
            sb.append("  思考模式：").append(Boolean.TRUE.equals(a.getEnableThinking()) ? "已开启" : "未开启").append("\n");
        }
        return "成功：\n" + sb;
    }
}
