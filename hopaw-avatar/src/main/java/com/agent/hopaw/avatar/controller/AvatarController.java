package com.agent.hopaw.avatar.controller;

import com.agent.hopaw.avatar.model.UserLevelInfo;
import com.agent.hopaw.avatar.service.AvatarService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/avatar")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping("/level")
    public UserLevelInfo getUserLevel(@RequestParam String userId) {
        return avatarService.getUserLevelInfo(userId);
    }

    @GetMapping("/levels")
    public Map<String, UserLevelInfo> getAllUserLevels() {
        return avatarService.getAllUserLevels();
    }
}