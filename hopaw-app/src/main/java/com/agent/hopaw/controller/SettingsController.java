package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.biz.util.MailUtil;
import com.agent.hopaw.util.CurrentUser;
import com.agent.hopaw.util.PasswordUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class SettingsController {

    private static final Map<String, String[]> TAB_RESOURCES = new HashMap<>() {{
        put("memory",       new String[] {"/js/page/settings-memory.js", null});
        put("mail",         new String[] {"/js/page/settings-mail.js", null});
        put("tts",          new String[] {"/js/page/settings-tts.js", "/css/page/settings-tts.css"});
        put("plugin-store", new String[] {"/js/page/settings-plugin-store.js", null});
        put("account",      new String[] {"/js/page/settings-account.js", null});
    }};

    private final MailUtil mailUtil;
    private final AccountService accountService;

    public SettingsController(MailUtil mailUtil, AccountService accountService) {
        this.mailUtil = mailUtil;
        this.accountService = accountService;
    }

    @GetMapping("/settings")
    public String settingsPage() {
        return "redirect:/settings/memory";
    }

    @GetMapping("/settings/{tab}")
    public String settingsTabPage(@PathVariable String tab, Model model, HttpServletRequest request) {
        model.addAttribute("currentTab", tab);

        String[] resources = TAB_RESOURCES.get(tab);
        if (resources != null) {
            model.addAttribute("tabJs", resources[0]);
            model.addAttribute("tabCss", resources[1]);
        }

        // 账号设置 tab 需要当前登录账号信息
        if ("account".equals(tab)) {
            String userId = CurrentUser.require(request);
            Account account = accountService.getByUserId(userId);
            model.addAttribute("currentAccount", account);
        }

        return "settings";
    }

    /**
     * 更新当前登录账号信息
     */
    @PutMapping("/api/settings/account")
    @ResponseBody
    public ResponseBean updateCurrentAccount(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String userId = CurrentUser.require(request);
        Account account = accountService.getByUserId(userId);
        if (account == null) {
            return ResponseBean.fail("账户不存在");
        }

        String username = body.get("username");
        String nickname = body.get("nickname");
        if (username != null && !username.isBlank()) {
            account.setUsername(username);
        }
        if (nickname != null) {
            account.setNickname(nickname);
        }

        accountService.update(account);

        // 更新 session 中的账户信息
        CurrentUser.set(request, userId, account);
        return ResponseBean.success();
    }

    /**
     * 修改当前登录账号的密码设置
     */
    @PutMapping("/api/settings/account/password")
    @ResponseBody
    public ResponseBean updateCurrentAccountPassword(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String userId = CurrentUser.require(request);
        Account account = accountService.getByUserId(userId);
        if (account == null) {
            return ResponseBean.fail("账户不存在");
        }

        Integer passwordEnabled = body.get("passwordEnabled") != null
                ? Integer.parseInt(body.get("passwordEnabled").toString()) : null;
        String password = body.get("password") != null ? body.get("password").toString() : null;

        if (passwordEnabled != null && passwordEnabled == 1) {
            if (password == null || password.isBlank()) {
                return ResponseBean.fail("启用密码时必须设置密码");
            }
            account.setPasswordEnabled(1);
            account.setPassword(PasswordUtil.hash(password));
        } else {
            account.setPasswordEnabled(0);
            account.setPassword(null);
        }

        accountService.update(account);
        CurrentUser.set(request, userId, account);
        return ResponseBean.success();
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
