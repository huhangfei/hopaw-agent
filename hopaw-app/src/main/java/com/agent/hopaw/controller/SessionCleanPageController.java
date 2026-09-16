package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SessionCleanPageController {

    @GetMapping("/session-clean")
    public String page(Model model) {
        model.addAttribute("activePage", "session-clean");
        model.addAttribute("activeTab", "session-clean");
        return "session-clean";
    }
}
