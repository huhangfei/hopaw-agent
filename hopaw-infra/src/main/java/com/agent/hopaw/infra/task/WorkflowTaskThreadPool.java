package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.service.ISysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 工作流任务线程池：支持多任务并发执行。
 * 核心线程数/最大线程数/队列容量均从系统配置读取（设置页可调，保存后动态重建）。
 * 内置内存级 in-flight 集合防止同一任务被重复提交（任务排队期间状态仍为待执行，
 * 调度器每轮轮询都会查到它，靠该集合去重；应用重启后集合与队列同时清空，不丢任务也不重复）。
 */
@Component("workflowTaskThreadPool")
public class WorkflowTaskThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskThreadPool.class);

    /** 配置项：核心线程数 */
    public static final String CONFIG_CORE_SIZE = "workflow_pool_core_size";
    /** 配置项：最大线程数 */
    public static final String CONFIG_MAX_SIZE = "workflow_pool_max_size";
    /** 配置项：队列容量（排队任务数上限） */
    public static final String CONFIG_QUEUE_CAPACITY = "workflow_pool_queue_capacity";

    /** 默认值：核心线程数 */
    public static final int DEFAULT_CORE_SIZE = 2;
    /** 默认值：最大线程数 */
    public static final int DEFAULT_MAX_SIZE = 4;
    /** 默认值：队列容量 */
    public static final int DEFAULT_QUEUE_CAPACITY = 20;

    private final ISysConfigService sysConfigService;

    private ThreadPoolTaskExecutor executor;
    /** 已提交（执行中或排队中）的任务ID集合，防止重复提交 */
    private final Set<Long> inFlightTaskIds = ConcurrentHashMap.newKeySet();

    public WorkflowTaskThreadPool(ISysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    /**
     * 提交任务执行：同一任务重复提交直接跳过；队列满时抛出拒绝异常由调用方决定后续处理。
     *
     * @return true=已受理（执行中或已入队），false=重复提交已跳过
     */
    public boolean submitTask(Long taskId, Runnable runnable) {
        if (!inFlightTaskIds.add(taskId)) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    runnable.run();
                } finally {
                    inFlightTaskIds.remove(taskId);
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            // 队列已满：移除标记，任务状态未变，等待下轮轮询重试
            inFlightTaskIds.remove(taskId);
            throw e;
        }
    }

    /** 按当前系统配置重建线程池（旧池等待已提交任务执行完后关闭） */
    public synchronized void reload() {
        int core = readIntConfig(CONFIG_CORE_SIZE, DEFAULT_CORE_SIZE, 1, 32);
        int max = readIntConfig(CONFIG_MAX_SIZE, DEFAULT_MAX_SIZE, core, 64);
        int queue = readIntConfig(CONFIG_QUEUE_CAPACITY, DEFAULT_QUEUE_CAPACITY, 0, 1000);

        ThreadPoolTaskExecutor old = this.executor;
        ThreadPoolTaskExecutor fresh = buildExecutor(core, max, queue);
        // 先初始化新池再关闭旧池，保证任意时刻都有可用池
        fresh.initialize();
        this.executor = fresh;
        if (old != null) {
            // shutdown 会执行完已提交任务（含排队中的），不中断正在执行的任务
            old.shutdown();
        }
        logger.info("工作流任务线程池已重建: core={}, max={}, queueCapacity={}", core, max, queue);
    }

    /** 线程池运行状态（供设置页展示） */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        if (executor == null) {
            return stats;
        }
        stats.put("corePoolSize", executor.getCorePoolSize());
        stats.put("maxPoolSize", executor.getMaxPoolSize());
        stats.put("queueCapacity", executor.getQueueCapacity());
        stats.put("activeCount", executor.getActiveCount());
        stats.put("poolSize", executor.getPoolSize());
        stats.put("queuedTasks", executor.getThreadPoolExecutor().getQueue().size());
        stats.put("inFlightTasks", inFlightTaskIds.size());
        stats.put("completedTasks", executor.getThreadPoolExecutor().getCompletedTaskCount());
        return stats;
    }

    private ThreadPoolTaskExecutor buildExecutor(int core, int max, int queue) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("workflow-task-");
        pool.setCorePoolSize(core);
        pool.setMaxPoolSize(max);
        pool.setQueueCapacity(queue);
        // 队列满且达到最大线程数时由调用方捕获拒绝异常，下轮轮询重试
        pool.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        // 应用空闲时回收核心线程，避免长期占用
        pool.setAllowCoreThreadTimeOut(true);
        pool.setKeepAliveSeconds(60);
        // 关闭时等待已提交任务完成
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(60);
        return pool;
    }

    /** 读取整型配置：带默认值与上下限裁剪，非法值回退默认值 */
    private int readIntConfig(String key, int defaultValue, int min, int max) {
        String raw;
        try {
            raw = sysConfigService.getValueByKey(key, String.valueOf(defaultValue));
        } catch (Exception e) {
            // 首次启动空库时表尚未由 DatabaseInitializer 创建（其建表时机晚于 bean 初始化），
            // 回退默认值保证应用能启动；建表后可在设置页调整并动态重建
            logger.warn("读取线程池配置失败（库表可能尚未初始化），使用默认值: key={}, 原因: {}", key, e.getMessage());
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            if (v < min) {
                return min;
            }
            return Math.min(v, max);
        } catch (NumberFormatException e) {
            logger.warn("线程池配置非法，回退默认值: key={}, value={}", key, raw);
            return defaultValue;
        }
    }

    @javax.annotation.PostConstruct
    void init() {
        reload();
    }

    @PreDestroy
    void destroy() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
