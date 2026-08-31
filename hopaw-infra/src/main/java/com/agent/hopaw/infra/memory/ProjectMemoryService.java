package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.mapper.ProjectMapper;
import com.agent.hopaw.infra.model.entity.Project;
import com.agent.hopaw.infra.service.IAiModelService;
import com.agent.hopaw.infra.service.IProjectService;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.monitor.LangChain4jChatModelListener;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 项目/任务维度记忆服务：与用户维度长时记忆（LongTermMemoryService）并列。
 *
 * 差异定位：
 * - 用户记忆：按 (sessionId, userId) 总结，存 SQLite + 向量库，面向聊天会话；
 * - 项目/任务记忆：按项目/任务维度总结（跨用户共享——项目与任务本身不区分用户），
 *   以 Markdown 文件持久化到项目空间目录，随项目生命周期存在。
 *
 * 存储布局（项目空间内）：
 * - memory/project-memory.md        项目整体记忆（目标、进展、关键决策、经验教训）
 * - memory/task-{taskId}-memory.md  单任务执行记忆（每次执行/交互的增量总结）
 *
 * 写入策略：AI 增量总结合并——新会话纪要与现有记忆一并交给模型，产出精简合并后的记忆全文写回，
 * 避免追加式存储无限膨胀；AI 不可用时回退为带时间戳的分节追加。
 */
@Service
public class ProjectMemoryService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectMemoryService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final String MEMORY_DIR = "memory";
    private static final String PROJECT_MEMORY_FILE = "project-memory.md";
    private static final String TASK_MEMORY_FILE_PREFIX = "task-";
    private static final String TASK_MEMORY_FILE_SUFFIX = "-memory.md";
    /** 记忆文件大小保护上限（字符数），超过则强制走 AI 总结合并压缩 */
    private static final int MAX_MEMORY_CHARS = 32 * 1024;

    private final IProjectService projectService;
    private final ProjectMapper projectMapper;
    private final IAiModelService aiModelService;
    private final ISysConfigService sysConfigService;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectMemoryService(IProjectService projectService,
                                ProjectMapper projectMapper,
                                IAiModelService aiModelService,
                                ISysConfigService sysConfigService,
                                ApplicationEventPublisher eventPublisher) {
        this.projectService = projectService;
        this.projectMapper = projectMapper;
        this.aiModelService = aiModelService;
        this.sysConfigService = sysConfigService;
        this.eventPublisher = eventPublisher;
    }

    /** 读取项目整体记忆（无记忆返回 null） */
    public String getProjectMemoryContent(Long projectId) {
        return readFile(resolveMemoryFile(projectId, PROJECT_MEMORY_FILE));
    }

    /** 读取指定任务记忆（无记忆返回 null） */
    public String getTaskMemoryContent(Long projectId, Long taskId) {
        return readFile(resolveMemoryFile(projectId, TASK_MEMORY_FILE_PREFIX + taskId + TASK_MEMORY_FILE_SUFFIX));
    }

    /**
     * 更新任务记忆：新会话纪要与现有记忆 AI 总结合并后写回任务记忆文件。
     *
     * @param projectId 项目编号（决定记忆落盘位置）
     * @param taskId    任务编号
     * @param newConversation 新增会话纪要文本
     * @param userId    触发本次整理的用户（用于定位项目空间与模型调用监听）
     */
    public void updateTaskMemory(Long projectId, Long taskId, String newConversation, String userId) {
        if (projectId == null || taskId == null || newConversation == null || newConversation.isBlank()) {
            return;
        }
        Path file = resolveMemoryFile(projectId, TASK_MEMORY_FILE_PREFIX + taskId + TASK_MEMORY_FILE_SUFFIX);
        writeMergedMemory(file, "任务记忆（任务编号 " + taskId + "）", newConversation, userId);
    }

    /** 更新项目整体记忆：新会话纪要与现有记忆 AI 总结合并后写回 */
    public void updateProjectMemory(Long projectId, String newConversation, String userId) {
        if (projectId == null || newConversation == null || newConversation.isBlank()) {
            return;
        }
        Path file = resolveMemoryFile(projectId, PROJECT_MEMORY_FILE);
        writeMergedMemory(file, "项目记忆（项目编号 " + projectId + "）", newConversation, userId);
    }

    /**
     * 合并写入：优先 AI 总结压缩；AI 失败或文件未超限时回退为分节追加（带时间戳，永不丢失数据）。
     */
    private void writeMergedMemory(Path file, String memoryTitle, String newConversation, String userId) {
        try {
            String existing = readFile(file);
            boolean needCompress = existing != null && existing.length() > MAX_MEMORY_CHARS;
            String merged = trySummarize(memoryTitle, existing, newConversation, userId, needCompress);
            if (merged != null && !merged.isBlank()) {
                Files.createDirectories(file.getParent());
                Files.write(file, merged.getBytes(StandardCharsets.UTF_8));
                logger.info("项目空间记忆已更新: {}", file);
            }
        } catch (Exception e) {
            logger.warn("项目空间记忆写入失败: file={}", file, e);
        }
    }

    /**
     * AI 总结合并：现有记忆 + 新纪要 -> 精简记忆全文。
     * 文件未超限时采用追加分节（保留过程细节）；超限或现有记忆存在时合并压缩。
     * 返回 null 表示放弃 AI 结果（调用方按需回退）。
     */
    private String trySummarize(String memoryTitle, String existing, String newConversation,
                                String userId, boolean needCompress) {
        // 无现有记忆且无需压缩：AI 直接从纪要生成首版记忆
        if (existing == null || existing.isBlank()) {
            return callMemoryModel(memoryTitle, null, newConversation, userId, false);
        }
        if (!needCompress) {
            // 未超限：AI 生成新纪要的小结，追加为一节
            String section = callMemoryModel(memoryTitle, null, newConversation, userId, true);
            if (section == null || section.isBlank()) {
                section = newConversation.length() > 2000 ? newConversation.substring(0, 2000) + "…" : newConversation;
            }
            return existing + "\n\n---- [" + LocalDateTime.now().format(TIME_FMT) + "] ----\n" + section;
        }
        // 超限：合并压缩
        String compressed = callMemoryModel(memoryTitle, existing, newConversation, userId, false);
        return compressed != null && !compressed.isBlank() ? compressed
                // AI 失败兜底：粗略截断保留最新内容
                : (existing + "\n\n---- [" + LocalDateTime.now().format(TIME_FMT) + "] ----\n" + truncate(newConversation));
    }

    private String truncate(String text) {
        return text != null && text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
    }

    /** 调用记忆整理模型；appendMode=true 时只总结新纪要本身 */
    private String callMemoryModel(String memoryTitle, String existing, String newConversation,
                                   String userId, boolean appendMode) {
        try {
            Long modelId = parseMemoryModelId();
            LangChain4jChatModelListener listener = new LangChain4jChatModelListener(AiModelCallSourceEnum.MemoryOrganize)
                    .setUserId(userId)
                    .setEventPublisher(eventPublisher);
            ChatModel chatModel = aiModelService.createChatModel(modelId, true, listener);
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是").append(memoryTitle).append("的管理助手，请整理一段对话纪要为可供后续执行参考的记忆。\n");
            prompt.append("要求：\n");
            if (appendMode) {
                prompt.append("1. 只输出新纪要的精炼小结（目标、结论、关键决策、待办、经验教训），不包含现有记忆。\n");
            } else {
                prompt.append("1. 将【现有记忆】与【新会话纪要】合并为一份完整记忆，保留仍然有效的信息，剔除过时与重复内容，按主题分节组织。\n");
                if (existing != null && !existing.isBlank()) {
                    prompt.append("\n【现有记忆】\n").append(existing).append("\n");
                }
            }
            prompt.append("2. 直接输出 Markdown 正文，不要任何寒暄或解释性前后缀。\n");
            prompt.append("3. 控制在 800 字以内，信息密度优先。\n");
            prompt.append("\n【新会话纪要】\n").append(newConversation).append("\n");

            ChatResponse response = chatModel.chat(UserMessage.from(prompt.toString()));
            String text = response == null || response.aiMessage() == null ? null : response.aiMessage().text();
            return text == null ? null : text.trim();
        } catch (Exception e) {
            logger.warn("项目空间记忆 AI 汇总失败（{}）：{}", memoryTitle, e.getMessage());
            return null;
        }
    }

    private Long parseMemoryModelId() {
        String modelIdStr = sysConfigService.getValueByKey("memory_ai_model_id", "");
        if (modelIdStr != null && !modelIdStr.isBlank()) {
            try {
                return Long.parseLong(modelIdStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /** 解析项目空间内的记忆文件路径；项目不存在时返回 null */
    private Path resolveMemoryFile(Long projectId, String fileName) {
        if (projectId == null) {
            return null;
        }
        try {
            Project project = projectMapper.findById(projectId);
            if (project == null) {
                return null;
            }
            String space = projectService.getProjectSpaceAbsolutePath(projectId, project.getUserId());
            if (space == null || space.isEmpty()) {
                return null;
            }
            return Paths.get(space, MEMORY_DIR, fileName);
        } catch (Exception e) {
            logger.warn("解析项目空间记忆路径失败: projectId={}", projectId, e);
            return null;
        }
    }

    private String readFile(Path file) {
        if (file == null) {
            return null;
        }
        try {
            if (Files.exists(file)) {
                String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                return content.isBlank() ? null : content;
            }
        } catch (Exception e) {
            logger.warn("读取项目空间记忆失败: file={}", file, e);
        }
        return null;
    }
}
