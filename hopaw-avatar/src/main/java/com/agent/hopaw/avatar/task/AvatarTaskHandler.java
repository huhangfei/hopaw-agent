package com.agent.hopaw.avatar.task;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.avatar.model.AvatarAction;
import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.avatar.util.AvatarProactivePlanner;
import com.agent.hopaw.avatar.websocket.AvatarWebSocketHandler;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.task.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class AvatarTaskHandler implements TaskHandler {

    private static final Logger logger = LoggerFactory.getLogger(AvatarTaskHandler.class);
    public static final String TYPE = "avatar";

    /** 若距上次主动问候已超过该阈值且本轮无新数据，则补发一个 wave 动作以维持存在感 */
    private static final long WAVE_INTERVAL_MINUTES = 3L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private volatile boolean running = false;

    private final AvatarConfigMapper avatarConfigMapper;
    private final AvatarProactivePlanner proactivePlanner;
    private final AvatarWebSocketHandler avatarWebSocketHandler;
    private final ApplicationEventPublisher eventPublisher;

    public AvatarTaskHandler(AvatarConfigMapper avatarConfigMapper,
                             AvatarProactivePlanner proactivePlanner,
                             AvatarWebSocketHandler avatarWebSocketHandler,
                             ApplicationEventPublisher eventPublisher) {
        this.avatarConfigMapper = avatarConfigMapper;
        this.proactivePlanner = proactivePlanner;
        this.avatarWebSocketHandler = avatarWebSocketHandler;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void execute(ScheduledTask task) {
        if (task == null) {
            return;
        }
        if (running) {
            logger.warn("虚拟人定时任务正在运行中，跳过本次执行 [{}]", task.getId());
            return;
        }
        running = true;
        try {
            logger.info("虚拟人定时任务执行 [{}] cron={}", task.getId(), task.getCronExpression());
            List<AgentAvatarConfig> configs = loadAllConfigs();
            if (configs.isEmpty()) {
                logger.info("虚拟人定时任务跳过：未找到任何 AgentAvatarConfig 记录 [{}]", task.getId());
                return;
            }

            int processed = 0;
            int skipped = 0;
            int noIncrement = 0;
            int waved = 0;
            for (AgentAvatarConfig config : configs) {
                String userId = config.getUserId();
                Long agentId = config.getAgentId();
                if (userId == null || userId.isBlank()) {
                    skipped++;
                    continue;
                }
                if (agentId == null) {
                    skipped++;
                    continue;
                }
                if (Boolean.TRUE.equals(config.getDisabled())) {
                    logger.info("虚拟人定时任务跳过：虚拟人已关闭 userId={} agentId={}", userId, agentId);
                    skipped++;
                    continue;
                }
                if (!avatarWebSocketHandler.hasActiveSession(userId, agentId)) {
                    logger.info("虚拟人定时任务跳过：无活跃 WS 会话 userId={} agentId={}", userId, agentId);
                    skipped++;
                    continue;
                }
                try {
                    Long afterId = config.getLastProcessedChatId();
                    Long maxId = proactivePlanner.analyzeAndDecide(userId, agentId, task, afterId);
                    if (maxId != null && (afterId == null || maxId > afterId)) {
                        try {
                            avatarConfigMapper.updateLastProcessedChatId(userId, agentId, maxId);
                        } catch (Exception persistError) {
                            logger.error("更新 lastProcessedChatId 失败 userId={} agentId={} maxId={}", userId, agentId, maxId, persistError);
                        }
                    } else {
                        // 本轮未拉到新数据
                        noIncrement++;
                        // 距离上次主动问候超过阈值，则补发一个 wave 动作
                        if (shouldSendWave(config.getLastProactiveGreetingTime())) {
                            try {
                                eventPublisher.publishEvent(AvatarEvent.action(userId, agentId, AvatarAction.WAVE));
                                waved++;
                                logger.info("虚拟人定时任务补发 wave：距上次问候已超过 {} 分钟 userId={} agentId={} lastGreetingTime={}",
                                        WAVE_INTERVAL_MINUTES, userId, agentId, config.getLastProactiveGreetingTime());
                            } catch (Exception publishError) {
                                logger.error("发布虚拟人 wave 事件失败 userId={} agentId={}", userId, agentId, publishError);
                            }
                        }
                    }
                    processed++;
                } catch (Exception e) {
                    logger.error("虚拟人定时任务处理失败 userId={} agentId={} [{}]", userId, agentId, task.getId(), e);
                }
            }

            logger.info("虚拟人定时任务执行完成 [{}] 处理={} 跳过={} 无增量={} 补发wave={}", task.getId(), processed, skipped, noIncrement, waved);
        } catch (Exception e) {
            logger.error("虚拟人定时任务执行失败 [{}]", task.getId(), e);
        } finally {
            running = false;
            logger.debug("虚拟人定时任务执行完成 [{}]", task.getId());
        }
    }

    /**
     * 判断当前是否需要补发 wave 动作。
     * 规则：从未主动问候过，或距上次主动问候已超过 WAVE_INTERVAL_MINUTES 分钟。
     */
    private boolean shouldSendWave(String lastProactiveGreetingTime) {
        if (lastProactiveGreetingTime == null || lastProactiveGreetingTime.isBlank()) {
            return true;
        }
        try {
            LocalDateTime last = LocalDateTime.parse(lastProactiveGreetingTime, TIME_FORMAT);
            return last.plusMinutes(WAVE_INTERVAL_MINUTES).isBefore(LocalDateTime.now());
        } catch (Exception e) {
            logger.warn("解析 lastProactiveGreetingTime 失败，按需补发处理 time={} err={}", lastProactiveGreetingTime, e.getMessage());
            return true;
        }
    }

    private List<AgentAvatarConfig> loadAllConfigs() {
        try {
            return avatarConfigMapper.findAll();
        } catch (Exception e) {
            logger.error("扫描 AgentAvatarConfig 失败", e);
            return List.of();
        }
    }
}
