package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/accounts")
public class AccountPageController {

    @GetMapping
    public String accountsPage(Model model) {
        model.addAttribute("activePage", "accounts");
        return "accounts";
    }
}
