package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.service.AccountService;
import com.agent.hopaw.infra.task.ProjectThreadPool;
import com.agent.hopaw.infra.task.WorkflowTaskThreadPool;
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
        put("scheduled-tasks", new String[] {"/js/page/scheduled-tasks.js", "/css/page/scheduled-tasks.css"});
        put("workflow-pool", new String[] {"/js/page/settings-workflow-pool.js", null});
        put("notify",       new String[] {"/js/page/settings-notify.js", "/css/page/settings-notify.css"});
    }};

    private final MailUtil mailUtil;
    private final AccountService accountService;
    private final BackupService backupService;
    private final WorkflowTaskThreadPool workflowTaskThreadPool;
    private final ProjectThreadPool projectThreadPool;

    public SettingsController(MailUtil mailUtil, AccountService accountService, BackupService backupService,
                              WorkflowTaskThreadPool workflowTaskThreadPool,
                              ProjectThreadPool projectThreadPool) {
        this.mailUtil = mailUtil;
        this.accountService = accountService;
        this.backupService = backupService;
        this.workflowTaskThreadPool = workflowTaskThreadPool;
        this.projectThreadPool = projectThreadPool;
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

    /**
     * 按最新配置重建工作流任务线程池（设置页保存后调用）
     */
    @PostMapping("/api/settings/workflow-pool/reload")
    @ResponseBody
    public ResponseBean reloadWorkflowPool() {
        try {
            workflowTaskThreadPool.reload();
            return ResponseBean.success(workflowTaskThreadPool.getStats());
        } catch (Exception e) {
            log.error("重建工作流线程池失败", e);
            return ResponseBean.fail("重建线程池失败: " + e.getMessage());
        }
    }

    /**
     * 查询工作流任务线程池运行状态
     */
    @GetMapping("/api/settings/workflow-pool/status")
    @ResponseBody
    public ResponseBean workflowPoolStatus() {
        return ResponseBean.success(workflowTaskThreadPool.getStats());
    }

    /**
     * 按最新配置重建项目线程池（设置页保存后调用）
     */
    @PostMapping("/api/settings/project-pool/reload")
    @ResponseBody
    public ResponseBean reloadProjectPool() {
        try {
            projectThreadPool.reload();
            return ResponseBean.success(projectThreadPool.getStats());
        } catch (Exception e) {
            log.error("重建项目线程池失败", e);
            return ResponseBean.fail("重建线程池失败: " + e.getMessage());
        }
    }

    /**
     * 查询项目线程池运行状态
     */
    @GetMapping("/api/settings/project-pool/status")
    @ResponseBody
    public ResponseBean projectPoolStatus() {
        return ResponseBean.success(projectThreadPool.getStats());
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
     * 返回 JSON：data.fileName + data.zipBase64 + data.password，
     * 前端用 JS 触发下载并弹窗展示后端生成的密码。
     */
    @PostMapping("/api/backup")
    @ResponseBody
    public ResponseBean backup(@RequestBody Map<String, Object> body) {
        try {
            boolean exportSysConfig = Boolean.TRUE.equals(body.get("sysConfig"));
            boolean exportModelConfig = Boolean.TRUE.equals(body.get("modelConfig"));
            boolean exportAgentConfig = Boolean.TRUE.equals(body.get("agentConfig"));
            boolean exportTtsConfig = Boolean.TRUE.equals(body.get("ttsConfig"));
            boolean exportMemory = Boolean.TRUE.equals(body.get("memory"));

            if (!exportSysConfig && !exportModelConfig && !exportAgentConfig && !exportTtsConfig && !exportMemory) {
                return ResponseBean.fail("请至少选择一项备份内容");
            }

            BackupService.BackupResult result = backupService.backup(
                    exportSysConfig, exportModelConfig, exportAgentConfig, exportTtsConfig, exportMemory);
            File zipFile = result.zipPath().toFile();
            byte[] zipBytes = Files.readAllBytes(result.zipPath());
            String zipBase64 = java.util.Base64.getEncoder().encodeToString(zipBytes);

            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("fileName", zipFile.getName());
            data.put("zipBase64", zipBase64);
            data.put("password", result.password());
            return ResponseBean.success(data);
        } catch (Exception e) {
            log.error("备份失败", e);
            return ResponseBean.fail("备份失败: " + e.getMessage());
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
