package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.model.entity.LoginLog;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.infra.service.IIpBlacklistService;
import com.agent.hopaw.infra.service.ILoginLogService;
import com.agent.hopaw.infra.service.INotificationService;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.util.CurrentUser;
import com.agent.hopaw.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final AccountService accountService;
    private final ILoginLogService loginLogService;
    private final IIpBlacklistService ipBlacklistService;
    private final ISysConfigService sysConfigService;
    private final INotificationService notificationService;

    @Value("${hopaw.captcha.enabled:false}")
    private boolean captchaEnabled;

    /** 登录失败次数缓存（防暴力破解）：key=IP，value=次数 */
    private final ConcurrentHashMap<String, int[]> loginFailCache = new ConcurrentHashMap<>();
    /** 超过阈值后锁定时间（毫秒） */
    private static final long LOCKOUT_MS = 15 * 60 * 1000L;
    /** 触发锁定的失败次数 */
    private static final int FAIL_THRESHOLD = 5;

    public LoginController(AccountService accountService,
                           ILoginLogService loginLogService,
                           IIpBlacklistService ipBlacklistService,
                           ISysConfigService sysConfigService,
                           INotificationService notificationService) {
        this.accountService = accountService;
        this.loginLogService = loginLogService;
        this.ipBlacklistService = ipBlacklistService;
        this.sysConfigService = sysConfigService;
        this.notificationService = notificationService;
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

        String clientIp = getClientIp(request);

        // 频率限制：同一 IP 短时间内失败过多则拒绝
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
            recordLog(null, userId, clientIp, "failed", "账户不存在");
            return ResponseBean.fail("账户不存在");
        }
        if (account.getStatus() != null && account.getStatus() == 0) {
            recordLog(userId, account.getUsername(), clientIp, "failed", "账户已被禁用");
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
                    recordLog(userId, account.getUsername(), clientIp, "failed", "验证码错误");
                    checkAndHandleException(userId, account.getUsername(), clientIp);
                    return ResponseBean.fail("验证码错误");
                }
            }
            if (!PasswordUtil.verify(password, account.getPassword())) {
                recordLoginFail(clientIp);
                recordLog(userId, account.getUsername(), clientIp, "failed", "密码错误");
                checkAndHandleException(userId, account.getUsername(), clientIp);
                return ResponseBean.fail("密码错误");
            }
        }
        // 登录成功：清除失败记录
        loginFailCache.remove(clientIp);
        CurrentUser.set(request, userId, account);
        recordLog(userId, account.getUsername(), clientIp, "success", null);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", account.getUserId());
        data.put("username", account.getUsername());
        data.put("nickname", account.getNickname());
        return ResponseBean.success(data);
    }

    /**
     * 记录登录日志
     */
    private void recordLog(String userId, String username, String ip, String result, String failReason) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUsername(username);
            loginLog.setIp(ip);
            loginLog.setResult(result);
            loginLog.setFailReason(failReason);
            loginLogService.record(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败", e);
        }
    }

    /**
     * 检查并处理登录异常：用户最近N条全部失败 → 禁用用户；IP最近N条全部失败 → 加入黑名单
     */
    private void checkAndHandleException(String userId, String username, String clientIp) {
        try {
            String maxUserStr = sysConfigService.getValueByKey("login_max_user_failures", "10");
            String maxIpStr = sysConfigService.getValueByKey("login_max_ip_failures", "20");
            String channelIdsStr = sysConfigService.getValueByKey("login_exception_notify_channels", "");

            int maxUserFailures = Integer.parseInt(maxUserStr);
            int maxIpFailures = Integer.parseInt(maxIpStr);

            boolean userDisabled = false;
            boolean ipBlocked = false;

            // 检查用户：最近N条是否全部失败
            if (loginLogService.isUserAllFailures(userId, maxUserFailures)) {
                Account account = accountService.getByUserId(userId);
                if (account != null && account.getStatus() != null && account.getStatus() == 1) {
                    account.setStatus(0);
                    accountService.update(account);
                    userDisabled = true;
                    log.warn("用户最近{}条登录全部失败，已禁用: userId={}", maxUserFailures, userId);
                }
            }

            // 检查IP：最近N条是否全部失败
            if (loginLogService.isIpAllFailures(clientIp, maxIpFailures)) {
                if (!ipBlacklistService.isBlocked(clientIp)) {
                    ipBlacklistService.add(clientIp, "登录异常自动加入（最近" + maxIpFailures + "条全部失败）");
                    ipBlocked = true;
                    log.warn("IP最近{}条登录全部失败，已加入黑名单: ip={}", maxIpFailures, clientIp);
                }
            }

            // 发送通知
            if ((userDisabled || ipBlocked) && channelIdsStr != null && !channelIdsStr.isBlank()) {
                StringBuilder title = new StringBuilder("⚠ 登录异常告警");
                StringBuilder content = new StringBuilder();
                if (userDisabled) {
                    content.append("用户 ").append(username).append("（").append(userId).append("）最近")
                            .append(maxUserFailures).append("条登录全部失败，已自动禁用。\n");
                }
                if (ipBlocked) {
                    content.append("IP ").append(clientIp).append(" 最近")
                            .append(maxIpFailures).append("条登录全部失败，已自动加入黑名单。\n");
                }
                content.append("时间: ").append(java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                String[] ids = channelIdsStr.split(",");
                for (String idStr : ids) {
                    try {
                        Long channelId = Long.parseLong(idStr.trim());
                        notificationService.sendByChannelId(channelId, title.toString(), content.toString());
                    } catch (Exception e) {
                        log.error("发送登录异常通知失败: channelId={}", idStr, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("处理登录异常检测失败", e);
        }
    }

    private void recordLoginFail(String clientIp) {
        int[] info = loginFailCache.computeIfAbsent(clientIp, k -> new int[]{0, 0});
        info[0]++;
        info[1] = (int) System.currentTimeMillis();
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return normalizeIp(xff.split(",")[0].trim());
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return normalizeIp(realIp.trim());
        }
        return normalizeIp(request.getRemoteAddr());
    }

    private String normalizeIp(String ip) {
        if (ip == null) return ip;
        // IPv6 回环地址转 IPv4
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
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
