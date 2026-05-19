package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.tool.IAgentToolService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/tools")
public class AgentToolController {

    private final IAgentToolService IAgentToolService;

    public AgentToolController(IAgentToolService IAgentToolService) {
        this.IAgentToolService = IAgentToolService;
    }

    @GetMapping
    public String toolsPage(Model model) {
        model.addAttribute("toolSets", IAgentToolService.getToolSets());
        return "tools";
    }

    @GetMapping("/api/list")
    @ResponseBody
    public ResponseBean list() {
        return ResponseBean.success(IAgentToolService.getToolSets());
    }

    @PostMapping("/api/unload")
    @ResponseBody
    public ResponseBean unloadPlugin(@RequestParam String jarFileName) {
        boolean result = IAgentToolService.unloadPlugin(jarFileName);
        if (result) {
            return ResponseBean.success("插件卸载成功");
        }
        return ResponseBean.fail("插件不存在或卸载失败");
    }
}
