package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.service.IProjectIterateService;
import com.agent.hopaw.infra.service.IProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 项目自动迭代轮询 Handler：复用 DynamicTaskService 动态定时任务机制。
 * 每次触发时查询「配置了项目管理智能体 + 启用自动迭代 + 进行中」的项目，
 * 提交到项目线程池执行一轮项目管理智能体迭代（分析项目/创建任务/审核验收/完结项目）。
 * 线程池核心/最大线程数与队列容量可在设置页调整（project_pool_* 配置项）。
 * 轮询间隔/启停可在设置页「定时任务」中调整（内置任务，类型 projectAutoIterate）。
 */
@Component("projectAutoIterateTaskHandler")
public class ProjectAutoIterateTaskHandler implements TaskHandler {
    private static final Logger logger = LoggerFactory.getLogger(ProjectAutoIterateTaskHandler.class);

    /** 任务类型标识，对应 scheduled_tasks.task_type */
    public static final String TYPE = "projectAutoIterate";

    private final IProjectService projectService;
    private final IProjectIterateService projectIterateService;
    /** 项目迭代专属线程池：与工作流任务线程池隔离（迭代为长耗时智能体执行） */
    private final ProjectThreadPool projectThreadPool;

    public ProjectAutoIterateTaskHandler(IProjectService projectService,
                                         IProjectIterateService projectIterateService,
                                         ProjectThreadPool projectThreadPool) {
        this.projectService = projectService;
        this.projectIterateService = projectIterateService;
        this.projectThreadPool = projectThreadPool;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void execute(ScheduledTask task) {
        List<Project> projects = projectService.findAutoIterateProjects();
        if (projects == null || projects.isEmpty()) {
            return;
        }
        for (Project project : projects) {
            try {
                // 重复提交由线程池内置 in-flight 集合去重：上一轮迭代未结束时跳过本轮
                boolean accepted = projectThreadPool.submitProject(project.getId(), () -> {
                    try {
                        projectIterateService.executeProjectIterate(project.getId());
                    } catch (Exception e) {
                        logger.error("项目自动迭代执行失败: projectId={}, error={}", project.getId(), e.getMessage(), e);
                    }
                });
                if (!accepted) {
                    logger.debug("项目自动迭代：上一轮迭代未结束，跳过 id={}", project.getId());
                }
            } catch (Exception e) {
                // 线程池拒绝等异常：等待下轮轮询重试
                logger.warn("项目自动迭代提交失败: projectId={}, error={}", project.getId(), e.getMessage());
            }
        }
    }
}