package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IpBlacklistPageController {

    @GetMapping("/ip-blacklist")
    public String page(Model model) {
        model.addAttribute("activePage", "ip-blacklist");
        model.addAttribute("activeTab", "ip-blacklist");
        return "ip-blacklist";
    }
}
