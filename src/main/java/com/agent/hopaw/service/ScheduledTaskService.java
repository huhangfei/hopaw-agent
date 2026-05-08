package com.agent.hopaw.service;

import com.agent.hopaw.mapper.ScheduledTaskMapper;
import com.agent.hopaw.model.ScheduledTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ScheduledTaskService {

    private final ScheduledTaskMapper taskMapper;
    private final DynamicTaskService dynamicTaskService;

    public ScheduledTaskService(ScheduledTaskMapper taskMapper, DynamicTaskService dynamicTaskService) {
        this.taskMapper = taskMapper;
        this.dynamicTaskService = dynamicTaskService;
    }

    public List<ScheduledTask> findAll() {
        return taskMapper.findAll();
    }

    public ScheduledTask findById(Long id) {
        return taskMapper.findById(id);
    }

    public ScheduledTask findByTaskType(String taskType) {
        return taskMapper.findByTaskType(taskType);
    }

    public List<ScheduledTask> findByUserId(String userId) {
        return taskMapper.findByUserId(userId);
    }

    public List<ScheduledTask> findByAgentId(String agentId) {
        return taskMapper.findByAgentId(agentId);
    }

    public List<ScheduledTask> findByUserIdAndAgentId(String userId, String agentId) {
        return taskMapper.findByUserIdAndAgentId(userId, agentId);
    }

    public boolean isTaskRunning(Long id) {
        return dynamicTaskService.isRunning(id);
    }

    public boolean isTaskRunning(String type) {
        ScheduledTask task = taskMapper.findByTaskType(type);
        if(task==null){
            return false;
        }
        return dynamicTaskService.isRunning(task.getId());
    }

    @Transactional
    public void setEnabled(Long id, Integer enabled) {
        ScheduledTask task = taskMapper.findById(id);
        if (task == null) return;
        task.setEnabled(enabled);
        dynamicTaskService.cancelTask(task.getId());
        taskMapper.update(task);
        if (enabled == 1) {
            dynamicTaskService.scheduleTask(task);
        }
    }

    @Transactional
    public void insert(ScheduledTask task) {
        if (task.getEnabled() == null) {
            task.setEnabled(0);
        }
        taskMapper.insert(task);
        if (task.getEnabled() == 1) {
            dynamicTaskService.scheduleTask(task);
        }
    }

    @Transactional
    public void update(ScheduledTask task) {
        dynamicTaskService.cancelTask(task.getId());
        taskMapper.update(task);
        if (task.getEnabled() == 1) {
            dynamicTaskService.scheduleTask(task);
        }
    }

    @Transactional
    public void deleteById(Long id) {
        dynamicTaskService.cancelTask(id);
        taskMapper.deleteById(id);
    }
}
