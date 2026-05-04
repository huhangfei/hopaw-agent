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

    @GetMapping("/type/{taskType}")
    public ResponseBean getByTaskType(@PathVariable String taskType) {
        ScheduledTask task = scheduledTaskService.findByTaskType(taskType);
        if (task == null) {
            return ResponseBean.fail("任务不存在");
        }
        var map = new java.util.HashMap<String, Object>();
        map.put("task", task);
        map.put("running", scheduledTaskService.isTaskRunning(task.getId()));
        return ResponseBean.success(map);
    }

    @PutMapping("/{id}/enabled")
    public ResponseBean setEnabled(@PathVariable Long id, @RequestBody java.util.Map<String, Integer> body) {
        ScheduledTask existing = scheduledTaskService.findById(id);
        if (existing == null) {
            return ResponseBean.fail("任务不存在");
        }
        Integer enabled = body.get("enabled");
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            return ResponseBean.fail("参数错误");
        }
        scheduledTaskService.setEnabled(id, enabled);
        return ResponseBean.success();
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
