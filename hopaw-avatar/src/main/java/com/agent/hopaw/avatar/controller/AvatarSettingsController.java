package com.agent.hopaw.avatar.controller;

import com.agent.hopaw.avatar.model.AvatarModelGroup;
import com.agent.hopaw.avatar.model.AvatarSettings;
import com.agent.hopaw.avatar.service.AvatarSettingsService;
import com.agent.hopaw.avatar.task.AvatarTaskHandler;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.service.ScheduledTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/avatar")
public class AvatarSettingsController {

    private final AvatarSettingsService avatarSettingsService;
    private final ScheduledTaskService scheduledTaskService;

    public AvatarSettingsController(AvatarSettingsService avatarSettingsService,
                                   ScheduledTaskService scheduledTaskService) {
        this.avatarSettingsService = avatarSettingsService;
        this.scheduledTaskService = scheduledTaskService;
    }

    @GetMapping("/settings")
    public ResponseBean getSettings(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ResponseBean.success(avatarSettingsService.getSettings(resolveUserId(userId)));
    }

    @PutMapping("/settings")
    public ResponseBean saveSettings(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                     @RequestBody AvatarSettings settings) {
        avatarSettingsService.saveSettings(resolveUserId(userId), settings);
        return ResponseBean.success();
    }

    @GetMapping("/task/status")
    public ResponseBean getTaskStatus() {
        ScheduledTask task = scheduledTaskService.findByTaskType(AvatarTaskHandler.TYPE);
        if (task == null) {
            return ResponseBean.fail("虚拟人定时任务未注册");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("task", task);
        data.put("running", scheduledTaskService.isTaskRunning(task.getId()));
        return ResponseBean.success(data);
    }

    @GetMapping("/models")
    public ResponseBean listModelGroups(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> data = new HashMap<>();
        List<AvatarModelGroup> groups = avatarSettingsService.listModelGroups();
        data.put("groups", groups);
        data.put("selected", avatarSettingsService.getSelectedModelGroup(resolveUserId(userId)));
        return ResponseBean.success(data);
    }

    @GetMapping("/models/pool")
    public ResponseBean resolveModelPool(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("pool", avatarSettingsService.resolveModelPool(resolveUserId(userId)));
        data.put("selected", avatarSettingsService.getSelectedModelGroup(resolveUserId(userId)));
        return ResponseBean.success(data);
    }

    private static String resolveUserId(String userId) {
        return (userId == null || userId.isEmpty()) ? DEFAULT_USER_ID : userId;
    }

    private static final String DEFAULT_USER_ID = "user1";
}
