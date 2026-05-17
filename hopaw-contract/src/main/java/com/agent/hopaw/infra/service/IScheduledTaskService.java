package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.ScheduledTask;

import java.util.List;

public interface IScheduledTaskService {
    List<ScheduledTask> findAll();
    ScheduledTask findById(Long id);
    ScheduledTask findByTaskType(String taskType);
    List<ScheduledTask> findByUserId(String userId);
    List<ScheduledTask> findByAgentId(String agentId);
    List<ScheduledTask> findByUserIdAndAgentId(String userId, String agentId);
    boolean isTaskRunning(Long id);
    boolean isTaskRunning(String type);
    void setEnabled(Long id, Integer enabled);
    void insert(ScheduledTask task);
    void update(ScheduledTask task);
    void deleteById(Long id);
}
