package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AvatarRankPageController {

    @GetMapping("/avatar-rank")
    public String page(Model model) {
        model.addAttribute("activePage", "avatar-rank");
        return "avatar-rank";
    }
}
