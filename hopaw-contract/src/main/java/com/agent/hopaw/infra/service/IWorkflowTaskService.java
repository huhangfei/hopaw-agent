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

    /** 重做任务：已完成/失败的任务重置为待执行，由后台调度器拉起重跑 */
    void redoTask(Long id, String userId);
    List<WorkflowTask> findPendingExecution();
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
