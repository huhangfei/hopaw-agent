package com.agent.hopaw.controller;

import com.agent.hopaw.model.SysConfig;
import com.agent.hopaw.service.SysConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class SettingsController {

    private final SysConfigService sysConfigService;

    public SettingsController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @GetMapping("/settings")
    public String settingsPage(Model model) {
        List<SysConfig> configs = sysConfigService.getAll();
        String memoryEnabled = "false";
        String memoryModelId = "";
        String memoryFrequency = "5";
        for (SysConfig c : configs) {
            switch (c.getConfigKey()) {
                case "memory_enabled" -> memoryEnabled = c.getConfigValue();
                case "memory_ai_model_id" -> memoryModelId = c.getConfigValue();
                case "memory_frequency" -> memoryFrequency = c.getConfigValue();
            }
        }
        model.addAttribute("memoryEnabled", memoryEnabled);
        model.addAttribute("memoryModelId", memoryModelId);
        model.addAttribute("memoryFrequency", memoryFrequency);
        return "settings";
    }
}
