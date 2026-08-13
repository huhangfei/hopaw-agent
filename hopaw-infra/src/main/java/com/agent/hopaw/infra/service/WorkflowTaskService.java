package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.mapper.TaskAttachmentMapper;
import com.agent.hopaw.infra.mapper.TaskSessionMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.model.entity.Agent;
import com.agent.hopaw.infra.model.entity.TaskAttachment;
import com.agent.hopaw.infra.model.entity.TaskComment;
import com.agent.hopaw.infra.model.entity.TaskSession;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkflowTaskService implements IWorkflowTaskService {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskService.class);

    private final WorkflowTaskMapper workflowTaskMapper;
    private final TaskAttachmentMapper taskAttachmentMapper;
    private final TaskSessionMapper taskSessionMapper;
    private final IAgentExecutorService agentExecutorService;
    private final IChatSessionService chatSessionService;
    private final IAgentService agentService;
    private final ITaskCommentService taskCommentService;

    public WorkflowTaskService(WorkflowTaskMapper workflowTaskMapper,
                                TaskAttachmentMapper taskAttachmentMapper,
                                TaskSessionMapper taskSessionMapper,
                                IAgentExecutorService agentExecutorService,
                                IChatSessionService chatSessionService,
                                IAgentService agentService,
                                ITaskCommentService taskCommentService) {
        this.workflowTaskMapper = workflowTaskMapper;
        this.taskAttachmentMapper = taskAttachmentMapper;
        this.taskSessionMapper = taskSessionMapper;
        this.agentExecutorService = agentExecutorService;
        this.chatSessionService = chatSessionService;
        this.agentService = agentService;
        this.taskCommentService = taskCommentService;
    }

    @Override
    public WorkflowTask createTask(WorkflowTask task) {
        task.setStatus("pending");
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTime(now);
        task.setUpdateTime(now);
        workflowTaskMapper.insert(task);
        return task;
    }

    @Override
    public WorkflowTask updateTask(WorkflowTask task, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(task.getId());
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该任务");
        }
        existing.setTitle(task.getTitle());
        existing.setContent(task.getContent());
        if (task.getProjectId() != null) {
            existing.setProjectId(task.getProjectId());
        }
        if (task.getAgentId() != null) {
            existing.setAgentId(task.getAgentId());
        }
        if (task.getStartTime() != null) {
            existing.setStartTime(task.getStartTime());
        }
        if (task.getExecutionPeriod() != null) {
            existing.setExecutionPeriod(task.getExecutionPeriod());
        }
        existing.setUpdateTime(LocalDateTime.now());
        workflowTaskMapper.update(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deleteTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该任务");
        }
        taskAttachmentMapper.deleteByTaskId(id);
        taskSessionMapper.deleteByTaskId(id);
        workflowTaskMapper.deleteById(id);
    }

    @Override
    public WorkflowTask getTask(Long id, String userId) {
        WorkflowTask task = workflowTaskMapper.findById(id);
        if (task == null) {
            return null;
        }
        if (!userId.equals(task.getUserId())) {
            return null;
        }
        return task;
    }

    @Override
    public List<WorkflowTask> getTasksByStatus(String userId, String status) {
        return workflowTaskMapper.findByUserIdAndStatus(userId, status);
    }

    @Override
    public List<WorkflowTask> getTasksPage(String userId, String keyword, String status, Long projectId, Long agentId, int page, int size) {
        int offset = (page - 1) * size;
        return workflowTaskMapper.findByUserIdWithFilters(userId, keyword, status, projectId, agentId, offset, size);
    }

    @Override
    public int countTasks(String userId, String keyword, String status, Long projectId, Long agentId) {
        return workflowTaskMapper.countByUserIdWithFilters(userId, keyword, status, projectId, agentId);
    }

    @Override
    public void approveTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        if (!"pending".equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许审批");
        }
        updateTaskStatus(id, "pending_execution", null);
    }

    @Override
    public void acceptTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        if (!"pending_acceptance".equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许验收");
        }
        updateTaskStatus(id, "completed", null);
    }

    @Override
    public void rejectTask(Long id, String userId, String reason) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        if (!"pending_acceptance".equals(existing.getStatus())) {
            throw new RuntimeException("当前任务状态不允许驳回");
        }
        // 记录驳回原因
        updateTaskStatus(id, "rejected", reason);
        // 立即重新执行
        updateTaskStatus(id, "processing", null);
        executeTask(id);
    }

    @Override
    public void closeTask(Long id, String userId) {
        WorkflowTask existing = workflowTaskMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权操作该任务");
        }
        updateTaskStatus(id, "closed", null);
    }

    @Override
    public List<WorkflowTask> findPendingExecution() {
        return workflowTaskMapper.findPendingExecution();
    }

    @Override
    public void executeTask(Long taskId) {
        WorkflowTask task = workflowTaskMapper.findById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        Agent agent = agentService.getAgentById(task.getAgentId());
        if (agent == null) {
            throw new RuntimeException("智能体不存在");
        }
        // 更新状态为 processing
        updateTaskStatus(taskId, "processing", null);
        // 获取评论历史
        List<TaskComment> comments = taskCommentService.getCommentsByTaskId(taskId);
        // 查询已关联会话：打回重做时复用最近一次会话编号，保留上下文记忆
        List<TaskSession> sessions = taskSessionMapper.findByTaskId(taskId);
        String existingSessionId = null;
        if (sessions != null && !sessions.isEmpty()) {
            // findByTaskId 按 id ASC 返回，取最后一条为最近会话
            existingSessionId = sessions.get(sessions.size() - 1).getSessionId();
        }
        // 创建任务执行器（复用或新建会话）
        IAgentExecutor executor = agentExecutorService.createTaskExecutor(task, agent, comments, existingSessionId);
        String sessionId = executor.getSessionId();
        // 仅新建会话时记录 task_sessions 关联并更新业务类型
        if (existingSessionId == null) {
            taskSessionMapper.insert(taskId, sessionId);
            chatSessionService.updateBizType(sessionId, "task");
        }
        // 执行
        executor.execute();
    }

    @Override
    public void updateTaskStatus(Long taskId, String status, String rejectReason) {
        workflowTaskMapper.updateStatus(taskId, status, rejectReason);
    }

    @Override
    @Transactional
    public void bindAttachments(Long taskId, List<Long> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        for (Long attachmentId : attachmentIds) {
            taskAttachmentMapper.insert(taskId, attachmentId);
        }
    }

    @Override
    public void unbindAttachment(Long taskId, Long attachmentId) {
        taskAttachmentMapper.deleteByTaskIdAndAttachmentId(taskId, attachmentId);
    }

    @Override
    public List<TaskAttachment> getTaskAttachments(Long taskId) {
        List<TaskAttachment> list = taskAttachmentMapper.findByTaskId(taskId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public List<TaskSession> getTaskSessions(Long taskId) {
        List<TaskSession> list = taskSessionMapper.findByTaskId(taskId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public Long findTaskIdBySessionId(String sessionId) {
        return taskSessionMapper.findTaskIdBySessionId(sessionId);
    }
}
