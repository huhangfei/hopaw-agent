package com.agent.hopaw.avatar.controller;

import com.agent.hopaw.avatar.model.UserIntimacyInfo;
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

    @GetMapping("/intimacy")
    public UserIntimacyInfo getUserIntimacy(@RequestParam String userId,
                                            @RequestParam(value = "agentId", required = false) Long agentId) {
        if (agentId != null) {
            return avatarService.getUserAgentIntimacyInfo(userId, agentId);
        }
        return avatarService.getUserIntimacyInfo(userId);
    }

    @GetMapping("/intimacies")
    public Map<String, UserIntimacyInfo> getAllUserIntimacies() {
        return avatarService.getAllUserIntimacies();
    }
}
