package com.agent.hopaw.avatar.task;

import com.agent.hopaw.avatar.entity.AvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.avatar.util.AvatarProactivePlanner;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.task.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AvatarTaskHandler implements TaskHandler {

    private static final Logger logger = LoggerFactory.getLogger(AvatarTaskHandler.class);
    public static final String TYPE = "avatar";

    private volatile boolean running = false;

    private final AvatarConfigMapper avatarConfigMapper;
    private final AvatarProactivePlanner proactivePlanner;

    public AvatarTaskHandler(AvatarConfigMapper avatarConfigMapper,
                             AvatarProactivePlanner proactivePlanner) {
        this.avatarConfigMapper = avatarConfigMapper;
        this.proactivePlanner = proactivePlanner;
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
            List<AvatarConfig> configs = loadAllConfigs();
            if (configs.isEmpty()) {
                logger.info("虚拟人定时任务跳过：未找到任何 AvatarConfig 记录 [{}]", task.getId());
                return;
            }

            int processed = 0;
            int skipped = 0;
            int noIncrement = 0;
            for (AvatarConfig config : configs) {
                String userId = config.getUserId();
                if (userId == null || userId.isBlank()) {
                    skipped++;
                    continue;
                }
                if (Boolean.TRUE.equals(config.getDisabled())) {
                    logger.info("虚拟人定时任务跳过：虚拟人已关闭 userId={}", userId);
                    skipped++;
                    continue;
                }
                try {
                    Long afterId = config.getLastProcessedChatId();
                    List<ChatHistory> processedRecords = proactivePlanner.analyzeAndDecide(userId, task, afterId);
                    if (processedRecords == null || processedRecords.isEmpty()) {
                        noIncrement++;
                        logger.info("虚拟人定时任务跳过：未查询到增量用户输入 userId={} afterId={}", userId, afterId);
                        continue;
                    }
                    Long maxId = processedRecords.stream()
                            .map(ChatHistory::getId)
                            .filter(id -> id != null)
                            .max(Comparator.naturalOrder())
                            .orElse(afterId);
                    if (maxId != null && (afterId == null || maxId > afterId)) {
                        try {
                            avatarConfigMapper.updateLastProcessedChatId(userId, maxId);
                        } catch (Exception persistError) {
                            logger.error("更新 lastProcessedChatId 失败 userId={} maxId={}", userId, maxId, persistError);
                        }
                    }
                    processed++;
                } catch (Exception e) {
                    logger.error("虚拟人定时任务处理失败 userId={} [{}]", userId, task.getId(), e);
                }
            }

            logger.info("虚拟人定时任务执行完成 [{}] 处理={} 跳过={} 无增量={}", task.getId(), processed, skipped, noIncrement);
        } catch (Exception e) {
            logger.error("虚拟人定时任务执行失败 [{}]", task.getId(), e);
        } finally {
            running = false;
            logger.debug("虚拟人定时任务执行完成 [{}]", task.getId());
        }
    }

    private List<AvatarConfig> loadAllConfigs() {
        try {
            return avatarConfigMapper.findAll();
        } catch (Exception e) {
            logger.error("扫描 AvatarConfig 失败", e);
            return List.of();
        }
    }
}
