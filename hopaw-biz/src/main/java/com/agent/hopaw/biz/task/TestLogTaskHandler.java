package com.agent.hopaw.biz.task;

import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.task.TaskHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TestLogTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(TestLogTaskHandler.class);

    @Override
    public String getType() {
        return "testLog";
    }

    @Override
    public void execute(ScheduledTask task) {
        logger.info("定时任务执行 [{}] - {}: {}", task.getId(), task.getTaskName(), task.getDescription());
    }
}
