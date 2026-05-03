package com.agent.hopaw.controller;

import com.agent.hopaw.model.ResponseBean;
import com.agent.hopaw.model.ScheduledTask;
import com.agent.hopaw.service.ScheduledTaskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @GetMapping
    public ResponseBean getAll() {
        List<ScheduledTask> list = scheduledTaskService.findAll();
        return ResponseBean.success(list);
    }

    @GetMapping("/{id}")
    public ResponseBean getById(@PathVariable Long id) {
        ScheduledTask task = scheduledTaskService.findById(id);
        if (task == null) {
            return ResponseBean.fail("任务不存在");
        }
        return ResponseBean.success(task);
    }

    @PostMapping
    public ResponseBean create(@RequestBody ScheduledTask task) {
        if (task.getTaskName() == null || task.getTaskName().isBlank()) {
            return ResponseBean.fail("任务名称不能为空");
        }
        if (task.getCronExpression() == null || task.getCronExpression().isBlank()) {
            return ResponseBean.fail("Cron表达式不能为空");
        }
        scheduledTaskService.insert(task);
        return ResponseBean.success(task);
    }

    @PutMapping("/{id}")
    public ResponseBean update(@PathVariable Long id, @RequestBody ScheduledTask task) {
        ScheduledTask existing = scheduledTaskService.findById(id);
        if (existing == null) {
            return ResponseBean.fail("任务不存在");
        }
        task.setId(id);
        scheduledTaskService.update(task);
        return ResponseBean.success(task);
    }

    @DeleteMapping("/{id}")
    public ResponseBean delete(@PathVariable Long id) {
        ScheduledTask existing = scheduledTaskService.findById(id);
        if (existing == null) {
            return ResponseBean.fail("任务不存在");
        }
        scheduledTaskService.deleteById(id);
        return ResponseBean.success();
    }
}
