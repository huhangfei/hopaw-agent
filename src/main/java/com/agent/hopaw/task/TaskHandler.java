package com.agent.hopaw.task;

import com.agent.hopaw.model.ScheduledTask;

public interface TaskHandler {
    String getType();
    void execute(ScheduledTask task);
}
