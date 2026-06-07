package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.biz.util.MailUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class SettingsController {

    private static final Map<String, String[]> TAB_RESOURCES = Map.of(
        "memory",       new String[] {"/js/page/settings-memory.js", null},
        "mail",         new String[] {"/js/page/settings-mail.js", null},
        "tts",          new String[] {"/js/page/settings-tts.js", "/css/page/settings-tts.css"},
        "plugin-store", new String[] {"/js/page/settings-plugin-store.js", null}
    );

    private final MailUtil mailUtil;

    public SettingsController(MailUtil mailUtil) {
        this.mailUtil = mailUtil;
    }

    @GetMapping("/settings")
    public String settingsPage() {
        return "redirect:/settings/memory";
    }

    @GetMapping("/settings/{tab}")
    public String settingsTabPage(@PathVariable String tab, Model model) {
        model.addAttribute("currentTab", tab);

        String[] resources = TAB_RESOURCES.get(tab);
        if (resources != null) {
            model.addAttribute("tabJs", resources[0]);
            model.addAttribute("tabCss", resources[1]);
        }

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
