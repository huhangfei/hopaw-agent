package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.constant.TaskStatusEnum;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.service.IWorkflowTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作流任务轮询 Handler：复用 DynamicTaskService 动态定时任务机制。
 * 每次触发时轮询待执行任务并提交到工作流线程池并发执行；
 * 调度仅做筛选与提交（毫秒级返回），实际执行在 WorkflowTaskThreadPool 的工作线程中。
 * 轮询间隔/启停可在设置页「定时任务」中调整（内置任务，类型 workflowTaskPoll）。
 */
@Component("workflowPollTaskHandler")
public class WorkflowPollTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowPollTaskHandler.class);

    /** 任务类型标识，对应 scheduled_tasks.task_type */
    public static final String TYPE = "workflowTaskPoll";

    private final IWorkflowTaskService taskService;
    private final WorkflowTaskThreadPool threadPool;
    /** 防重入标志：上一次轮询未结束时跳过本次触发 */
    private final AtomicBoolean polling = new AtomicBoolean(false);

    public WorkflowPollTaskHandler(IWorkflowTaskService taskService, WorkflowTaskThreadPool threadPool) {
        this.taskService = taskService;
        this.threadPool = threadPool;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void execute(ScheduledTask task) {
        if (!polling.compareAndSet(false, true)) {
            logger.debug("上一轮工作流任务轮询尚未结束，跳过本次触发");
            return;
        }
        try {
            pollPendingTasks();
        } finally {
            polling.set(false);
        }
    }

    private void pollPendingTasks() {
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
                // 前置条件检查：所有前置任务状态命中要求状态（多选任意命中）才允许执行
                if (!taskService.isPreconditionsSatisfied(task.getId())) {
                    logger.info("任务前置条件未满足，暂缓拉起: id={}, title={}", task.getId(), task.getTitle());
                    continue;
                }
                // 提交到线程池异步执行：同一任务重复提交由线程池内部去重
                boolean accepted = threadPool.submitTask(task.getId(), () -> {
                    try {
                        logger.info("拉起任务: id={}, title={}", task.getId(), task.getTitle());
                        taskService.executeTask(task.getId());
                    } catch (Exception e) {
                        logger.error("任务执行失败: id={}, error={}", task.getId(), e.getMessage(), e);
                        taskService.updateTaskStatus(task.getId(), TaskStatusEnum.FAILED.getCode(), e.getMessage());
                    }
                });
                if (!accepted) {
                    logger.debug("任务已提交过，跳过重复拉起: id={}", task.getId());
                }
            } catch (RejectedExecutionException e) {
                // 线程池队列已满：任务状态未变，等待下轮轮询自动重试
                logger.warn("线程池已满，任务等待下轮重试: id={}, title={}", task.getId(), task.getTitle());
            } catch (Exception e) {
                logger.error("拉起任务失败: id={}, error={}", task.getId(), e.getMessage(), e);
                taskService.updateTaskStatus(task.getId(), TaskStatusEnum.FAILED.getCode(), e.getMessage());
            }
        }
    }
}
