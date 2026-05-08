package com.agent.hopaw.controller;

import com.agent.hopaw.model.ResponseBean;
import com.agent.hopaw.util.MailUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class SettingsController {

    private final MailUtil mailUtil;

    public SettingsController(MailUtil mailUtil) {
        this.mailUtil = mailUtil;
    }

    @GetMapping("/settings")
    public String settingsPage() {
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
