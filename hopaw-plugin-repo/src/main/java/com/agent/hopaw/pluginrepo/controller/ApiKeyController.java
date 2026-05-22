package com.agent.hopaw.pluginrepo.controller;

import com.agent.hopaw.pluginrepo.entity.ApiKey;
import com.agent.hopaw.pluginrepo.entity.User;
import com.agent.hopaw.pluginrepo.service.ApiKeyService;
import com.agent.hopaw.pluginrepo.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/plugin-repo/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AuthService authService;

    public ApiKeyController(ApiKeyService apiKeyService, AuthService authService) {
        this.apiKeyService = apiKeyService;
        this.authService = authService;
    }

    @GetMapping("")
    public String keysPage(Model model) {
        User user = authService.getCurrentUser();
        if (user == null) {
            return "redirect:/login";
        }
        boolean isAdmin = "ADMIN".equals(user.getRole());
        List<ApiKey> keys;
        if (isAdmin) {
            keys = apiKeyService.findAll();
        } else {
            keys = apiKeyService.findByUserId(user.getId());
        }
        model.addAttribute("keys", keys);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("currentUser", user);
        return "apikey-manage";
    }

    @PostMapping("/api/keys")
    @ResponseBody
    public ResponseEntity<?> createKey(@RequestBody Map<String, String> body) {
        User user = authService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(msg("error", "未登录"));
        }
        String name = body.getOrDefault("name", "API Key");
        LocalDateTime expiresAt = null;
        String expiresAtStr = body.get("expiresAt");
        if (expiresAtStr != null && !expiresAtStr.isEmpty()) {
            expiresAt = LocalDateTime.parse(expiresAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        ApiKey key = apiKeyService.create(user.getId(), name, expiresAt);
        Map<String, Object> result = new HashMap<>();
        result.put("type", "success");
        result.put("message", "创建成功");
        result.put("keyValue", key.getKeyValue());
        result.put("id", key.getId());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/api/keys/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteKey(@PathVariable Long id) {
        User user = authService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(msg("error", "未登录"));
        }
        boolean isAdmin = "ADMIN".equals(user.getRole());
        List<ApiKey> userKeys = apiKeyService.findByUserId(user.getId());
        boolean ownKey = userKeys.stream().anyMatch(k -> k.getId().equals(id));

        if (!isAdmin && !ownKey) {
            return ResponseEntity.status(403).body(msg("error", "无权限删除"));
        }
        apiKeyService.delete(id);
        return ResponseEntity.ok(msg("success", "删除成功"));
    }

    @PutMapping("/api/keys/{id}/lock")
    @ResponseBody
    public ResponseEntity<?> toggleLock(@PathVariable Long id) {
        User user = authService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(msg("error", "未登录"));
        }
        if (!"ADMIN".equals(user.getRole())) {
            return ResponseEntity.status(403).body(msg("error", "仅管理员可操作"));
        }

        List<ApiKey> allKeys = apiKeyService.findAll();
        ApiKey target = allKeys.stream().filter(k -> k.getId().equals(id)).findFirst().orElse(null);
        if (target == null) {
            return ResponseEntity.badRequest().body(msg("error", "Key不存在"));
        }

        apiKeyService.setLocked(id, !target.isLocked());
        return ResponseEntity.ok(msg("success", target.isLocked() ? "已解锁" : "已锁定"));
    }

    @GetMapping("/api/keys")
    @ResponseBody
    public List<ApiKey> listKeys() {
        User user = authService.getCurrentUser();
        if (user == null) {
            return List.of();
        }
        if ("ADMIN".equals(user.getRole())) {
            return apiKeyService.findAll();
        }
        return apiKeyService.findByUserId(user.getId());
    }

    private Map<String, String> msg(String type, String message) {
        Map<String, String> m = new HashMap<>();
        m.put("type", type);
        m.put("message", message);
        return m;
    }
}