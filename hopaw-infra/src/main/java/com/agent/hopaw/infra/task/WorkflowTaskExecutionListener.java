package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.constant.TaskCommenterTypeEnum;
import com.agent.hopaw.infra.constant.TaskStatusEnum;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.service.IWorkflowTaskCommentService;
import com.agent.hopaw.infra.service.IWorkflowTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 监听智能体执行完成事件，更新工作流任务状态
 */
@Component
public class WorkflowTaskExecutionListener {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskExecutionListener.class);
    private final IWorkflowTaskService taskService;
    private final IWorkflowTaskCommentService taskCommentService;

    public WorkflowTaskExecutionListener(IWorkflowTaskService taskService,
                                         IWorkflowTaskCommentService taskCommentService) {
        this.taskService = taskService;
        this.taskCommentService = taskCommentService;
    }

    @EventListener
    public void onAgentMessage(AgentMessageEvent event) {
        AiMessageBaseInfo message = event.getMessage();
        if (message == null || message.getSessionId() == null) {
            return;
        }
        String type = message.getType();
        if (!"done".equals(type) && !"task-done".equals(type) && !"error".equals(type)) {
            return;
        }
        // 通过 task_sessions 关系表反查任务ID
        Long taskId = taskService.findTaskIdBySessionId(message.getSessionId());
        if (taskId == null) {
            return; // 非任务会话
        }
        if ("done".equals(type) || "task-done".equals(type)) {
            updateStatusIfTransitionAllowed(taskId, TaskStatusEnum.PENDING_ACCEPTANCE, null);
        } else if ("error".equals(type)) {
            if (updateStatusIfTransitionAllowed(taskId, TaskStatusEnum.FAILED, message.getContent())) {
                addFailureComment(taskId, message.getContent());
            }
        }
    }

    /**
     * 任务置为失败后，异常信息同时写入任务评论（智能体身份的普通评论），便于在评论时间线中追溯失败原因
     */
    private void addFailureComment(Long taskId, String errorContent) {
        try {
            WorkflowTask task = taskService.getTaskById(taskId);
            if (task == null) {
                return;
            }
            String content = (errorContent != null && !errorContent.trim().isEmpty())
                    ? errorContent : "任务执行失败（未知异常）";
            taskCommentService.addComment(taskId, content, task.getUserId(),
                    TaskCommenterTypeEnum.AGENT.getCode(),
                    task.getAgentId() != null ? String.valueOf(task.getAgentId()) : null);
        } catch (Exception e) {
            logger.warn("任务失败异常信息写入任务评论失败: taskId={}", taskId, e);
        }
    }

    /**
     * 按正常状态流转规则更新任务状态：仅当当前状态允许流转到目标状态时才更新，否则跳过
     * （例如任务已被用户关闭/验收后，迟到的执行完成事件不应再覆盖状态）
     *
     * @return 是否实际更新了状态
     */
    private boolean updateStatusIfTransitionAllowed(Long taskId, TaskStatusEnum target, String rejectReason) {
        WorkflowTask task = taskService.getTaskById(taskId);
        if (task == null) {
            logger.warn("任务不存在，跳过状态更新: taskId={}, target={}", taskId, target.getCode());
            return false;
        }
        TaskStatusEnum current = TaskStatusEnum.fromCode(task.getStatus());
        if (current == null || !current.canTransitionTo(target)) {
            logger.info("任务状态不允许流转，跳过更新: taskId={}, current={}, target={}",
                    taskId, task.getStatus(), target.getCode());
            return false;
        }
        logger.info("任务执行事件更新状态: taskId={}, {} -> {}", taskId, current.getCode(), target.getCode());
        taskService.updateTaskStatus(taskId, target.getCode(), rejectReason);
        return true;
    }
}
