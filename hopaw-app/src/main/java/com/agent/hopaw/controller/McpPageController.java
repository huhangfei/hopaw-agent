package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/mcp")
public class McpPageController {

    @GetMapping
    public String mcpPage(Model model) {
        model.addAttribute("activePage", "mcp");
        return "mcp";
    }
}