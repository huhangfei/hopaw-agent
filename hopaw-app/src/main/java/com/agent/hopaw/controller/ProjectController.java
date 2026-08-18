package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.FileUploadItem;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.ProjectLog;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.service.IAccountService;
import com.agent.hopaw.infra.service.IProjectLogService;
import com.agent.hopaw.infra.service.IProjectService;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.*;

@Controller
public class ProjectController {
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    /** 项目状态中文标签（用于日志文案） */
    private static final Map<String, String> STATUS_LABELS = new HashMap<>();
    static {
        STATUS_LABELS.put("planning", "规划中");
        STATUS_LABELS.put("in_progress", "进行中");
        STATUS_LABELS.put("paused", "已暂停");
        STATUS_LABELS.put("completed", "已完成");
        STATUS_LABELS.put("archived", "已归档");
    }

    private final IProjectService projectService;
    private final IAccountService accountService;
    private final IProjectLogService projectLogService;

    public ProjectController(IProjectService projectService,
                             IAccountService accountService,
                             IProjectLogService projectLogService) {
        this.projectService = projectService;
        this.accountService = accountService;
        this.projectLogService = projectLogService;
    }

    // 页面
    @GetMapping("/projects")
    public String index(Model model) {
        return "projects";
    }

    // 分页查询
    @GetMapping("/api/projects/page")
    @ResponseBody
    public ResponseBean getProjectsPage(HttpServletRequest request,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "12") int size) {
        String userId = CurrentUser.require(request);
        List<Project> list = projectService.getProjectsPage(userId, keyword, status, page, size);
        int total = projectService.countProjects(userId, keyword, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ResponseBean.success(result);
    }

    // 查询所有项目(下拉用)
    @GetMapping("/api/projects/all")
    @ResponseBody
    public ResponseBean getAllProjects(HttpServletRequest request) {
        String userId = CurrentUser.require(request);
        return ResponseBean.success(projectService.getAllProjects(userId));
    }

    // 查询单个项目详情
    @GetMapping("/api/projects/{id}")
    @ResponseBody
    public ResponseBean getProject(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        Project project = projectService.getProject(id, userId);
        if (project == null) {
            return ResponseBean.fail("项目不存在或无权访问");
        }
        fillCreatorName(project);
        return ResponseBean.success(project);
    }

    // 查询项目下的任务列表
    @GetMapping("/api/projects/{id}/tasks")
    @ResponseBody
    public ResponseBean getProjectTasks(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        List<WorkflowTask> tasks = projectService.getProjectTasks(id, userId);
        for (WorkflowTask task : tasks) {
            fillCreatorName(task);
        }
        return ResponseBean.success(tasks);
    }

    // 查询项目操作日志
    @GetMapping("/api/projects/{id}/logs")
    @ResponseBody
    public ResponseBean getProjectLogs(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        // 权限校验：确保用户有权访问该项目
        Project project = projectService.getProject(id, userId);
        if (project == null) {
            return ResponseBean.fail("项目不存在或无权访问");
        }
        List<ProjectLog> logs = projectLogService.getLogsByProjectId(id);
        return ResponseBean.success(logs);
    }

    /** 填充项目创建人昵称 */
    private void fillCreatorName(Project project) {
        if (project == null || project.getUserId() == null) return;
        Account account = accountService.getByUserId(project.getUserId());
        if (account != null) {
            project.setCreatorName(account.getNickname() != null ? account.getNickname() : account.getUsername());
        }
    }

    /** 填充任务创建人昵称 */
    private void fillCreatorName(WorkflowTask task) {
        if (task == null || task.getUserId() == null) return;
        Account account = accountService.getByUserId(task.getUserId());
        if (account != null) {
            task.setCreatorName(account.getNickname() != null ? account.getNickname() : account.getUsername());
        }
    }

    // 项目状态流转
    @PutMapping("/api/projects/{id}/status")
    @ResponseBody
    public ResponseBean updateStatus(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        String userId = CurrentUser.require(request);
        String status = body.get("status");
        try {
            Project before = projectService.getProject(id, userId);
            if (before == null) {
                return ResponseBean.fail("项目不存在或无权访问");
            }
            String fromStatus = before.getStatus();
            projectService.updateStatus(id, status, userId);
            // 记录日志：状态流转
            String fromLabel = STATUS_LABELS.getOrDefault(fromStatus, fromStatus);
            String toLabel = STATUS_LABELS.getOrDefault(status, status);
            projectLogService.log(id, userId, "status_change",
                    "项目状态由「" + fromLabel + "」变更为「" + toLabel + "」");
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 创建
    @PostMapping("/api/projects")
    @ResponseBody
    public ResponseBean createProject(HttpServletRequest request, @RequestBody Project project) {
        String userId = CurrentUser.require(request);
        project.setUserId(userId);
        Project created = projectService.createProject(project);
        // 记录日志：创建项目（spaceDir 现为相对路径）
        String spaceInfo = created.getSpaceDir() != null ? "，空间目录：" + created.getSpaceDir() : "";
        projectLogService.log(created.getId(), userId, "create", "创建项目「" + (created.getName() != null ? created.getName() : "") + "」" + spaceInfo);
        return ResponseBean.success(created);
    }

    // 更新
    @PutMapping("/api/projects/{id}")
    @ResponseBody
    public ResponseBean updateProject(HttpServletRequest request, @PathVariable Long id, @RequestBody Project project) {
        String userId = CurrentUser.require(request);
        project.setId(id);
        try {
            // 先查出原项目用于对比变更字段
            Project before = projectService.getProject(id, userId);
            if (before == null) {
                return ResponseBean.fail("项目不存在或无权访问");
            }
            Project updated = projectService.updateProject(project, userId);
            // 记录日志：更新项目信息，列出变化字段
            String detail = buildUpdateDetail(before, updated);
            if (detail != null && !detail.isEmpty()) {
                projectLogService.log(id, userId, "update", detail);
            }
            return ResponseBean.success(updated);
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    /** 构造项目更新日志详情：对比前后字段差异 */
    private String buildUpdateDetail(Project before, Project after) {
        StringBuilder sb = new StringBuilder();
        if (!Objects.equals(before.getName(), after.getName())) {
            sb.append("名称由「").append(before.getName()).append("」改为「").append(after.getName()).append("」；");
        }
        if (!Objects.equals(before.getDescription(), after.getDescription())) {
            sb.append("描述已更新；");
        }
        // 状态变化已由 status_change 单独记录，这里不重复
        if (sb.length() == 0) {
            return null;
        }
        return "更新项目信息：" + sb;
    }

    // 删除
    @DeleteMapping("/api/projects/{id}")
    @ResponseBody
    public ResponseBean deleteProject(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            Project before = projectService.getProject(id, userId);
            if (before == null) {
                return ResponseBean.fail("项目不存在或无权访问");
            }
            // 删除前记录日志（项目删除后日志仍保留作为审计记录）
            projectLogService.log(id, userId, "delete", "删除项目「" + (before.getName() != null ? before.getName() : "") + "」");
            projectService.deleteProject(id, userId);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 项目空间文件树
    @GetMapping("/api/projects/{id}/files")
    @ResponseBody
    public ResponseBean getProjectFiles(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        try {
            return ResponseBean.success(projectService.listProjectFiles(id, userId));
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 项目空间：创建文件/目录
    @PostMapping("/api/projects/{id}/files")
    @ResponseBody
    public ResponseBean createFileEntry(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String userId = CurrentUser.require(request);
        try {
            String path = body.get("path") == null ? "" : body.get("path").toString();
            boolean isDir = Boolean.TRUE.equals(body.get("isDirectory"));
            List<?> tree = projectService.createFileEntry(id, userId, path, isDir);
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            projectLogService.log(id, userId, "file_create",
                    (isDir ? "新建目录「" : "新建文件「") + name + "」");
            return ResponseBean.success(tree);
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 项目空间：删除文件/目录
    @DeleteMapping("/api/projects/{id}/files")
    @ResponseBody
    public ResponseBean deleteFileEntry(HttpServletRequest request, @PathVariable Long id, @RequestParam("path") String path) {
        String userId = CurrentUser.require(request);
        try {
            projectService.deleteFileEntry(id, userId, path);
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            projectLogService.log(id, userId, "file_delete", "删除「" + name + "」");
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 项目空间：移动/重命名
    @PutMapping("/api/projects/{id}/files/move")
    @ResponseBody
    public ResponseBean moveFileEntry(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, String> body) {
        String userId = CurrentUser.require(request);
        try {
            String from = body.get("from");
            String to = body.get("to");
            projectService.moveFileEntry(id, userId, from, to);
            String fromName = from.contains("/") ? from.substring(from.lastIndexOf('/') + 1) : from;
            String toName = to.contains("/") ? to.substring(to.lastIndexOf('/') + 1) : to;
            String detail = fromName.equals(toName)
                    ? "移动「" + fromName + "」到 " + (to.contains("/") ? to.substring(0, to.lastIndexOf('/')) : "/")
                    : "重命名「" + fromName + "」为「" + toName + "」";
            projectLogService.log(id, userId, "file_move", detail);
            return ResponseBean.success();
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 项目空间：批量上传文件
    @PostMapping("/api/projects/{id}/files/upload")
    @ResponseBody
    public ResponseBean uploadProjectFiles(HttpServletRequest request,
                                           @PathVariable Long id,
                                           @RequestParam("files") MultipartFile[] files,
                                           @RequestParam(value = "targetDir", required = false, defaultValue = "") String targetDir) {
        String userId = CurrentUser.require(request);
        try {
            List<FileUploadItem> items = new ArrayList<>();
            int count = 0;
            if (files != null) {
                for (MultipartFile file : files) {
                    if (file == null || file.isEmpty()) {
                        continue;
                    }
                    items.add(new FileUploadItem(file.getOriginalFilename(), file.getInputStream(), file.getSize()));
                    count++;
                }
            }
            List<?> tree = projectService.uploadProjectFiles(id, userId, targetDir, items);
            String dirLabel = (targetDir == null || targetDir.isEmpty()) ? "根目录" : targetDir;
            projectLogService.log(id, userId, "file_upload", "上传 " + count + " 个文件到「" + dirLabel + "」");
            return ResponseBean.success(tree);
        } catch (IOException e) {
            return ResponseBean.fail("读取上传文件失败：" + e.getMessage());
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    /**
     * 供 WorkflowTaskController 在任务关联项目时调用，记录项目日志。
     */
    public void logTaskBind(Long projectId, String userId, Long taskId, String taskTitle) {
        projectLogService.log(projectId, userId, "task_bind",
                "关联任务「" + (taskTitle != null ? taskTitle : "#" + taskId) + "」(#" + taskId + ")");
    }

    public void logTaskUnbind(Long projectId, String userId, Long taskId, String taskTitle) {
        projectLogService.log(projectId, userId, "task_unbind",
                "取消关联任务「" + (taskTitle != null ? taskTitle : "#" + taskId) + "」(#" + taskId + ")");
    }
}
