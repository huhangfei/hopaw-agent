package com.agent.hopaw.infra.task;

import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
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

    public WorkflowTaskExecutionListener(IWorkflowTaskService taskService) {
        this.taskService = taskService;
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
            logger.info("任务执行完成，更新状态为待验收: taskId={}", taskId);
            taskService.updateTaskStatus(taskId, "pending_acceptance", null);
        } else if ("error".equals(type)) {
            logger.info("任务执行失败: taskId={}, error={}", taskId, message.getContent());
            taskService.updateTaskStatus(taskId, "failed", message.getContent());
        }
    }
}
