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

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/avatar")
public class AvatarSettingsController {

    /** 兼容未登录兜底（应用拦截器会先拦截大多数调用） */
    private static final String FALLBACK_USER_ID = "user1";

    private final AvatarSettingsService avatarSettingsService;
    private final ScheduledTaskService scheduledTaskService;

    public AvatarSettingsController(AvatarSettingsService avatarSettingsService,
                                   ScheduledTaskService scheduledTaskService) {
        this.avatarSettingsService = avatarSettingsService;
        this.scheduledTaskService = scheduledTaskService;
    }

    @GetMapping("/settings")
    public ResponseBean getSettings(HttpServletRequest request,
                                    @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        return ResponseBean.success(avatarSettingsService.getSettings(resolveUserId(request, headerUserId)));
    }

    @PutMapping("/settings")
    public ResponseBean saveSettings(HttpServletRequest request,
                                     @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                     @RequestBody AvatarSettings settings) {
        avatarSettingsService.saveSettings(resolveUserId(request, headerUserId), settings);
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
    public ResponseBean listModelGroups(HttpServletRequest request,
                                        @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        Map<String, Object> data = new HashMap<>();
        List<AvatarModelGroup> groups = avatarSettingsService.listModelGroups();
        data.put("groups", groups);
        data.put("selected", avatarSettingsService.getSelectedModelGroup(resolveUserId(request, headerUserId)));
        return ResponseBean.success(data);
    }

    @GetMapping("/models/pool")
    public ResponseBean resolveModelPool(HttpServletRequest request,
                                         @RequestHeader(value = "X-User-Id", required = false) String headerUserId) {
        Map<String, Object> data = new HashMap<>();
        String userId = resolveUserId(request, headerUserId);
        data.put("pool", avatarSettingsService.resolveModelPool(userId));
        data.put("selected", avatarSettingsService.getSelectedModelGroup(userId));
        return ResponseBean.success(data);
    }

    /**
     * 优先从 header 取（兼容旧调用），其次从 session 取，最后回退到默认用户。
     */
    private static String resolveUserId(HttpServletRequest request, String headerUserId) {
        if (headerUserId != null && !headerUserId.isEmpty()) {
            return headerUserId;
        }
        if (request != null) {
            var session = request.getSession(false);
            if (session != null) {
                Object value = session.getAttribute("currentUserId");
                if (value != null && !value.toString().isEmpty()) {
                    return value.toString();
                }
            }
        }
        return FALLBACK_USER_ID;
    }
}
