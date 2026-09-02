package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.UserChatRequest;
import com.agent.hopaw.infra.model.entity.TaskSession;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.model.entity.WorkflowTaskPrecondition;

import java.util.List;

/**
 * 工作流任务服务接口
 */
public interface IWorkflowTaskService {
    WorkflowTask createTask(WorkflowTask task);
    WorkflowTask updateTask(WorkflowTask task, String userId);
    void deleteTask(Long id, String userId);
    WorkflowTask getTask(Long id, String userId);
    List<WorkflowTask> getTasksByStatus(String userId, String status);
    List<WorkflowTask> getTasksPage(String userId, String keyword, String status, Long projectId, Long agentId, int page, int size);
    int countTasks(String userId, String keyword, String status, Long projectId, Long agentId);
    void approveTask(Long id, String userId);
    void acceptTask(Long id, String userId);
    void rejectTask(Long id, String userId, String reason);
    void closeTask(Long id, String userId);

    /**
     * 关闭任务（带评论者身份）：智能体关闭时自动评论以智能体身份写入，
     * 用户关闭时以用户身份写入；处理中的任务不允许关闭
     */
    void closeTask(Long id, String userId, String commenterType, String commenterId);

    /**
     * 审核任务（带评论者身份）：智能体审核时自动评论以智能体身份写入，
     * 用户审核时以用户身份写入
     *
     * @param id 任务编号
     * @param userId 任务归属用户
     * @param commenterType 评论者类型：user / agent
     * @param commenterId 评论者编号（智能体审核时为智能体ID）
     */
    void approveTask(Long id, String userId, String commenterType, String commenterId);

    /**
     * 验收任务（带评论者身份）：智能体验收时自动评论以智能体身份写入，
     * 用户验收时以用户身份写入
     */
    void acceptTask(Long id, String userId, String commenterType, String commenterId);

    /**
     * 驳回任务（带评论者身份）：智能体驳回时自动评论以智能体身份写入，
     * 用户驳回时以用户身份写入
     */
    void rejectTask(Long id, String userId, String reason, String commenterType, String commenterId);

    /** 重做任务：已完成/失败的任务重置为待执行，由后台调度器拉起重跑 */
    void redoTask(Long id, String userId);
    List<WorkflowTask> findPendingExecution();

    /** 查询所有处理中状态的任务（按ID正序），供中断恢复扫描使用 */
    List<WorkflowTask> findProcessing();

    void executeTask(Long taskId);
    void executeTask(UserChatRequest userChatRequest);
    void updateTaskStatus(Long taskId, String status, String rejectReason);
    List<TaskSession> getTaskSessions(Long taskId);
    Long findTaskIdBySessionId(String sessionId);
    List<String> getSessionIdsByProjectId(Long projectId);

    /** 按ID查询任务（不做用户归属校验，供内部流程使用） */
    WorkflowTask getTaskById(Long id);

    /** 查询任务配置的前置条件列表 */
    List<WorkflowTaskPrecondition> getPreconditions(Long taskId);

    /**
     * 检查任务所有前置条件是否满足：每个前置任务的当前状态命中其要求状态（多选，任意命中即可）即满足；
     * 未配置前置条件视为满足
     */
    boolean isPreconditionsSatisfied(Long taskId);
}
