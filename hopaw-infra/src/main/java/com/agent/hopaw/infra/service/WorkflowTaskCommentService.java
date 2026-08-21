package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.ProjectLogTypeEnum;
import com.agent.hopaw.infra.constant.TaskCommenterTypeEnum;
import com.agent.hopaw.infra.constant.TaskCommentStatusEnum;
import com.agent.hopaw.infra.constant.TaskCommentTypeEnum;
import com.agent.hopaw.infra.mapper.TaskCommentMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.WorkflowTaskComment;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkflowTaskCommentService implements IWorkflowTaskCommentService {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskCommentService.class);

    private final TaskCommentMapper taskCommentMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final IProjectLogService projectLogService;
    private final IAgentService agentService;

    public WorkflowTaskCommentService(TaskCommentMapper taskCommentMapper,
                                      WorkflowTaskMapper workflowTaskMapper,
                                      IProjectLogService projectLogService,
                                      IAgentService agentService) {
        this.taskCommentMapper = taskCommentMapper;
        this.workflowTaskMapper = workflowTaskMapper;
        this.projectLogService = projectLogService;
        this.agentService = agentService;
    }

    @Override
    public WorkflowTaskComment addComment(Long taskId, String content, String userId) {
        // 用户评论：评论者身份默认为 user
        return addComment(taskId, content, userId, TaskCommenterTypeEnum.USER.getCode(), userId, TaskCommentTypeEnum.DEFAULT.getCode());
    }

    @Override
    public WorkflowTaskComment addComment(Long taskId, String content, String userId, String commenterType, String commenterId) {
        return addComment(taskId, content, userId, commenterType, commenterId, TaskCommentTypeEnum.DEFAULT.getCode());
    }

    @Override
    public WorkflowTaskComment addComment(Long taskId, String content, String userId, String commenterType, String commenterId, String commentType) {
        TaskCommentTypeEnum typeEnum = TaskCommentTypeEnum.fromCode(commentType);
        WorkflowTaskComment comment = new WorkflowTaskComment();
        comment.setTaskId(taskId);
        comment.setContent(content);
        comment.setUserId(userId);
        comment.setCommenterType(commenterType);
        comment.setCommenterId(commenterId);
        comment.setCommentType(typeEnum.getCode());
        comment.setCreateTime(LocalDateTime.now());
        comment.setStatus(TaskCommentStatusEnum.PENDING.getCode());
        taskCommentMapper.insert(comment);
        logCommentToProject(taskId, userId, commenterType, commenterId, typeEnum, content);
        return comment;
    }

    /** 新增任务评论写入关联项目的操作日志（不记录评论内容，仅记录动作） */
    private void logCommentToProject(Long taskId, String userId, String commenterType, String commenterId, TaskCommentTypeEnum commentType, String content) {
        try {
            WorkflowTask task = workflowTaskMapper.findById(taskId);
            if (task == null || task.getProjectId() == null) {
                return;
            }
            String taskLabel = "任务「" + (task.getTitle() != null ? task.getTitle() : "#" + taskId) + "」(#" + taskId + ")";
            boolean byAgent = TaskCommenterTypeEnum.isAgent(commenterType);
            String detail = taskLabel + " 新增" + (byAgent ? TaskCommenterTypeEnum.AGENT.getDescription() : TaskCommenterTypeEnum.USER.getDescription()) + "评论";
            if (commentType.isSummary()) {
                detail +="："+ content;
            }
            // 评论类型 → 项目日志类型映射：普通评论→默认日志，总结评论→重点日志
            String logType = commentType.isSummary() ? ProjectLogTypeEnum.IMPORTANT.getCode() : ProjectLogTypeEnum.DEFAULT.getCode();
            if (byAgent) {
                String operatorName = "智能体";
                if (commenterId != null) {
                    Agent agent = agentService.getAgentById(Long.valueOf(commenterId));
                    if (agent != null && agent.getName() != null) {
                        operatorName = "智能体「" + agent.getName() + "」";
                    }
                }
                projectLogService.log(task.getProjectId(), task.getUserId(), operatorName, "task_comment", detail, logType);
            } else {
                projectLogService.log(task.getProjectId(), userId, "task_comment", detail, logType);
            }
        } catch (Exception e) {
            logger.warn("任务评论写入项目日志失败: taskId={}", taskId, e);
        }
    }

    @Override
    public void deleteComment(Long id, String userId) {
        WorkflowTaskComment existing = taskCommentMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("评论不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该评论");
        }
        taskCommentMapper.deleteById(id);
    }

    @Override
    public List<WorkflowTaskComment> getCommentsByTaskId(Long taskId) {
        List<WorkflowTaskComment> list = taskCommentMapper.findByTaskId(taskId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public List<WorkflowTaskComment> getPendingCommentsByTaskId(Long taskId) {
        List<WorkflowTaskComment> list = taskCommentMapper.findByTaskIdAndStatus(taskId, TaskCommentStatusEnum.PENDING.getCode());
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public void markCommentsAsProcessed(List<Long> commentIds) {
        if (commentIds == null || commentIds.isEmpty()) {
            return;
        }
        taskCommentMapper.updateStatusByIds(commentIds, TaskCommentStatusEnum.PROCESSED.getCode());
    }
}
