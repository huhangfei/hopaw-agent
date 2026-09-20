package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.IToolSetService;
import com.agent.hopaw.infra.tool.AgentTool;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

/**
 * 工具集管理页面与查询接口（二级）。
 *
 * <p>插件级操作（安装/升级/卸载/导出/启停）已迁至 {@link PluginController}（{@code /plugins}）。
 * 本控制器只负责工具集（{@link AgentTool}）层面的展示与查询。</p>
 */
@Controller
@RequestMapping("/tools")
public class AgentToolController {

    private final IToolSetService toolSetService;

    public AgentToolController(IToolSetService toolSetService) {
        this.toolSetService = toolSetService;
    }

    /** 工具管理页：平铺展示所有工具集（内置 + 插件），详情含工具集级/方法级开关。 */
    @GetMapping
    public String toolsPage(Model model) {
        List<ToolSetInfo> toolSets = toolSetService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        model.addAttribute("activePage", "tools");
        model.addAttribute("activeTab", "tools");
        return "tools";
    }

    /** 工具集列表（前端 index 页工具图标/配置按钮、智能体表单等消费）。 */
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseBean list() {
        return ResponseBean.success(toolSetService.getToolSets());
    }

    /**
     * 工具集配置信息：是否存在配置项及其配置键（工具级）。
     */
    @GetMapping("/api/toolset-config-info")
    @ResponseBody
    public ResponseBean toolSetConfigInfo(@RequestParam String toolSetName) {
        AgentTool tool = toolSetService.getAgentTool(toolSetName);
        if (tool != null && !tool.getConfigItems().isEmpty()) {
            List<String> configKeys = tool.getConfigItems().stream()
                    .map(item -> tool.getConfigPrefix() + item.getKey())
                    .toList();
            return ResponseBean.success(Map.of(
                    "hasConfig", true,
                    "toolSetName", tool.getName(),
                    "configKeys", configKeys
            ));
        }
        return ResponseBean.success(Map.of("hasConfig", false));
    }
}
