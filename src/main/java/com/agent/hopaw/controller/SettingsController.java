package com.agent.hopaw.controller;

import com.agent.hopaw.model.ResponseBean;
import com.agent.hopaw.model.SysConfig;
import com.agent.hopaw.service.SysConfigService;
import com.agent.hopaw.util.MailUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class SettingsController {

    private final SysConfigService sysConfigService;
    private final MailUtil mailUtil;

    public SettingsController(SysConfigService sysConfigService, MailUtil mailUtil) {
        this.sysConfigService = sysConfigService;
        this.mailUtil = mailUtil;
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

    @PostMapping("/api/mail/test")
    @ResponseBody
    public ResponseBean testMail() {
        try {
            boolean ok = mailUtil.testConnection();
            return ok ? ResponseBean.success() : ResponseBean.fail("连接失败，请检查配置");
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }
}
