package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.util.CurrentUser;
import com.agent.hopaw.util.PasswordUtil;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 登录 / 切换用户相关接口。
 */
@Controller
public class LoginController {

    private final AccountService accountService;

    public LoginController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 登录页（选择用户）
     */
    @GetMapping("/login")
    public String loginPage(HttpServletRequest request, Model model,
                            @RequestParam(required = false) String redirect) {
        // 已登录直接跳到目标页或首页
        if (CurrentUser.isLogin(request)) {
            return "redirect:" + (redirect == null || redirect.isEmpty() ? "/" : redirect);
        }
        List<Account> accounts = accountService.listAccounts();
        model.addAttribute("accounts", accounts);
        model.addAttribute("redirect", redirect);
        return "login";
    }

    /**
     * 公开接口：登录页加载用户列表（仅返回启用账户）
     */
    @GetMapping("/api/auth/accounts")
    @ResponseBody
    public ResponseBean listLoginAccounts() {
        List<Account> accounts = accountService.listAccounts();
        return ResponseBean.success(accounts);
    }

    /**
     * 公开接口：检查指定账户是否需要密码登录
     */
    @GetMapping("/api/auth/check-password")
    @ResponseBody
    public ResponseBean checkPasswordRequired(@RequestParam String userId) {
        Account account = accountService.getByUserId(userId);
        if (account == null) {
            return ResponseBean.fail("账户不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("passwordRequired", account.getPasswordEnabled() != null && account.getPasswordEnabled() == 1);
        return ResponseBean.success(data);
    }

    /**
     * 公开接口：选择用户即登录
     */
    @PostMapping("/api/auth/login")
    @ResponseBody
    public ResponseBean login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String userId = body == null ? null : body.get("userId");
        String password = body == null ? null : body.get("password");
        if (userId == null || userId.isBlank()) {
            return ResponseBean.fail("用户编号不能为空");
        }
        Account account = accountService.getByUserId(userId);
        if (account == null) {
            return ResponseBean.fail("账户不存在");
        }
        if (account.getStatus() != null && account.getStatus() == 0) {
            return ResponseBean.fail("账户已被禁用");
        }
        // 密码校验
        if (account.getPasswordEnabled() != null && account.getPasswordEnabled() == 1) {
            if (password == null || password.isBlank()) {
                return ResponseBean.fail("password_required");
            }
            if (!PasswordUtil.verify(password, account.getPassword())) {
                return ResponseBean.fail("密码错误");
            }
        }
        CurrentUser.set(request, userId, account);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", account.getUserId());
        data.put("username", account.getUsername());
        data.put("nickname", account.getNickname());
        return ResponseBean.success(data);
    }

    /**
     * 注销
     */
    @PostMapping("/api/auth/logout")
    @ResponseBody
    public ResponseBean logout(HttpServletRequest request) {
        CurrentUser.clear(request);
        return ResponseBean.success();
    }

    /**
     * 切换用户：清除登录态后由前端跳转至 /login
     */
    @PostMapping("/api/auth/switch")
    @ResponseBody
    public ResponseBean switchUser(HttpServletRequest request) {
        CurrentUser.clear(request);
        return ResponseBean.success();
    }

    /**
     * 当前登录用户信息
     */
    @GetMapping("/api/auth/me")
    @ResponseBody
    public ResponseBean me(HttpServletRequest request) {
        String userId = CurrentUser.fromSession(request);
        if (userId == null) {
            return ResponseBean.fail("未登录");
        }
        Account account = accountService.getByUserId(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        if (account != null) {
            data.put("username", account.getUsername());
            data.put("nickname", account.getNickname());
        }
        return ResponseBean.success(data);
    }
}
