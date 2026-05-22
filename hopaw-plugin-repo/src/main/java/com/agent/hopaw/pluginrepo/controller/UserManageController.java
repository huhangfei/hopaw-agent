package com.agent.hopaw.pluginrepo.controller;

import com.agent.hopaw.pluginrepo.entity.User;
import com.agent.hopaw.pluginrepo.service.AuthService;
import com.agent.hopaw.pluginrepo.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/plugin-repo/admin")
public class UserManageController {

    private final UserService userService;
    private final AuthService authService;

    public UserManageController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String userPage(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("currentUser", authService.getCurrentUser());
        return "user-manage";
    }

    @PostMapping("/api/users")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String displayName = body.getOrDefault("displayName", "");
        String role = body.getOrDefault("role", "USER");

        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(msg("error", "用户名不能为空"));
        }
        if (password == null || password.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(msg("error", "密码不能为空"));
        }
        if (userService.findByUsername(username) != null) {
            return ResponseEntity.badRequest().body(msg("error", "用户名已存在"));
        }

        userService.create(username.trim(), password, displayName, role);
        return ResponseEntity.ok(msg("success", "创建成功"));
    }

    @PutMapping("/api/users/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(msg("error", "用户不存在"));
        }
        String displayName = body.getOrDefault("displayName", user.getDisplayName());
        String role = body.getOrDefault("role", user.getRole());
        String password = body.get("password");
        userService.update(id, displayName, role, password);
        return ResponseEntity.ok(msg("success", "更新成功"));
    }

    @DeleteMapping("/api/users/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(msg("error", "用户不存在"));
        }
        if ("admin".equals(user.getUsername())) {
            return ResponseEntity.badRequest().body(msg("error", "不能删除超级管理员"));
        }
        userService.delete(id);
        return ResponseEntity.ok(msg("success", "删除成功"));
    }

    @PutMapping("/api/users/{id}/lock")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> toggleLock(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return ResponseEntity.badRequest().body(msg("error", "用户不存在"));
        }
        if ("admin".equals(user.getUsername())) {
            return ResponseEntity.badRequest().body(msg("error", "不能锁定超级管理员"));
        }
        userService.setLocked(id, !user.isLocked());
        return ResponseEntity.ok(msg("success", user.isLocked() ? "已解锁" : "已锁定"));
    }

    @GetMapping("/api/users")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> listUsers() {
        return userService.findAll();
    }

    private Map<String, String> msg(String type, String message) {
        Map<String, String> m = new HashMap<>();
        m.put("type", type);
        m.put("message", message);
        return m;
    }
}