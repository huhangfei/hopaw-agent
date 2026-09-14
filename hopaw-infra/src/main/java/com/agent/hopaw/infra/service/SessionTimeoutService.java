package com.agent.hopaw.infra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 会话执行超时配置：聊天/项目/工作流任务三类会话的执行器看门狗超时时间（秒），
 * 从系统配置读取（设置页"会话超时"可调），每次执行时实时读取，保存后立即生效。
 */
@Service
public class SessionTimeoutService {
    private static final Logger logger = LoggerFactory.getLogger(SessionTimeoutService.class);

    /** 配置项：普通聊天会话执行超时（秒） */
    public static final String CONFIG_CHAT_TIMEOUT = "chat_execute_timeout_seconds";
    /** 配置项：项目会话执行超时（秒），项目自动迭代与项目会话唤起共用 */
    public static final String CONFIG_PROJECT_TIMEOUT = "project_execute_timeout_seconds";
    /** 配置项：工作流任务会话执行超时（秒） */
    public static final String CONFIG_WORKFLOW_TASK_TIMEOUT = "workflow_task_execute_timeout_seconds";

    /** 默认超时时间（秒） */
    public static final long DEFAULT_TIMEOUT_SECONDS = 360;
    /** 最小超时时间（秒） */
    public static final long MIN_TIMEOUT_SECONDS = 30;
    /** 最大超时时间（秒） */
    public static final long MAX_TIMEOUT_SECONDS = 86400;

    private final ISysConfigService sysConfigService;

    public SessionTimeoutService(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /** 普通聊天会话执行超时（秒） */
    public long getChatTimeoutSeconds() {
        return readTimeout(CONFIG_CHAT_TIMEOUT);
    }

    /** 项目会话执行超时（秒）：项目自动迭代与项目会话唤起共用 */
    public long getProjectTimeoutSeconds() {
        return readTimeout(CONFIG_PROJECT_TIMEOUT);
    }

    /** 工作流任务会话执行超时（秒） */
    public long getWorkflowTaskTimeoutSeconds() {
        return readTimeout(CONFIG_WORKFLOW_TASK_TIMEOUT);
    }

    /** 读取超时配置：非法值或读取失败回退默认值，并按上下限裁剪 */
    private long readTimeout(String key) {
        String raw;
        try {
            raw = sysConfigService.getValueByKey(key, String.valueOf(DEFAULT_TIMEOUT_SECONDS));
        } catch (Exception e) {
            logger.warn("读取会话超时配置失败，使用默认值: key={}, 原因: {}", key, e.getMessage());
            return DEFAULT_TIMEOUT_SECONDS;
        }
        try {
            long v = Long.parseLong(raw.trim());
            if (v < MIN_TIMEOUT_SECONDS) {
                return MIN_TIMEOUT_SECONDS;
            }
            return Math.min(v, MAX_TIMEOUT_SECONDS);
        } catch (NumberFormatException e) {
            logger.warn("会话超时配置非法，回退默认值: key={}, value={}", key, raw);
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }
}
