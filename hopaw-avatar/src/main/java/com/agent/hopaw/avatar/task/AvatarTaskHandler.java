package com.agent.hopaw.avatar.task;

import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.task.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AvatarTaskHandler implements TaskHandler {

    private static final Logger logger = LoggerFactory.getLogger(AvatarTaskHandler.class);
    public static final String TYPE = "avatar";

    private volatile boolean running = false;

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
        } catch (Exception e) {
            logger.error("虚拟人定时任务执行失败 [{}]", task.getId(), e);
        } finally {
            running = false;
            logger.debug("虚拟人定时任务执行完成 [{}]", task.getId());
        }
    }
}
