package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.tool.AgentToolService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/tools")
public class AgentToolController {

    private final AgentToolService agentToolService;

    public AgentToolController(AgentToolService agentToolService) {
        this.agentToolService = agentToolService;
    }

    @GetMapping
    public String toolsPage(Model model) {
        model.addAttribute("toolSets", agentToolService.getToolSets());
        return "tools";
    }

    @GetMapping("/api/list")
    @ResponseBody
    public ResponseBean list() {
        return ResponseBean.success(agentToolService.getToolSets());
    }
}
