package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.service.IWorkflowTaskService;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
public class WorkflowTaskController {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowTaskController.class);
    private final IWorkflowTaskService taskService;
    private final ProjectController projectController;

    public WorkflowTaskController(IWorkflowTaskService taskService, ProjectController projectController) {
        this.taskService = taskService;
        this.projectController = projectController;
    }

    // 看板页面
    @GetMapping("/tasks-board")
    public String board(Model model) {
        return "tasks-board";
    }

    // 任务详情页面 (新窗口)
    @GetMapping("/tasks-board/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("taskId", id);
        return "task-detail";
    }

    // 看板数据 (按状态分组)
    @GetMapping("/api/workflow/tasks/board")
    @ResponseBody
    public ResponseBean getBoardData(HttpServletRequest request,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long agentId) {
        String userId = CurrentUser.require(request);
        Map<String, Object> result = new LinkedHashMap<>();
        String[] statuses = {"pending", "pending_execution", "processing", "pending_acceptance", "completed", "failed"};
        for (String status : statuses) {
            // 需要按 userId + status + projectId + agentId 查询
            // 但 getTasksByStatus 只接受 userId + status
            // 改用 getTasksPage 大 size 查询
            List<WorkflowTask> tasks = taskService.getTasksPage(userId, null, status, projectId, agentId, 1, 1000);
            result.put(status, tasks);
        }
        return ResponseBean.success(result);
    }

    // 任务详情
    @GetMapping("/api/workflow/tasks/{id}")
    @ResponseBody
    public ResponseBean getTask(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        WorkflowTask task = taskService.getTask(id, userId);
        if (task == null) {
            return ResponseBean.fail("任务不存在");
        }
        return ResponseBean.success(task);
    }

    // 创建任务
    @PostMapping("/api/workflow/tasks")
    @ResponseBody
    public ResponseBean createTask(HttpServletRequest request, @RequestBody WorkflowTask task) {
        String userId = CurrentUser.require(request);
        task.setUserId(userId);
        WorkflowTask created = taskService.createTask(task);
        // 若任务关联了项目，记录项目操作日志
        if (created.getProjectId() != null) {
            try {
                projectController.logTaskBind(created.getProjectId(), userId, created.getId(), created.getTitle());
            } catch (Exception ex) {
                logger.warn("记录任务关联项目日志失败: projectId={}", created.getProjectId(), ex);
            }
        }
        return ResponseBean.success(created);
    }

    // 更新任务
    @PutMapping("/api/workflow/tasks/{id}")
    @ResponseBody
    public ResponseBean updateTask(HttpServletRequest request, @PathVariable Long id, @RequestBody WorkflowTask task) {
        String userId = CurrentUser.require(request);
        task.setId(id);
        try {
            // 查出原任务，用于对比 projectId 是否变化
            WorkflowTask before = taskService.getTask(id, userId);
            WorkflowTask updated = taskService.updateTask(task, userId);
            // 若 projectId 发生变化，记录项目操作日志
            if (before != null) {
                Long oldPid = before.getProjectId();
                Long newPid = updated.getProjectId();
                if (!Objects.equals(oldPid, newPid)) {
                    try {
                        if (oldPid != null) {
                            projectController.logTaskUnbind(oldPid, userId, id, before.getTitle());
                        }
                        if (newPid != null) {
                            projectController.logTaskBind(newPid, userId, id, updated.getTitle());
                        }
                    } catch (Exception ex) {
                        logger.warn("记录任务关联项目变化日志失败: taskId={}", id, ex);
                    }
                }
            }
            return ResponseBean.success(updated);
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 删除任务
    @DeleteMapping("/api/workflow/tasks/{id}")
    @ResponseBody
    public ResponseBean deleteTask(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            // 删除前查出任务，若关联了项目则记录取消关联日志
            WorkflowTask before = taskService.getTask(id, userId);
            if (before != null && before.getProjectId() != null) {
                try {
                    projectController.logTaskUnbind(before.getProjectId(), userId, id, before.getTitle());
                } catch (Exception ex) {
                    logger.warn("记录删除任务取消关联日志失败: taskId={}", id, ex);
                }
            }
            taskService.deleteTask(id, userId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 审核
    @PutMapping("/api/workflow/tasks/{id}/approve")
    @ResponseBody
    public ResponseBean approveTask(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            taskService.approveTask(id, userId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 验收通过
    @PutMapping("/api/workflow/tasks/{id}/accept")
    @ResponseBody
    public ResponseBean acceptTask(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            taskService.acceptTask(id, userId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 打回重做
    @PutMapping("/api/workflow/tasks/{id}/reject")
    @ResponseBody
    public ResponseBean rejectTask(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        String userId = CurrentUser.require(request);
        String reason = body.get("reason");
        try {
            taskService.rejectTask(id, userId, reason);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 关闭任务
    @PutMapping("/api/workflow/tasks/{id}/close")
    @ResponseBody
    public ResponseBean closeTask(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            taskService.closeTask(id, userId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 任务会话列表
    @GetMapping("/api/workflow/tasks/{id}/sessions")
    @ResponseBody
    public ResponseBean getTaskSessions(@PathVariable Long id) {
        return ResponseBean.success(taskService.getTaskSessions(id));
    }
}
