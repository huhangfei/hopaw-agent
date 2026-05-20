package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.ToolConfigService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.AgentToolService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tool-config")
public class ToolConfigController {

    private final ToolConfigService toolConfigService;
    private final AgentToolService agentToolService;

    public ToolConfigController(ToolConfigService toolConfigService, AgentToolService agentToolService) {
        this.toolConfigService = toolConfigService;
        this.agentToolService = agentToolService;
    }

    @GetMapping("/{toolName}")
    public String configPage(@PathVariable String toolName, Model model) {
        Map<String, Object> config = toolConfigService.getToolConfig(toolName);
        model.addAttribute("config", config);
        model.addAttribute("toolName", toolName);
        return "tool-config";
    }

    @PostMapping("/{toolName}")
    public String saveConfig(@PathVariable String toolName,
                             @RequestParam Map<String, String> params,
                             RedirectAttributes redirectAttributes) {
        try {
            toolConfigService.saveToolConfig(toolName, params);
            redirectAttributes.addFlashAttribute("success", "配置保存成功！");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "保存失败：" + e.getMessage());
        }
        return "redirect:/tool-config/" + toolName;
    }

    @GetMapping
    public String index(Model model) {
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        return "tool-config-index";
    }
}
