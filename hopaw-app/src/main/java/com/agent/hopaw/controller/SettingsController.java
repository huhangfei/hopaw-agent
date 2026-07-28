package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.service.BackupService;
import com.agent.hopaw.biz.util.MailUtil;
import com.agent.hopaw.util.CurrentUser;
import com.agent.hopaw.util.PasswordUtil;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Controller
public class SettingsController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SettingsController.class);

    private static final Map<String, String[]> TAB_RESOURCES = new HashMap<>() {{
        put("memory",       new String[] {"/js/page/settings-memory.js", null});
        put("mail",         new String[] {"/js/page/settings-mail.js", null});
        put("tts",          new String[] {"/js/page/settings-tts.js", "/css/page/settings-tts.css"});
        put("plugin-store", new String[] {"/js/page/settings-plugin-store.js", null});
        put("account",      new String[] {"/js/page/settings-account.js", null});
        put("backup",       new String[] {"/js/page/settings-backup.js", null});
    }};

    private final MailUtil mailUtil;
    private final AccountService accountService;
    private final BackupService backupService;

    public SettingsController(MailUtil mailUtil, AccountService accountService, BackupService backupService) {
        this.mailUtil = mailUtil;
        this.accountService = accountService;
        this.backupService = backupService;
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

    /**
     * 备份数据
     */
    @PostMapping("/api/backup")
    @ResponseBody
    public ResponseEntity<Resource> backup(@RequestBody Map<String, Object> body) {
        try {
            boolean exportSysConfig = Boolean.TRUE.equals(body.get("sysConfig"));
            boolean exportModelConfig = Boolean.TRUE.equals(body.get("modelConfig"));
            boolean exportAgentConfig = Boolean.TRUE.equals(body.get("agentConfig"));
            String password = (String) body.get("password");

            if (!exportSysConfig && !exportModelConfig && !exportAgentConfig) {
                return ResponseEntity.badRequest().build();
            }

            Path zipPath = backupService.backup(exportSysConfig, exportModelConfig, exportAgentConfig, password);
            File zipFile = zipPath.toFile();

            FileSystemResource resource = new FileSystemResource(zipFile);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + zipFile.getName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(zipFile.length())
                    .body(resource);
        } catch (Exception e) {
            log.error("备份失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 导入备份
     */
    @PostMapping("/api/backup/restore")
    @ResponseBody
    public ResponseBean restore(@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                @RequestParam(value = "password", required = false) String password) {
        if (file == null || file.isEmpty()) {
            return ResponseBean.fail("请选择备份文件");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".zip")) {
            return ResponseBean.fail("仅支持 .zip 备份文件");
        }
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("hopaw-restore-upload-", ".zip");
            file.transferTo(tempFile.toFile());
            String summary = backupService.restore(tempFile.toFile(), password);
            return ResponseBean.success(summary);
        } catch (IllegalArgumentException e) {
            return ResponseBean.fail(e.getMessage());
        } catch (Exception e) {
            log.error("导入备份失败", e);
            return ResponseBean.fail("导入失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            }
        }
    }
}
