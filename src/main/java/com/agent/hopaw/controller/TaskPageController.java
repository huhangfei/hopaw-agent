package com.agent.hopaw.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tasks")
public class TaskPageController {

    @GetMapping
    public String tasksPage() {
        return "tasks";
    }
}
