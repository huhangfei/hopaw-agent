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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 执行时段格式：HH:mm-HH:mm（每日允许拉起的时间窗口，支持跨天如 22:00-06:00） */
    private static final Pattern EXECUTION_PERIOD_PATTERN =
            Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d)-([01]\\d|2[0-3]):([0-5]\\d)$");

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
                // 执行时段窗口检查：当前时刻不在每日允许拉起的时间窗口内时暂缓
                if (!isWithinExecutionWindow(task.getExecutionPeriod())) {
                    logger.debug("任务不在执行时段窗口内，暂缓拉起: id={}, window={}", task.getId(), task.getExecutionPeriod());
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

    /**
     * 判断当前时刻是否在执行时段窗口内：
     * - 窗口为空、格式不合法或结束<=开始（历史脏数据）：不限制，视为命中
     * - 合法窗口（start < end，如 09:00-18:00）：start <= 当前 <= end
     */
    private boolean isWithinExecutionWindow(String window) {
        if (window == null || window.trim().isEmpty()) {
            return true;
        }
        Matcher matcher = EXECUTION_PERIOD_PATTERN.matcher(window.trim());
        if (!matcher.matches()) {
            return true;
        }
        int startMin = Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
        int endMin = Integer.parseInt(matcher.group(3)) * 60 + Integer.parseInt(matcher.group(4));
        if (startMin >= endMin) {
            // 反向窗口视为非法数据：不限制
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        int curMin = now.getHour() * 60 + now.getMinute();
        return curMin >= startMin && curMin <= endMin;
    }
}
