package com.agent.hopaw.biz.tool.agenttoolset;

import com.agent.hopaw.infra.model.dto.ToolInfo;
import com.agent.hopaw.infra.model.dto.ToolParamInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智能体工具集工具：查询当前系统中所有可用的智能体工具集与工具明细，
 * 便于智能体在执行前了解自身可用的能力清单（含工具名称、描述、参数与安全级别）。
 */
@Component("agentToolSetTool")
public class AgentToolSetTool implements AgentTool {

    private final IAgentToolService agentToolService;

    public AgentToolSetTool(IAgentToolService agentToolService) {
        this.agentToolService = agentToolService;
    }

    @Override
    public String getName() {
        return "agentToolSetTool";
    }

    @Override
    public String getDescription() {
        return "智能体工具集查询：查询当前所有可用的智能体工具集与工具明细，或按工具集名称查询单个工具集详情";
    }

    @Override
    public String getIcon() {
        return "agent-tool-set-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "工具集,智能体工具,查询工具";
    }

    /**
     * 查询所有智能体工具集（含每个工具集下的工具名称、描述与安全级别）。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询所有智能体工具", "查询当前系统中所有可用的智能体工具集及各工具集内的工具清单（名称、描述、安全级别）"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String findAllAgentTools(InvocationParameters invocationParameters) {
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        if (toolSets == null || toolSets.isEmpty()) {
            return "成功：当前系统中没有可用的智能体工具集";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(toolSets.size()).append(" 个工具集：\n");
        for (ToolSetInfo toolSet : toolSets) {
            sb.append("\n【工具集】").append(toolSet.getName());
            if (toolSet.getSource() != null) {
                sb.append("（").append(toolSet.getSource().getDescription()).append("）");
            }
            sb.append("\n");
            sb.append("说明：").append(toolSet.getDescription() != null ? toolSet.getDescription() : "").append("\n");
            List<ToolInfo> tools = toolSet.getTools();
            if (tools == null || tools.isEmpty()) {
                sb.append("（该工具集下暂无可用工具）\n");
                continue;
            }
            for (ToolInfo tool : tools) {
                sb.append("  - 工具名称：").append(tool.getName()).append("\n");
                sb.append("    描述：").append(tool.getDescription() != null ? tool.getDescription() : "").append("\n");
                sb.append("    安全级别：").append(securityLevelText(tool.getSecurityLevel())).append("\n");
            }
        }
        return "成功：\n" + sb;
    }

    /**
     * 按工具集名称查询单个工具集详情（含工具参数明细）。
     */
    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询智能体工具集详情", "按工具集名称查询该工具集的详细信息和工具参数明细"}, searchBehavior = SearchBehavior.ALWAYS_VISIBLE)
    public String findAgentToolSetDetail(@P(value = "工具集名称，例如 projectTool、workflowTaskTool", required = false) String toolSetName,
                                         InvocationParameters invocationParameters) {
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        if (toolSets == null || toolSets.isEmpty()) {
            return "失败：当前系统中没有可用的智能体工具集";
        }
        if (toolSetName == null || toolSetName.trim().isEmpty()) {
            return "失败：工具集名称不能为空，可先调用「查询所有智能体工具」获取工具集清单";
        }
        String target = toolSetName.trim();
        for (ToolSetInfo toolSet : toolSets) {
            if (!target.equals(toolSet.getName())) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("工具集：").append(toolSet.getName());
            if (toolSet.getSource() != null) {
                sb.append("（").append(toolSet.getSource().getDescription()).append("）");
            }
            sb.append("\n");
            sb.append("说明：").append(toolSet.getDescription() != null ? toolSet.getDescription() : "").append("\n");
            List<ToolInfo> tools = toolSet.getTools();
            if (tools == null || tools.isEmpty()) {
                sb.append("（该工具集下暂无可用工具）\n");
                return "成功：\n" + sb;
            }
            for (ToolInfo tool : tools) {
                sb.append("\n工具名称：").append(tool.getName()).append("\n");
                sb.append("描述：").append(tool.getDescription() != null ? tool.getDescription() : "").append("\n");
                sb.append("安全级别：").append(securityLevelText(tool.getSecurityLevel())).append("\n");
                List<ToolParamInfo> params = tool.getParameters();
                if (params != null && !params.isEmpty()) {
                    sb.append("参数：\n");
                    for (ToolParamInfo param : params) {
                        sb.append("  - ").append(param.getName())
                                .append("（").append(param.getType()).append("）")
                                .append(param.isRequired() ? " [必填] " : " [可选] ")
                                .append(param.getDescription() != null ? param.getDescription() : "")
                                .append("\n");
                    }
                }
            }
            return "成功：\n" + sb;
        }
        return "失败：工具集不存在：" + target + "，可先调用「查询所有智能体工具」获取工具集清单";
    }

    /** 安全级别转中文描述 */
    private String securityLevelText(ToolSecurityLevel.Level level) {
        if (level == null) {
            return "未标注";
        }
        switch (level) {
            case SAFE:
                return "SAFE（安全，免审批）";
            case PARAM_REQUIRE_APPROVAL:
                return "PARAM_REQUIRE_APPROVAL（参数需审批）";
            case ALL_REQUIRE_APPROVAL:
                return "ALL_REQUIRE_APPROVAL（全部需审批）";
            default:
                return level.name();
        }
    }
}
