package com.agent.hopaw.pluginrepo.controller;

import com.agent.hopaw.pluginrepo.entity.User;
import com.agent.hopaw.pluginrepo.service.AuthService;
import com.agent.hopaw.pluginrepo.service.MailService;
import com.agent.hopaw.pluginrepo.service.UserService;
import com.agent.hopaw.pluginrepo.service.VerificationCodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final VerificationCodeService verificationCodeService;
    private final MailService mailService;
    private final AuthService authService;

    public AuthController(UserService userService,
                        VerificationCodeService verificationCodeService,
                        MailService mailService,
                        AuthService authService) {
        this.userService = userService;
        this.verificationCodeService = verificationCodeService;
        this.mailService = mailService;
        this.authService = authService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/plugin-repo/auth/send-code")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendCode(@RequestParam String email, @RequestParam String type) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 检查邮箱是否已注册（仅在注册时检查）
            if ("register".equals(type) && userService.findByEmail(email) != null) {
                result.put("success", false);
                result.put("message", "该邮箱已被注册");
                return ResponseEntity.badRequest().body(result);
            }

            if (!mailService.isConfigured()) {
                result.put("success", false);
                result.put("message", "邮件服务未配置，无法发送验证码");
                return ResponseEntity.badRequest().body(result);
            }

            String code = verificationCodeService.generateCode(email, type, 10);
            
            // 发送邮件
            String subject = "【插件仓库】验证码";
            String text = String.format("您的验证码是：%s，10分钟内有效。", code);
            mailService.sendSimpleMail(email, subject, text);
            
            result.put("success", true);
            result.put("message", "验证码已发送");
            log.info("验证码发送成功: email={}, type={}", email, type);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("验证码发送失败: email={}, type={}, error={}", email, type, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "发送失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        }
    }

    @PostMapping("/plugin-repo/auth/register")
    @ResponseBody
    public Map<String, Object> register(@RequestParam String username,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String code,
                           @RequestParam String displayName) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 验证验证码
            if (!verificationCodeService.verifyCode(email, code, "register")) {
                result.put("success", false);
                result.put("message", "验证码错误或已过期");
                return result;
            }

            // 检查用户名是否已存在
            if (userService.findByUsername(username) != null) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                return result;
            }

            // 检查邮箱是否已存在
            if (userService.findByEmail(email) != null) {
                result.put("success", false);
                result.put("message", "邮箱已被注册");
                return result;
            }

            // 创建用户，默认角色是 USER
            userService.create(username, email, password, displayName != null ? displayName : username, "USER");
            
            result.put("success", true);
            result.put("message", "注册成功");
            log.info("用户注册成功: username={}, email={}", username, email);
            return result;
        } catch (Exception e) {
            log.error("用户注册失败: username={}, email={}, error={}", username, email, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "注册失败: " + e.getMessage());
            return result;
        }
    }

    @GetMapping("/plugin-repo/user/change-password")
    public String changePasswordPage() {
        return "change-password";
    }

    @PostMapping("/plugin-repo/user/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                @RequestParam String newPassword,
                                RedirectAttributes redirectAttributes) {
        User currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            return "redirect:/login";
        }

        if (!userService.verifyPassword(oldPassword, currentUser.getPassword())) {
            redirectAttributes.addFlashAttribute("error", "原密码错误");
            return "redirect:/plugin-repo/user/change-password";
        }

        userService.updatePassword(currentUser.getId(), newPassword);
        redirectAttributes.addFlashAttribute("success", "密码修改成功，请重新登录");
        return "redirect:/login";
    }
}