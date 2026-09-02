package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.NotifyEventEnum;
import com.agent.hopaw.infra.constant.ProjectStatusEnum;
import com.agent.hopaw.infra.enums.GlobalNoticeTypeEnum;
import com.agent.hopaw.infra.mapper.ProjectMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.model.dto.FileTreeNode;
import com.agent.hopaw.infra.model.dto.FileUploadItem;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import com.alibaba.fastjson2.JSON;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
public class ProjectService implements IProjectService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectMapper projectMapper;
    private final WorkflowTaskMapper workflowTaskMapper;
    private final IGlobalNoticeService globalNoticeService;
    private final INotificationService notificationService;

    /** 项目空间根目录（所有项目的工作空间目录都放置在此目录下） */
    private final String projectSpaceRoot;

    /** 项目空间目录原始配置值（可能为相对路径，用于生成存储用的相对路径） */
    private final String projectSpaceDirConfig;

    /** 下载用临时目录（存放打包产生的 zip 文件，由定时任务清理） */
    private final String tempDownloadsDir;

    /** 临时文件最大保留时长：1 小时（超过则清理） */
    private static final long TEMP_MAX_AGE_MS = 60 * 60 * 1000L;

    public ProjectService(ProjectMapper projectMapper,
                          WorkflowTaskMapper workflowTaskMapper,
                          @Value("${hopaw.project.space.dir:./project-spaces}") String projectSpaceDir,
                          IGlobalNoticeService globalNoticeService,
                          INotificationService notificationService) {
        this.projectMapper = projectMapper;
        this.workflowTaskMapper = workflowTaskMapper;
        this.globalNoticeService = globalNoticeService;
        this.notificationService = notificationService;
        // 保存原始配置值（自动创建项目空间时按此生成存储用的相对路径）
        this.projectSpaceDirConfig = projectSpaceDir;
        // 解析为绝对路径，确保后续文件操作一致
        File rootDir = new File(projectSpaceDir);
        if (!rootDir.isAbsolute()) {
            rootDir = new File(System.getProperty("user.dir"), projectSpaceDir);
        }
        this.projectSpaceRoot = rootDir.getAbsolutePath();
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        // 初始化下载临时目录：{java.io.tmpdir}/hopaw-downloads
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "hopaw-downloads");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        this.tempDownloadsDir = tempDir.getAbsolutePath();
        logger.info("项目空间根目录: {}", this.projectSpaceRoot);
        logger.info("下载临时目录: {}", this.tempDownloadsDir);
    }

    @Override
    public List<Project> findAutoIterateProjects() {
        List<Project> list = projectMapper.findAutoIterateProjects();
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public void updateSessionId(Long projectId, String sessionId) {
        projectMapper.updateSessionId(projectId, sessionId);
    }

    @Override
    public Project getProjectBySessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        return projectMapper.findBySessionId(sessionId.trim());
    }

    @Override
    public Project createProject(Project project) {
        project.setStatus(ProjectStatusEnum.PLANNING.getCode());
        LocalDateTime now = LocalDateTime.now();
        project.setCreateTime(now);
        project.setUpdateTime(now);
        applyNotifyListsToJson(project);
        projectMapper.insert(project);
        // 项目创建：发送外部通知（按项目本次提交的通知渠道/事项配置）
        try {
            notificationService.sendForProject(project.getId(), NotifyEventEnum.PROJECT_CREATED.getCode(),
                    NotifyEventEnum.PROJECT_CREATED.getDescription(), "项目已创建");
        } catch (Exception e) {
            logger.warn("项目创建外部通知发送失败: projectId={}", project.getId(), e);
        }
        // 项目空间：若前端指定了本地目录则直接使用，否则按项目编号自动创建
        String customSpaceDir = project.getSpaceDir();
        String spaceDir;
        if (customSpaceDir != null && !customSpaceDir.trim().isEmpty()) {
            spaceDir = useLocalSpaceDir(project.getId(), customSpaceDir.trim());
        } else {
            spaceDir = createProjectSpace(project.getId());
        }
        project.setSpaceDir(spaceDir);
        return project;
    }

    /**
     * 使用本地目录作为项目空间。
     * 存储绝对路径；若目录不存在则创建。
     * @return 规范化后的绝对路径字符串
     */
    private String useLocalSpaceDir(Long projectId, String localDir) {
        if (projectId == null) {
            return null;
        }
        try {
            Path path = Paths.get(localDir).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            if (!Files.isDirectory(path)) {
                throw new RuntimeException("指定路径不是目录: " + localDir);
            }
            String absPath = path.toString();
            projectMapper.updateSpaceDir(projectId, absPath);
            logger.info("项目[{}]使用本地目录作为空间: {}", projectId, absPath);
            return absPath;
        } catch (Exception e) {
            logger.error("项目[{}]设置本地空间目录失败: {}", projectId, localDir, e);
            throw new RuntimeException("设置本地空间目录失败: " + e.getMessage());
        }
    }

    /**
     * 根据项目编号创建项目空间目录：{projectSpaceRoot}/{projectId}
     * 存储相对路径（相对服务运行目录），便于跨环境迁移
     * @return 项目空间相对路径
     */
    private String createProjectSpace(Long projectId) {
        if (projectId == null) {
            return null;
        }
        try {
            // 以运行目录为基准的相对路径：{配置的项目空间目录}/{projectId}
            String relativeDir = new File(projectSpaceDirConfig).toPath().resolve(String.valueOf(projectId))
                    .normalize().toString().replace('\\', '/');
            // 确保实际目录存在（按绝对路径创建）
            Path spacePath = Paths.get(projectSpaceRoot, String.valueOf(projectId)).toAbsolutePath().normalize();
            Files.createDirectories(spacePath);
            projectMapper.updateSpaceDir(projectId, relativeDir);
            logger.info("已创建项目[{}]空间目录: {}（相对路径: {}）", projectId, spacePath, relativeDir);
            return relativeDir;
        } catch (Exception e) {
            logger.error("创建项目[{}]空间目录失败", projectId, e);
            throw new RuntimeException("创建项目空间目录失败: " + e.getMessage());
        }
    }

    /**
     * 将存储的项目空间路径解析为绝对路径。
     * 支持相对路径（相对服务运行目录 user.dir）；兼容旧数据（可能存储的是绝对路径）。
     */
    private Path resolveSpaceDirAbsolutePath(String storedSpaceDir) {
        if (storedSpaceDir == null || storedSpaceDir.isEmpty()) {
            return null;
        }
        Path p = Paths.get(storedSpaceDir);
        if (p.isAbsolute()) {
            // 兼容旧数据：存储的是绝对路径，直接使用
            return p.toAbsolutePath().normalize();
        }
        // 相对路径：以服务运行目录为起点解析
        return new File(System.getProperty("user.dir")).toPath().resolve(p).toAbsolutePath().normalize();
    }

    @Override
    public Project updateProjectSpaceDir(Long id, String newSpaceDir, String userId) {
        Project existing = projectMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该项目");
        }
        if (newSpaceDir == null || newSpaceDir.trim().isEmpty()) {
            throw new RuntimeException("空间目录不能为空");
        }
        String dir = newSpaceDir.trim();
        try {
            // 解析为新目录的绝对路径（相对路径以运行目录为起点），不存在则创建
            Path newPath = resolveSpaceDirAbsolutePath(dir);
            if (newPath == null) {
                throw new RuntimeException("空间目录地址无效");
            }
            Files.createDirectories(newPath);
            if (!Files.isDirectory(newPath)) {
                throw new RuntimeException("指定路径不是目录: " + dir);
            }
            // 存储原样地址（相对路径保持相对、绝对路径保持绝对），便于展示与迁移
            projectMapper.updateSpaceDir(id, dir);
            existing.setSpaceDir(dir);
            existing.setUpdateTime(LocalDateTime.now());
            projectMapper.update(existing);
            fillSpaceDirAbs(existing);
            logger.info("项目[{}]空间目录已修改为: {}", id, dir);
            return existing;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            logger.error("项目[{}]修改空间目录失败: {}", id, dir, e);
            throw new RuntimeException("修改空间目录失败: " + e.getMessage());
        }
    }

    @Override
    public Project updateProject(Project project, String userId) {
        Project existing = projectMapper.findById(project.getId());
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        // userId为空时不做归属校验（智能体工具场景，项目跨用户共享协作）
        if (userId != null && !userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该项目");
        }
        existing.setName(project.getName());
        existing.setDescription(project.getDescription());
        // 项目管理智能体与自动迭代设置随编辑更新（允许清空）
        existing.setAgentId(project.getAgentId());
        existing.setAutoIterate(project.getAutoIterate());
        // 自动迭代要求提示词随编辑更新（允许清空）
        existing.setIteratePrompt(project.getIteratePrompt());
        // 状态变更走 updateStatus 接口，这里仅当传入了合法状态且与当前不同时校验流转
        if (project.getStatus() != null && !project.getStatus().equals(existing.getStatus())) {
            validateTransition(existing.getStatus(), project.getStatus());
            existing.setStatus(project.getStatus());
        }
        // 通知渠道与通知事项随编辑更新（列表为 null 时保留原值，空列表表示清空）
        if (project.getNotifyChannelIds() != null) {
            existing.setNotifyChannelIds(project.getNotifyChannelIds());
        }
        if (project.getNotifyEventCodes() != null) {
            existing.setNotifyEventCodes(project.getNotifyEventCodes());
        }
        applyNotifyListsToJson(existing);
        existing.setUpdateTime(LocalDateTime.now());
        projectMapper.update(existing);
        return existing;
    }

    @Override
    public Project updateIterateConfig(Long id, Boolean autoIterate, String iteratePrompt, String userId) {
        Project existing = projectMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该项目");
        }
        if (autoIterate == null && iteratePrompt == null) {
            return existing; // 无变更
        }
        // 开启自动迭代前校验：必须已配置项目管理智能体
        if (Boolean.TRUE.equals(autoIterate) && existing.getAgentId() == null) {
            throw new RuntimeException("请先配置项目管理智能体再开启自动迭代");
        }
        if (autoIterate != null) {
            existing.setAutoIterate(autoIterate);
        }
        if (iteratePrompt != null) {
            existing.setIteratePrompt(iteratePrompt);
        }
        existing.setUpdateTime(LocalDateTime.now());
        projectMapper.update(existing);
        return existing;
    }

    @Override
    public void updateStatus(Long id, String status, String userId) {
        if (ProjectStatusEnum.fromCode(status) == null) {
            throw new RuntimeException("非法的项目状态: " + status);
        }
        Project existing = projectMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该项目");
        }
        if (status.equals(existing.getStatus())) {
            return; // 状态未变
        }
        validateTransition(existing.getStatus(), status);
        projectMapper.updateStatus(id, status);
        // 状态变更：推送全局通知（子类型 status_change）
        try {
            java.util.Map<String, Object> content = new java.util.HashMap<>();
            content.put("projectId", id);
            content.put("name", existing.getName());
            content.put("oldStatus", existing.getStatus());
            content.put("newStatus", status);
            globalNoticeService.notify(userId, GlobalNoticeTypeEnum.PROJECT, "status_change", content);
        } catch (Exception e) {
            logger.warn("项目状态变更通知推送失败: projectId={}", id, e);
        }
        // 状态变更：发送外部通知（钉钉群/邮件/飞书/Webhook，按项目通知配置）
        try {
            ProjectStatusEnum newEnum = ProjectStatusEnum.fromCode(status);
            String newLabel = newEnum != null ? newEnum.getDescription() : status;
            ProjectStatusEnum oldEnum = ProjectStatusEnum.fromCode(existing.getStatus());
            String oldLabel = oldEnum != null ? oldEnum.getDescription() : existing.getStatus();
            notificationService.sendForProject(id, NotifyEventEnum.PROJECT_STATUS_CHANGED.getCode(),
                    NotifyEventEnum.PROJECT_STATUS_CHANGED.getDescription(),
                    "项目状态由「" + oldLabel + "」变更为「" + newLabel + "」");
        } catch (Exception e) {
            logger.warn("项目状态变更外部通知发送失败: projectId={}", id, e);
        }
    }

    /** 校验状态流转是否合法（规则见 ProjectStatusEnum） */
    private void validateTransition(String from, String to) {
        ProjectStatusEnum fromEnum = ProjectStatusEnum.fromCode(from);
        ProjectStatusEnum toEnum = ProjectStatusEnum.fromCode(to);
        if (fromEnum == null || toEnum == null || !fromEnum.canTransitionTo(toEnum)) {
            throw new RuntimeException("不允许从状态[" + from + "]流转到[" + to + "]");
        }
    }

    @Override
    @Transactional
    public void deleteProject(Long id, String userId) {
        Project existing = projectMapper.findById(id);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        // userId为空时不做归属校验（智能体工具场景）
        if (userId != null && !userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该项目");
        }
        // 项目删除：发送外部通知。须在 deleteById 前触发——通知服务同步读取项目配置快照，
        // 删除后读不到；@Transactional 内同连接读取可见（读己之提交/未提交），快照数据正确。
        try {
            notificationService.sendForProject(id, NotifyEventEnum.PROJECT_DELETED.getCode(),
                    NotifyEventEnum.PROJECT_DELETED.getDescription(), "项目已删除");
        } catch (Exception e) {
            logger.warn("项目删除外部通知发送失败: projectId={}", id, e);
        }
        projectMapper.deleteById(id);
    }

    @Override
    public Project getProject(Long id, String userId) {
        Project project = projectMapper.findById(id);
        if (project == null) {
            return null;
        }
        // userId为空时不做归属校验（智能体工具场景）
        if (userId != null && !userId.equals(project.getUserId())) {
            return null;
        }
        fillSpaceDirAbs(project);
        fillNotifyLists(project);
        return project;
    }

    /**
     * 填充展示用的空间目录绝对路径（存储值不变：相对路径按运行目录解析，绝对路径原样）。
     */
    private void fillSpaceDirAbs(Project project) {
        if (project == null) return;
        Path abs = resolveSpaceDirAbsolutePath(project.getSpaceDir());
        project.setSpaceDirAbs(abs != null ? abs.toString() : null);
    }

    /**
     * 接口出参转换：将持久化的通知渠道/事项 JSON 字符串解析为列表字段（解析失败忽略，保持空）。
     */
    private void fillNotifyLists(Project project) {
        if (project == null) return;
        try {
            if (project.getNotifyChannels() != null && !project.getNotifyChannels().isBlank()) {
                project.setNotifyChannelIds(JSON.parseArray(project.getNotifyChannels(), Long.class));
            }
        } catch (Exception e) {
            logger.warn("解析项目通知渠道配置失败: projectId={}", project.getId());
        }
        try {
            if (project.getNotifyEvents() != null && !project.getNotifyEvents().isBlank()) {
                project.setNotifyEventCodes(JSON.parseArray(project.getNotifyEvents(), String.class));
            }
        } catch (Exception e) {
            logger.warn("解析项目通知事项配置失败: projectId={}", project.getId());
        }
    }

    /**
     * 接口入参转换：将列表字段序列化为 JSON 字符串持久化（列表为 null 时保留原值，空列表清空存储）。
     */
    private void applyNotifyListsToJson(Project project) {
        if (project.getNotifyChannelIds() != null) {
            project.setNotifyChannels(project.getNotifyChannelIds().isEmpty() ? null : JSON.toJSONString(project.getNotifyChannelIds()));
        }
        if (project.getNotifyEventCodes() != null) {
            project.setNotifyEvents(project.getNotifyEventCodes().isEmpty() ? null : JSON.toJSONString(project.getNotifyEventCodes()));
        }
    }

    @Override
    public List<Project> getProjectsPage(String userId, String keyword, String status, int page, int size) {
        int offset = (page - 1) * size;
        return projectMapper.findByUserIdWithFilters(userId, keyword, status, offset, size);
    }

    @Override
    public int countProjects(String userId, String keyword, String status) {
        return projectMapper.countByUserIdWithFilters(userId, keyword, status);
    }

    @Override
    public List<Project> getAllProjects(String userId) {
        return projectMapper.findByUserId(userId);
    }

    @Override
    public List<WorkflowTask> getProjectTasks(Long projectId, String userId) {
        Project existing = projectMapper.findById(projectId);
        if (existing == null || !userId.equals(existing.getUserId())) {
            return new ArrayList<>();
        }
        List<WorkflowTask> list = workflowTaskMapper.findByProjectId(projectId);
        return list != null ? list : new ArrayList<>();
    }

    @Override
    public List<FileTreeNode> listProjectFiles(Long projectId, String userId) {
        Project project = projectMapper.findById(projectId);
        if (project == null || !userId.equals(project.getUserId())) {
            throw new RuntimeException("项目不存在或无权访问");
        }
        Path rootPath = resolveProjectSpaceRoot(project, projectId);
        if (rootPath == null || !Files.exists(rootPath)) {
            return new ArrayList<>();
        }
        List<FileTreeNode> tree = new ArrayList<>();
        buildFileTree(rootPath, rootPath, tree);
        return tree;
    }

    /**
     * 解析项目空间的绝对路径根目录：兼容空值补建、旧绝对路径数据。
     */
    private Path resolveProjectSpaceRoot(Project project, Long projectId) {
        String spaceDir = project.getSpaceDir();
        if (spaceDir == null || spaceDir.isEmpty()) {
            // 兼容旧数据：若未记录空间目录则按规则补建
            String relativeDir = createProjectSpace(projectId);
            return resolveSpaceDirAbsolutePath(relativeDir);
        }
        return resolveSpaceDirAbsolutePath(spaceDir);
    }

    @Override
    public String getProjectSpaceAbsolutePath(Long projectId, String userId) {
        return getProjectSpaceRoot(projectId, userId).toAbsolutePath().toString();
    }

    /**
     * 递归构建文件树（仅遍历项目空间目录内的文件，防止路径穿越）
     * @param current 当前遍历的目录
     * @param root 项目空间根目录（用于计算相对路径）
     * @param nodes 当前层级的节点列表
     */
    private void buildFileTree(Path current, Path root, List<FileTreeNode> nodes) {
        File[] files = current.toFile().listFiles();
        if (files == null) {
            return;
        }
        // 排序：目录在前，文件在后，同类型按名称排序
        List<File> sorted = new ArrayList<>(Arrays.asList(files));
        sorted.sort(Comparator
                .comparing((File f) -> f.isDirectory() ? 0 : 1)
                .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

        for (File f : sorted) {
            FileTreeNode node = new FileTreeNode();
            node.setName(f.getName());
            // 相对项目空间根目录的路径
            String relative = root.relativize(f.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
            node.setPath(relative);
            if (f.isDirectory()) {
                node.setType("directory");
                node.setSize(0L);
                List<FileTreeNode> children = new ArrayList<>();
                buildFileTree(f.toPath(), root, children);
                node.setChildren(children);
            } else {
                node.setType("file");
                node.setSize(f.length());
                node.setLastModified(f.lastModified());
            }
            nodes.add(node);
        }
    }

    /* ==================== 项目空间文件管理 ==================== */

    /**
     * 获取项目空间根目录 Path（校验权限，必要时补建）
     */
    private Path getProjectSpaceRoot(Long projectId, String userId) {
        Project project = projectMapper.findById(projectId);
        if (project == null || !userId.equals(project.getUserId())) {
            throw new RuntimeException("项目不存在或无权访问");
        }
        Path rootPath = resolveProjectSpaceRoot(project, projectId);
        if (rootPath == null) {
            throw new RuntimeException("项目空间目录未配置");
        }
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                throw new RuntimeException("项目空间目录不存在且无法创建", e);
            }
        }
        return rootPath;
    }

    /**
     * 解析相对路径为绝对路径，并校验未越出项目空间根目录（防止路径穿越）
     */
    private Path resolveSafePath(Path root, String relativePath) {
        if (relativePath == null) {
            throw new RuntimeException("路径不能为空");
        }
        // 统一使用 / 分隔，去除首尾空白与多余的 /
        String cleaned = relativePath.trim().replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            return root;
        }
        // 拒绝包含 .. 的相对路径片段
        String[] segments = cleaned.split("/");
        for (String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg) || "..".equals(seg)) {
                throw new RuntimeException("非法路径：" + relativePath);
            }
        }
        Path resolved = root.resolve(cleaned).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new RuntimeException("非法路径访问：" + relativePath);
        }
        return resolved;
    }

    /**
     * 校验单个文件/目录名合法
     */
    private void validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new RuntimeException("名称不能为空");
        }
        String n = name.trim();
        if (".".equals(n) || "..".equals(n)) {
            throw new RuntimeException("非法名称：" + name);
        }
        // 禁止包含路径分隔符与非法字符
        if (n.contains("/") || n.contains("\\") || n.contains(":") ||
                n.contains("*") || n.contains("?") || n.contains("\"") ||
                n.contains("<") || n.contains(">") || n.contains("|")) {
            throw new RuntimeException("名称包含非法字符：" + name);
        }
    }

    /**
     * 从原始文件名中提取纯文件名（防止客户端传入带路径的名称）
     */
    private String sanitizeFileName(String original) {
        if (original == null || original.isEmpty()) {
            return "unnamed";
        }
        String name = original.replace('\\', '/');
        int idx = name.lastIndexOf('/');
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        if (name.isEmpty()) {
            return "unnamed";
        }
        return name;
    }

    @Override
    public List<FileTreeNode> createFileEntry(Long projectId, String userId, String relativePath, boolean isDirectory) {
        Path root = getProjectSpaceRoot(projectId, userId);
        // 校验最后一段名称合法
        String[] segments = relativePath.trim().replace('\\', '/').split("/");
        validateName(segments[segments.length - 1]);
        Path target = resolveSafePath(root, relativePath);
        try {
            if (isDirectory) {
                Files.createDirectories(target);
            } else {
                Files.createDirectories(target.getParent());
                Files.createFile(target);
            }
        } catch (FileAlreadyExistsException e) {
            throw new RuntimeException("已存在同名文件或目录：" + relativePath);
        } catch (IOException e) {
            throw new RuntimeException("创建失败：" + e.getMessage(), e);
        }
        return listProjectFiles(projectId, userId);
    }

    @Override
    public void deleteFileEntry(Long projectId, String userId, String relativePath) {
        Path root = getProjectSpaceRoot(projectId, userId);
        Path target = resolveSafePath(root, relativePath);
        if (target.equals(root)) {
            throw new RuntimeException("不能删除项目空间根目录");
        }
        if (!Files.exists(target)) {
            throw new RuntimeException("文件或目录不存在：" + relativePath);
        }
        try {
            if (Files.isDirectory(target)) {
                deleteDirectoryRecursively(target);
            } else {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            throw new RuntimeException("删除失败：" + e.getMessage(), e);
        }
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException("删除目录失败：" + p, e);
                    }
                });
    }

    @Override
    public void moveFileEntry(Long projectId, String userId, String fromPath, String toPath) {
        Path root = getProjectSpaceRoot(projectId, userId);
        Path from = resolveSafePath(root, fromPath);
        Path to = resolveSafePath(root, toPath);
        if (from.equals(root)) {
            throw new RuntimeException("不能移动项目空间根目录");
        }
        if (!Files.exists(from)) {
            throw new RuntimeException("源文件或目录不存在：" + fromPath);
        }
        if (Files.exists(to)) {
            throw new RuntimeException("目标已存在：" + toPath);
        }
        // 校验目标名称合法
        String[] toSegments = toPath.trim().replace('\\', '/').split("/");
        validateName(toSegments[toSegments.length - 1]);
        try {
            Files.createDirectories(to.getParent());
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("移动失败：" + e.getMessage(), e);
        }
    }

    @Override
    public List<FileTreeNode> uploadProjectFiles(Long projectId, String userId, String targetDir, List<FileUploadItem> items) {
        Path root = getProjectSpaceRoot(projectId, userId);
        Path destDir = resolveSafePath(root, targetDir == null ? "" : targetDir);
        if (!Files.exists(destDir)) {
            throw new RuntimeException("目标目录不存在：" + targetDir);
        }
        if (!Files.isDirectory(destDir)) {
            throw new RuntimeException("目标路径不是目录：" + targetDir);
        }
        if (items == null || items.isEmpty()) {
            throw new RuntimeException("请选择要上传的文件");
        }
        try {
            for (FileUploadItem item : items) {
                if (item == null || item.getInputStream() == null) {
                    continue;
                }
                String fileName = sanitizeFileName(item.getFileName());
                validateName(fileName);
                Path dest = destDir.resolve(fileName).toAbsolutePath().normalize();
                if (!dest.startsWith(root)) {
                    throw new RuntimeException("非法上传路径：" + fileName);
                }
                // 同名文件自动重命名为 name(1).ext
                dest = ensureUniquePath(dest);
                Files.createDirectories(dest.getParent());
                try (InputStream in = item.getInputStream()) {
                    Files.copy(in, dest);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("上传失败：" + e.getMessage(), e);
        }
        return listProjectFiles(projectId, userId);
    }

    /**
     * 若目标路径已存在，则追加 (1)、(2) ... 直到不冲突
     */
    private Path ensureUniquePath(Path path) {
        if (!Files.exists(path)) {
            return path;
        }
        String fileName = path.getFileName().toString();
        String baseName = fileName;
        String ext = "";
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = fileName.substring(0, dotIdx);
            ext = fileName.substring(dotIdx);
        }
        Path parent = path.getParent();
        int counter = 1;
        Path candidate;
        do {
            candidate = parent.resolve(baseName + "(" + counter + ")" + ext);
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }

    /* ==================== 项目空间下载 ==================== */

    @Override
    public Path resolveDownloadPath(Long projectId, String userId, String relativePath) {
        Path root = getProjectSpaceRoot(projectId, userId);
        // 空路径表示下载整个项目空间根目录
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return root;
        }
        Path resolved = resolveSafePath(root, relativePath);
        if (!Files.exists(resolved)) {
            throw new RuntimeException("文件或目录不存在：" + relativePath);
        }
        return resolved;
    }

    @Override
    public File createDownloadZip(Long projectId, String userId, String relativePath) {
        Path root = getProjectSpaceRoot(projectId, userId);
        Path source;
        if (relativePath == null || relativePath.trim().isEmpty()) {
            // 打包整个项目空间
            source = root;
        } else {
            source = resolveSafePath(root, relativePath);
        }
        if (!Files.exists(source)) {
            throw new RuntimeException("文件或目录不存在：" + relativePath);
        }
        // 确保临时目录存在
        File tempDir = new File(tempDownloadsDir);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new RuntimeException("无法创建下载临时目录");
        }
        // 生成唯一临时文件名
        String tempName = "proj" + projectId + "_" + System.currentTimeMillis() + ".zip";
        File zipFile = new File(tempDir, tempName);
        try {
            ZipFile zip = new ZipFile(zipFile);
            ZipParameters params = new ZipParameters();
            params.setCompressionMethod(CompressionMethod.DEFLATE);
            if (Files.isDirectory(source)) {
                zip.addFolder(source.toFile(), params);
            } else {
                zip.addFile(source.toFile(), params);
            }
            logger.info("项目[{}]已生成下载zip: {}", projectId, zipFile.getAbsolutePath());
            return zipFile;
        } catch (ZipException e) {
            if (zipFile.exists()) {
                zipFile.delete();
            }
            throw new RuntimeException("打包失败：" + e.getMessage(), e);
        }
    }

    /**
     * 定时清理下载临时文件：每小时执行一次，删除超过 1 小时的遗留 zip 文件。
     * 兜底机制：即使下载流式传输中 JVM 崩溃或客户端断连，也能回收磁盘空间。
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void cleanupTempDownloads() {
        File tempDir = new File(tempDownloadsDir);
        if (!tempDir.exists()) {
            return;
        }
        File[] files = tempDir.listFiles();
        if (files == null) {
            return;
        }
        long threshold = System.currentTimeMillis() - TEMP_MAX_AGE_MS;
        int cleaned = 0;
        for (File f : files) {
            if (f.isFile() && f.lastModified() < threshold) {
                if (f.delete()) {
                    cleaned++;
                } else {
                    logger.warn("清理临时下载文件失败: {}", f.getAbsolutePath());
                }
            }
        }
        if (cleaned > 0) {
            logger.info("已清理 {} 个过期的下载临时文件", cleaned);
        }
    }
}

