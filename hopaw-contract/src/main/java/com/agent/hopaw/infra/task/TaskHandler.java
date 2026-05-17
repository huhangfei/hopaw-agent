package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.model.entity.ScheduledTask;

public interface TaskHandler {
    String getType();
    void execute(ScheduledTask task);
}
