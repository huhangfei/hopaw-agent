package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.ToolConfigService;
import com.agent.hopaw.infra.service.IToolSetService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tool-config")
public class ToolConfigController {

    private final ToolConfigService toolConfigService;
    private final IToolSetService agentToolService;

    public ToolConfigController(ToolConfigService toolConfigService, IToolSetService agentToolService) {
        this.toolConfigService = toolConfigService;
        this.agentToolService = agentToolService;
    }

    /** 工具配置页：无模板独立页面（可嵌入弹框 iframe），表单内异步提交。 */
    @GetMapping("/{toolName}")
    public String configPage(@PathVariable String toolName, Model model) {
        Map<String, Object> config = toolConfigService.getToolConfig(toolName);
        model.addAttribute("config", config);
        model.addAttribute("toolName", toolName);
        return "tool-config";
    }

    /** 异步保存工具配置（JSON）。 */
    @PostMapping("/api/{toolName}")
    @ResponseBody
    public ResponseBean saveConfigApi(@PathVariable String toolName,
                                      @RequestParam Map<String, String> params) {
        try {
            toolConfigService.saveToolConfig(toolName, params);
            return ResponseBean.success("配置保存成功");
        } catch (IllegalArgumentException e) {
            return ResponseBean.fail(e.getMessage());
        } catch (Exception e) {
            return ResponseBean.fail("保存失败：" + e.getMessage());
        }
    }

    @GetMapping
    public String index(Model model) {
        List<ToolSetInfo> toolSets = agentToolService.getToolSets();
        model.addAttribute("toolSets", toolSets);
        model.addAttribute("activePage", "tools");
        return "tool-config-index";
    }
}
