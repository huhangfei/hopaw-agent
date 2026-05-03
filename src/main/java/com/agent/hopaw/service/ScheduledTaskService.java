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
