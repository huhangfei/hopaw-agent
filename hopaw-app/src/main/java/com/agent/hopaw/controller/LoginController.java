package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.util.CurrentUser;
import com.agent.hopaw.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录 / 切换用户相关接口。
 */
@Controller
public class LoginController {

    private final AccountService accountService;

    @Value("${hopaw.captcha.enabled:false}")
    private boolean captchaEnabled;

    /** 登录失败次数缓存（防暴力破解）：key=IP，value=次数 */
    private final ConcurrentHashMap<String, int[]> loginFailCache = new ConcurrentHashMap<>();
    /** 超过阈值后锁定时间（毫秒） */
    private static final long LOCKOUT_MS = 15 * 60 * 1000L;
    /** 触发锁定的失败次数 */
    private static final int FAIL_THRESHOLD = 5;

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
        String captchaCode = body == null ? null : body.get("captcha");
        if (userId == null || userId.isBlank()) {
            return ResponseBean.fail("用户编号不能为空");
        }

        // 频率限制：同一 IP 短时间内失败过多则拒绝
        String clientIp = getClientIp(request);
        int[] failInfo = loginFailCache.get(clientIp);
        if (failInfo != null && failInfo[0] >= FAIL_THRESHOLD) {
            long elapsed = System.currentTimeMillis() - failInfo[1];
            if (elapsed < LOCKOUT_MS) {
                long remainSec = (LOCKOUT_MS - elapsed) / 1000;
                return ResponseBean.fail("登录尝试过于频繁，请" + remainSec + "秒后重试");
            }
            loginFailCache.remove(clientIp);
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
            // 验证码校验（开启时）
            if (captchaEnabled) {
                if (captchaCode == null || captchaCode.isBlank()) {
                    return ResponseBean.fail("请输入验证码");
                }
                HttpSession session = request.getSession(false);
                if (session == null) {
                    return ResponseBean.fail("验证码已过期，请刷新");
                }
                String answer = (String) session.getAttribute(CaptchaController.SESSION_CAPTCHA_KEY);
                Long expireTime = (Long) session.getAttribute(CaptchaController.SESSION_CAPTCHA_KEY + "_expire");
                if (answer == null || expireTime == null || System.currentTimeMillis() > expireTime) {
                    return ResponseBean.fail("验证码已过期，请刷新");
                }
                session.removeAttribute(CaptchaController.SESSION_CAPTCHA_KEY);
                session.removeAttribute(CaptchaController.SESSION_CAPTCHA_KEY + "_expire");
                if (!answer.equals(captchaCode.trim().toLowerCase())) {
                    recordLoginFail(clientIp);
                    return ResponseBean.fail("验证码错误");
                }
            }
            if (!PasswordUtil.verify(password, account.getPassword())) {
                recordLoginFail(clientIp);
                return ResponseBean.fail("密码错误");
            }
        }
        // 登录成功：清除失败记录
        loginFailCache.remove(clientIp);
        CurrentUser.set(request, userId, account);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", account.getUserId());
        data.put("username", account.getUsername());
        data.put("nickname", account.getNickname());
        return ResponseBean.success(data);
    }

    private void recordLoginFail(String clientIp) {
        int[] info = loginFailCache.computeIfAbsent(clientIp, k -> new int[]{0, 0});
        info[0]++;
        info[1] = (int) System.currentTimeMillis();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return request.getRemoteAddr();
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
