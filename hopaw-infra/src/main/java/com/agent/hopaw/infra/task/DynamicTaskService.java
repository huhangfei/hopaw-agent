package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.mapper.ScheduledTaskMapper;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class DynamicTaskService {
    private static final Logger logger = LoggerFactory.getLogger(DynamicTaskService.class);

    private final ThreadPoolTaskScheduler taskScheduler;
    private final ScheduledTaskMapper taskMapper;
    private final Map<String, TaskHandler> handlerMap;
    private final Map<Long, ScheduledFuture<?>> runningTasks = new ConcurrentHashMap<>();

    public DynamicTaskService(ThreadPoolTaskScheduler taskScheduler,
                              ScheduledTaskMapper taskMapper,
                              List<TaskHandler> handlers) {
        this.taskScheduler = taskScheduler;
        this.taskMapper = taskMapper;
        this.handlerMap = new ConcurrentHashMap<>();
        for (TaskHandler handler : handlers) {
            handlerMap.put(handler.getType(), handler);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        List<ScheduledTask> tasks = taskMapper.findAll();
        for (ScheduledTask task : tasks) {
            if (task.getEnabled() != null && task.getEnabled() == 1) {
                scheduleTask(task);
            }
        }
        logger.info("动态定时任务初始化完成，已加载 {} 个任务", tasks.size());
    }

    public void scheduleTask(ScheduledTask task) {
        TaskHandler handler = handlerMap.get(task.getTaskType());
        if (handler == null) {
            logger.warn("未知的任务类型: {}", task.getTaskType());
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(task.getCronExpression());
            ScheduledFuture<?> future = taskScheduler.schedule(() -> {
                try {
                    handler.execute(task);
                } catch (Exception e) {
                    logger.error("任务执行异常 [{}] {}", task.getId(), task.getTaskName(), e);
                }
            }, trigger);
            runningTasks.put(task.getId(), future);
            logger.info("任务已调度 [{}] {} - cron: {}", task.getId(), task.getTaskName(), task.getCronExpression());
        } catch (Exception e) {
            logger.error("任务调度失败 [{}] {}", task.getId(), task.getTaskName(), e);
        }
    }

    public void cancelTask(Long taskId) {
        ScheduledFuture<?> future = runningTasks.remove(taskId);
        if (future != null) {
            future.cancel(false);
            logger.info("任务已取消 [{}]", taskId);
        }
    }

    public void cancelAll() {
        for (Long id : runningTasks.keySet()) {
            cancelTask(id);
        }
    }

    public boolean isRunning(Long taskId) {
        ScheduledFuture<?> future = runningTasks.get(taskId);
        return future != null && !future.isCancelled();
    }
}
