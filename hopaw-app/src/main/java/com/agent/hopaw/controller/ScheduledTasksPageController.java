package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ScheduledTasksPageController {

    @GetMapping("/scheduled-tasks")
    public String page(Model model) {
        model.addAttribute("activePage", "scheduled-tasks");
        model.addAttribute("activeTab", "scheduled-tasks");
        return "scheduled-tasks";
    }
}
