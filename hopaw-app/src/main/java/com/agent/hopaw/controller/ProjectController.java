package com.agent.hopaw.controller;

import com.agent.hopaw.infra.constant.ProjectStatusEnum;
import com.agent.hopaw.infra.model.dto.FileUploadItem;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.entity.Account;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.ProjectLog;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.agent.hopaw.infra.model.entity.ChatSession;
import com.agent.hopaw.infra.service.IAccountService;
import com.agent.hopaw.infra.service.IChatSessionService;
import com.agent.hopaw.infra.service.IBizTokenUsageService;
import com.agent.hopaw.infra.service.IProjectIterateService;
import com.agent.hopaw.infra.service.IProjectLogService;
import com.agent.hopaw.infra.service.IProjectService;
import com.agent.hopaw.infra.service.IWorkflowTaskService;
import com.agent.hopaw.util.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Controller
public class ProjectController {
    private static final Logger logger = LoggerFactory.getLogger(ProjectController.class);

    private final IProjectService projectService;
    private final IAccountService accountService;
    private final IProjectLogService projectLogService;
    private final IBizTokenUsageService bizTokenUsageService;
    private final IWorkflowTaskService workflowTaskService;
    private final IChatSessionService chatSessionService;
    private final IProjectIterateService projectIterateService;

    public ProjectController(IProjectService projectService,
                             IAccountService accountService,
                             IProjectLogService projectLogService,
                             IBizTokenUsageService bizTokenUsageService,
                             IWorkflowTaskService workflowTaskService,
                             IChatSessionService chatSessionService,
                             IProjectIterateService projectIterateService) {
        this.projectService = projectService;
        this.accountService = accountService;
        this.projectLogService = projectLogService;
        this.bizTokenUsageService = bizTokenUsageService;
        this.workflowTaskService = workflowTaskService;
        this.chatSessionService = chatSessionService;
        this.projectIterateService = projectIterateService;
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

    // 删除项目操作日志
    @DeleteMapping("/api/projects/{id}/logs/{logId}")
    @ResponseBody
    public ResponseBean deleteProjectLog(HttpServletRequest request, @PathVariable Long id, @PathVariable Long logId) {
        String userId = CurrentUser.require(request);
        Project project = projectService.getProject(id, userId);
        if (project == null) {
            return ResponseBean.fail("项目不存在或无权访问");
        }
        boolean ok = projectLogService.deleteLog(logId);
        return ok ? ResponseBean.success("删除成功") : ResponseBean.fail("日志不存在或已删除");
    }

    // 更新项目操作日志类型
    @PutMapping("/api/projects/{id}/logs/{logId}/type")
    @ResponseBody
    public ResponseBean updateProjectLogType(HttpServletRequest request, @PathVariable Long id,
                                             @PathVariable Long logId, @RequestBody java.util.Map<String, String> body) {
        String userId = CurrentUser.require(request);
        Project project = projectService.getProject(id, userId);
        if (project == null) {
            return ResponseBean.fail("项目不存在或无权访问");
        }
        String logType = body == null ? null : body.get("logType");
        boolean ok = projectLogService.updateLogType(logId, logType);
        return ok ? ResponseBean.success("更新成功") : ResponseBean.fail("日志不存在");
    }

    // 项目维度 Token 用量统计
    @GetMapping("/api/projects/{id}/token-usage")
    @ResponseBody
    public ResponseBean getProjectTokenUsage(HttpServletRequest request, @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "30") int limit) {
        String userId = CurrentUser.require(request);
        Project project = projectService.getProject(id, userId);
        if (project == null) {
            return ResponseBean.fail("项目不存在或无权访问");
        }
        if (limit < 1 || limit > 200) {
            limit = 30;
        }
        return ResponseBean.success(bizTokenUsageService.getProjectUsage(id, limit));
    }

    // 项目关联的所有会话ID（供前端匹配 WebSocket 消息）
    @GetMapping("/api/projects/{id}/session-ids")
    @ResponseBody
    public ResponseBean getProjectSessionIds(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        Project project = projectService.getProject(id, userId);
        if (project == null) {
            return ResponseBean.fail("项目不存在或无权访问");
        }
        return ResponseBean.success(workflowTaskService.getSessionIdsByProjectId(id));
    }

    // 项目管理智能体会话信息（标题等，供详情页展示）
    @GetMapping("/api/projects/{id}/session")
    @ResponseBody
    public ResponseBean getProjectSession(HttpServletRequest request, @PathVariable Long id) {
        String userId = CurrentUser.require(request);
        Project project = projectService.getProject(id, userId);
        if (project == null) {
            return ResponseBean.fail("项目不存在或无权访问");
        }
        ChatSession session = null;
        if (project.getSessionId() != null && !project.getSessionId().isEmpty()) {
            session = chatSessionService.getSessionBySessionId(project.getSessionId());
        }
        return ResponseBean.success(session);
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

    // 更新项目自动迭代配置（开启/关闭 + 迭代要求提示词）
    @PutMapping("/api/projects/{id}/iterate-config")
    @ResponseBody
    public ResponseBean updateIterateConfig(HttpServletRequest request, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String userId = CurrentUser.require(request);
        Boolean autoIterate = body.get("autoIterate") != null ? Boolean.valueOf(String.valueOf(body.get("autoIterate"))) : null;
        String iteratePrompt = body.get("iteratePrompt") != null ? String.valueOf(body.get("iteratePrompt")) : null;
        try {
            Project before = projectService.getProject(id, userId);
            if (before == null) {
                return ResponseBean.fail("项目不存在或无权访问");
            }
            Project updated = projectService.updateIterateConfig(id, autoIterate, iteratePrompt, userId);
            // 记录日志：迭代开关变更 / 提示词更新
            if (autoIterate != null && !autoIterate.equals(before.getAutoIterate())) {
                projectLogService.log(id, userId, "auto_iterate",
                        autoIterate ? "项目智能伙伴自动迭代已开启" : "项目智能伙伴自动迭代已关闭");
            }
            if (iteratePrompt != null && !iteratePrompt.equals(before.getIteratePrompt() == null ? "" : before.getIteratePrompt())) {
                projectLogService.log(id, userId, "auto_iterate", "项目智能伙伴迭代要求提示词已更新");
            }
            return ResponseBean.success(updated);
        } catch (Exception e) {
            return ResponseBean.fail(e.getMessage());
        }
    }

    // 手动下发指令执行一轮项目迭代（同步执行，返回执行结果与失败原因）
    @PostMapping("/api/projects/{id}/iterate")
    @ResponseBody
    public ResponseBean executeIterate(HttpServletRequest request, @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        String userId = CurrentUser.require(request);
        try {
            Project project = projectService.getProject(id, userId);
            if (project == null) {
                return ResponseBean.fail("项目不存在或无权访问");
            }
            String userMessage = (body != null && body.get("userMessage") != null && !body.get("userMessage").trim().isEmpty())
                    ? body.get("userMessage").trim() : null;
            // 手动下发指令：不校验自动迭代开关，仅由服务层做执行器并发检查
            return ResponseBean.success(projectIterateService.executeProjectIterateManual(id, userMessage));
        } catch (Exception e) {
            logger.error("手动执行项目迭代失败: projectId={}", id, e);
            return ResponseBean.fail(e.getMessage() != null ? e.getMessage() : "执行失败（未知异常）");
        }
    }

    // 修改项目空间目录（支持相对路径与绝对路径，相对路径以服务运行目录为起点）
    @PutMapping("/api/projects/{id}/space-dir")
    @ResponseBody
    public ResponseBean updateSpaceDir(HttpServletRequest request, @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String userId = CurrentUser.require(request);
        String newSpaceDir = body != null ? body.get("spaceDir") : null;
        try {
            Project before = projectService.getProject(id, userId);
            if (before == null) {
                return ResponseBean.fail("项目不存在或无权访问");
            }
            Project updated = projectService.updateProjectSpaceDir(id, newSpaceDir, userId);
            // 记录日志：空间目录变更
            projectLogService.log(id, userId, "update",
                    "空间目录由「" + (before.getSpaceDir() != null ? before.getSpaceDir() : "未设置") + "」改为「" + updated.getSpaceDir() + "」");
            return ResponseBean.success(updated);
        } catch (Exception e) {
            logger.error("修改项目空间目录失败: projectId={}", id, e);
            return ResponseBean.fail(e.getMessage() != null ? e.getMessage() : "修改失败（未知异常）");
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
            String fromLabel = resolveStatusLabel(fromStatus);
            String toLabel = resolveStatusLabel(status);
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

    /* ==================== 项目空间下载 ==================== */

    /**
     * 下载项目空间内的文件或目录。
     * - path 为空：打包整个项目空间为 zip 下载
     * - path 指向文件：直接流式下载
     * - path 指向目录：打包该目录为 zip 下载
     */
    @GetMapping("/api/projects/{id}/files/download")
    public void downloadProjectFile(HttpServletRequest request,
                                    HttpServletResponse response,
                                    @PathVariable Long id,
                                    @RequestParam(value = "path", required = false, defaultValue = "") String path) {
        String userId = CurrentUser.require(request);
        try {
            Path resolved = projectService.resolveDownloadPath(id, userId, path);
            if (Files.isDirectory(resolved)) {
                // 目录或整空间：打包 zip 下载
                java.io.File zipFile = projectService.createDownloadZip(id, userId, path);
                try {
                    String downloadName = buildDownloadName(path, true);
                    response.setContentType("application/zip");
                    response.setHeader("Content-Disposition", buildContentDisposition(downloadName));
                    response.setContentLengthLong(zipFile.length());
                    try (InputStream in = Files.newInputStream(zipFile.toPath());
                         OutputStream out = response.getOutputStream()) {
                        in.transferTo(out);
                    }
                } finally {
                    // 下载完成或异常后立即清理临时文件
                    if (!zipFile.delete()) {
                        logger.warn("删除临时zip失败: {}", zipFile.getAbsolutePath());
                    }
                }
            } else {
                // 单文件：直接流式下载，不产生临时文件
                String fileName = resolved.getFileName().toString();
                response.setContentType(Files.probeContentType(resolved));
                if (response.getContentType() == null) {
                    response.setContentType("application/octet-stream");
                }
                response.setHeader("Content-Disposition", buildContentDisposition(fileName));
                response.setContentLengthLong(Files.size(resolved));
                try (InputStream in = Files.newInputStream(resolved);
                     OutputStream out = response.getOutputStream()) {
                    in.transferTo(out);
                }
            }
        } catch (Exception e) {
            logger.error("下载项目[{}]文件失败: path={}", id, path, e);
            try {
                response.reset();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"msg\":\"下载失败：" +
                        e.getMessage().replace("\"", "\\\"") + "\"}");
            } catch (IOException ignored) {
            }
        }
    }

    /** 构造下载文件名：目录取末段名加 .zip，整空间取项目名加 .zip */
    private String buildDownloadName(String path, boolean isZip) {
        if (path == null || path.trim().isEmpty()) {
            return "project-space" + (isZip ? ".zip" : "");
        }
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        return isZip ? name + ".zip" : name;
    }

    /** 构造 Content-Disposition 头，使用 RFC 5987 编码以支持中文文件名 */
    private String buildContentDisposition(String fileName) throws IOException {
        String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        return "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded;
    }

    /**
     * 项目空间文件内联预览：以 inline 方式流式输出文件内容，供 iframe 预览组件加载。
     * 仅支持单文件预览（不支持目录）。
     */
    @GetMapping("/api/projects/{id}/files/preview")
    public void previewProjectFile(HttpServletRequest request,
                                   HttpServletResponse response,
                                   @PathVariable Long id,
                                   @RequestParam("path") String path) {
        String userId = CurrentUser.require(request);
        try {
            Path resolved = projectService.resolveDownloadPath(id, userId, path);
            if (Files.isDirectory(resolved)) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":400,\"msg\":\"不支持预览目录\"}");
                return;
            }
            String fileName = resolved.getFileName().toString();
            String contentType = Files.probeContentType(resolved);
            response.setContentType(contentType != null ? contentType : "application/octet-stream");
            response.setHeader("Content-Disposition", "inline");
            response.setContentLengthLong(Files.size(resolved));
            try (InputStream in = Files.newInputStream(resolved);
                 OutputStream out = response.getOutputStream()) {
                in.transferTo(out);
            }
        } catch (Exception e) {
            logger.error("预览项目[{}]文件失败: path={}", id, path, e);
            try {
                response.reset();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":500,\"msg\":\"预览失败：" +
                        e.getMessage().replace("\"", "\\\"") + "\"}");
            } catch (IOException ignored) {
            }
        }
    }

    /** 解析项目状态中文标签（用于日志文案），未知状态原样返回 */
    private String resolveStatusLabel(String status) {
        ProjectStatusEnum statusEnum = ProjectStatusEnum.fromCode(status);
        return statusEnum != null ? statusEnum.getDescription() : status;
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
