package com.agent.hopaw.avatar.controller;

import com.agent.hopaw.avatar.model.AvatarModelGroup;
import com.agent.hopaw.infra.model.dto.AvatarSettings;
import com.agent.hopaw.avatar.service.AvatarSettingsService;
import com.agent.hopaw.avatar.task.AvatarTaskHandler;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.service.ScheduledTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/avatar")
public class AvatarSettingsController {

    private static final Logger logger = LoggerFactory.getLogger(AvatarSettingsController.class);

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
                                    @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                    @RequestParam(value = "agentId", required = false) Long agentId) {
        String userId = resolveUserId(request, headerUserId);
        AvatarSettings settings = avatarSettingsService.getSettings(userId, agentId);
        logger.info("[avatar-settings] GET /settings userId={} agentId={} modelGroup={}", userId, agentId, settings.getModelGroup());
        return ResponseBean.success(settings);
    }

    @PutMapping("/settings")
    public ResponseBean saveSettings(HttpServletRequest request,
                                     @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                     @RequestParam(value = "agentId", required = false) Long agentId,
                                     @RequestBody AvatarSettings settings) {
        String userId = resolveUserId(request, headerUserId);
        logger.info("[avatar-settings] PUT /settings userId={} agentId={} payload={}", userId, agentId, settings);
        avatarSettingsService.saveSettings(userId, agentId, settings);
        logger.info("[avatar-settings] PUT /settings 保存完成 userId={} agentId={}", userId, agentId);
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
                                        @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                        @RequestParam(value = "agentId", required = false) Long agentId) {
        Map<String, Object> data = new HashMap<>();
        String userId = resolveUserId(request, headerUserId);
        List<AvatarModelGroup> groups = avatarSettingsService.listModelGroups();
        data.put("groups", groups);
        data.put("selected", avatarSettingsService.getSelectedModelGroup(userId, agentId));
        return ResponseBean.success(data);
    }

    @GetMapping("/models/pool")
    public ResponseBean resolveModelPool(HttpServletRequest request,
                                         @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
                                         @RequestParam(value = "agentId", required = false) Long agentId) {
        Map<String, Object> data = new HashMap<>();
        String userId = resolveUserId(request, headerUserId);
        data.put("pool", avatarSettingsService.resolveModelPool(userId, agentId));
        data.put("selected", avatarSettingsService.getSelectedModelGroup(userId, agentId));
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
