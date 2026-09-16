package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PromptPageController {

    @GetMapping("/prompts")
    public String index(Model model) {
        model.addAttribute("activePage", "prompts");
        model.addAttribute("activeTab", "prompts");
        return "prompts";
    }
}
