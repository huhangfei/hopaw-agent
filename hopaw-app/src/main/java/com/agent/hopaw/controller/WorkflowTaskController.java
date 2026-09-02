package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.TaskStatusEnum;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.service.IAccountService;
import com.agent.hopaw.infra.service.IBizTokenUsageService;
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
    private final IBizTokenUsageService bizTokenUsageService;
    private final IAccountService accountService;

    public WorkflowTaskController(IWorkflowTaskService taskService, ProjectController projectController,
                                  IBizTokenUsageService bizTokenUsageService, IAccountService accountService) {
        this.taskService = taskService;
        this.projectController = projectController;
        this.bizTokenUsageService = bizTokenUsageService;
        this.accountService = accountService;
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
        for (TaskStatusEnum status : TaskStatusEnum.boardOrder()) {
            // 需要按 userId + status + projectId + agentId 查询
            // 但 getTasksByStatus 只接受 userId + status
            // 改用 getTasksPage 大 size 查询
            List<WorkflowTask> tasks = taskService.getTasksPage(userId, null, status.getCode(), projectId, agentId, 1, 1000);
            tasks.forEach(this::fillCreatorInfo);
            result.put(status.getCode(), tasks);
        }
        return ResponseBean.success(result);
    }

    // 画布视图数据：项目全部任务（含前置依赖关系，供前端构建节点和连线）
    @GetMapping("/api/workflow/tasks/graph")
    @ResponseBody
    public ResponseBean getGraphData(HttpServletRequest request,
            @RequestParam Long projectId) {
        String userId = CurrentUser.require(request);
        if (projectId == null) {
            return ResponseBean.fail("画布视图必须选择项目");
        }
        List<WorkflowTask> tasks = taskService.getTasksPage(userId, null, null, projectId, null, 1, 1000);
        for (WorkflowTask task : tasks) {
            fillCreatorInfo(task);
            task.setPreconditions(taskService.getPreconditions(task.getId()));
        }
        return ResponseBean.success(tasks);
    }

    // 任务分页列表（表格视图：所有状态含已关闭，按创建时间倒序）
    @GetMapping("/api/workflow/tasks/page")
    @ResponseBody
    public ResponseBean getTasksPage(HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "15") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long agentId) {
        String userId = CurrentUser.require(request);
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 15;
        }
        List<WorkflowTask> tasks = taskService.getTasksPage(userId, keyword, status, projectId, agentId, page, size);
        tasks.forEach(this::fillCreatorInfo);
        int total = taskService.countTasks(userId, keyword, status, projectId, agentId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", tasks);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
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
        fillCreatorInfo(task);
        return ResponseBean.success(task);
    }

    // 创建任务
    @PostMapping("/api/workflow/tasks")
    @ResponseBody
    public ResponseBean createTask(HttpServletRequest request, @RequestBody WorkflowTask task) {
        String userId = CurrentUser.require(request);
        task.setUserId(userId);
        WorkflowTask created = taskService.createTask(task);
        fillCreatorInfo(created);
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
            // 删除前查出任务，删除成功后若关联了项目再记录取消关联日志（无权限等删除失败时不留误日志）
            WorkflowTask before = taskService.getTask(id, userId);
            if (before == null) {
                return ResponseBean.fail("任务不存在");
            }
            taskService.deleteTask(id, userId);
            if (before.getProjectId() != null) {
                try {
                    projectController.logTaskUnbind(before.getProjectId(), userId, id, before.getTitle());
                } catch (Exception ex) {
                    logger.warn("记录删除任务取消关联日志失败: taskId={}", id, ex);
                }
            }
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

    // 重做任务（已完成/失败 → 待执行）
    @PutMapping("/api/workflow/tasks/{id}/redo")
    @ResponseBody
    public ResponseBean redoTask(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            taskService.redoTask(id, userId);
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

    /** 填充任务创建者信息：用户创建填充创建人昵称，智能体创建回填创建者智能体名称 */
    private void fillCreatorInfo(WorkflowTask task) {
        if (task == null) {
            return;
        }
        if ("agent".equals(task.getCreatorType())) {
            if (task.getCreatorAgentName() == null && task.getCreatorAgentId() != null) {
                task.setCreatorAgentName("智能体#" + task.getCreatorAgentId());
            }
            return;
        }
        if (task.getUserId() != null) {
            Account account = accountService.getByUserId(task.getUserId());
            if (account != null) {
                task.setCreatorName(account.getNickname() != null ? account.getNickname() : account.getUsername());
            }
        }
    }

    // 任务维度 Token 用量统计
    @GetMapping("/api/workflow/tasks/{id}/token-usage")
    @ResponseBody
    public ResponseBean getTaskTokenUsage(HttpServletRequest request, @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "30") int limit) {
        String userId = CurrentUser.require(request);
        WorkflowTask task = taskService.getTask(id, userId);
        if (task == null) {
            return ResponseBean.fail("任务不存在");
        }
        if (limit < 1 || limit > 200) {
            limit = 30;
        }
        return ResponseBean.success(bizTokenUsageService.getTaskUsage(id, limit));
    }
}
