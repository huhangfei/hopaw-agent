package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginLogPageController {

    @GetMapping("/login-log")
    public String page(Model model) {
        model.addAttribute("activePage", "login-log");
        model.addAttribute("activeTab", "login-log");
        return "login-log";
    }
}
