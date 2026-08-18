package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.service.IWorkflowTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流任务后台调度器：轮询待执行任务并拉起智能体
 */
@Component("workflowTaskScheduler")
public class WorkflowTaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskScheduler.class);
    private final IWorkflowTaskService taskService;

    public WorkflowTaskScheduler(IWorkflowTaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollPendingTasks() {
        List<WorkflowTask> tasks = taskService.findPendingExecution();
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (WorkflowTask task : tasks) {
            try {
                // 如果设置了开始时间且还未到，跳过
                if (task.getStartTime() != null && task.getStartTime().isAfter(LocalDateTime.now())) {
                    continue;
                }
                logger.info("拉起任务: id={}, title={}", task.getId(), task.getTitle());
                taskService.executeTask(task.getId());
            } catch (Exception e) {
                logger.error("拉起任务失败: id={}, error={}", task.getId(), e.getMessage(), e);
                taskService.updateTaskStatus(task.getId(), "failed", e.getMessage());
            }
        }
    }
}
