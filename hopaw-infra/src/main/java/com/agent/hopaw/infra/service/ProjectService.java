package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.ProjectMapper;
import com.agent.hopaw.infra.mapper.WorkflowTaskMapper;
import com.agent.hopaw.infra.model.dto.FileTreeNode;
import com.agent.hopaw.infra.model.dto.FileUploadItem;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.model.entity.WorkflowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProjectService implements IProjectService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectService.class);

    /** 允许的项目状态值 */
    private static final Set<String> ALLOWED_STATUS = new HashSet<>(Arrays.asList(
            "planning", "in_progress", "paused", "completed", "archived"
    ));
    /** 状态流转规则：key 可流转到 value 集合中的任一状态 */
    private static final java.util.Map<String, Set<String>> TRANSITIONS = new java.util.HashMap<>();
    static {
        TRANSITIONS.put("planning", new HashSet<>(Arrays.asList("in_progress", "archived")));
        TRANSITIONS.put("in_progress", new HashSet<>(Arrays.asList("paused", "completed", "archived")));
        TRANSITIONS.put("paused", new HashSet<>(Arrays.asList("in_progress", "archived")));
        TRANSITIONS.put("completed", new HashSet<>(Arrays.asList("in_progress", "archived")));
        TRANSITIONS.put("archived", new HashSet<>(Arrays.asList("planning")));
    }

    private final ProjectMapper projectMapper;
    private final WorkflowTaskMapper workflowTaskMapper;

    /** 项目空间根目录（所有项目的工作空间目录都放置在此目录下） */
    private final String projectSpaceRoot;

    public ProjectService(ProjectMapper projectMapper,
                          WorkflowTaskMapper workflowTaskMapper,
                          @Value("${hopaw.project.space.dir:./project-spaces}") String projectSpaceDir) {
        this.projectMapper = projectMapper;
        this.workflowTaskMapper = workflowTaskMapper;
        // 解析为绝对路径，确保后续文件操作一致
        File rootDir = new File(projectSpaceDir);
        if (!rootDir.isAbsolute()) {
            rootDir = new File(System.getProperty("user.dir"), projectSpaceDir);
        }
        this.projectSpaceRoot = rootDir.getAbsolutePath();
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        logger.info("项目空间根目录: {}", this.projectSpaceRoot);
    }

    @Override
    public Project createProject(Project project) {
        project.setStatus("planning");
        LocalDateTime now = LocalDateTime.now();
        project.setCreateTime(now);
        project.setUpdateTime(now);
        projectMapper.insert(project);
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
     * @return 项目空间绝对路径
     */
    private String createProjectSpace(Long projectId) {
        if (projectId == null) {
            return null;
        }
        try {
            String relativeDir = String.valueOf(projectId);
            Path spacePath = Paths.get(projectSpaceRoot, relativeDir).toAbsolutePath().normalize();
            Files.createDirectories(spacePath);
            String absPath = spacePath.toString();
            projectMapper.updateSpaceDir(projectId, absPath);
            logger.info("已创建项目[{}]空间目录: {}", projectId, absPath);
            return absPath;
        } catch (Exception e) {
            logger.error("创建项目[{}]空间目录失败", projectId, e);
            throw new RuntimeException("创建项目空间目录失败: " + e.getMessage());
        }
    }

    /**
     * 将存储的项目空间路径解析为绝对路径。
     * 新数据为相对路径（相对 projectSpaceRoot）；兼容旧数据（可能存储的是绝对路径）。
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
        // 相对路径：拼接项目空间根目录
        return Paths.get(projectSpaceRoot, storedSpaceDir).toAbsolutePath().normalize();
    }

    @Override
    public Project updateProject(Project project, String userId) {
        Project existing = projectMapper.findById(project.getId());
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权修改该项目");
        }
        existing.setName(project.getName());
        existing.setDescription(project.getDescription());
        // 状态变更走 updateStatus 接口，这里仅当传入了合法状态且与当前不同时校验流转
        if (project.getStatus() != null && !project.getStatus().equals(existing.getStatus())) {
            validateTransition(existing.getStatus(), project.getStatus());
            existing.setStatus(project.getStatus());
        }
        existing.setUpdateTime(LocalDateTime.now());
        projectMapper.update(existing);
        return existing;
    }

    @Override
    public void updateStatus(Long id, String status, String userId) {
        if (!ALLOWED_STATUS.contains(status)) {
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
    }

    /** 校验状态流转是否合法 */
    private void validateTransition(String from, String to) {
        Set<String> allowed = TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
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
        if (!userId.equals(existing.getUserId())) {
            throw new RuntimeException("无权删除该项目");
        }
        projectMapper.deleteById(id);
    }

    @Override
    public Project getProject(Long id, String userId) {
        Project project = projectMapper.findById(id);
        if (project == null) {
            return null;
        }
        if (!userId.equals(project.getUserId())) {
            return null;
        }
        return project;
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
}
