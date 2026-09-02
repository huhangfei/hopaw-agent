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
     * 合并写入：每次都将现有记忆与新纪要一并交给 AI 合并总结（去重、压缩、按主题重组）后写回全文，
     * 避免追加式存储产生大量重复分节。AI 失败时回退为分节追加（带时间戳，永不丢失数据）。
     */
    private void writeMergedMemory(Path file, String memoryTitle, String newConversation, String userId) {
        try {
            String existing = readFile(file);
            String merged = trySummarize(memoryTitle, existing, newConversation, userId);
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
     * 每次更新均为全量合并（跨轮次重复内容会被合并去重），保持记忆整洁。
     * 总结结果经校验模型校验，不通过则重试一次；两次均不通过回退分节追加。
     * 返回 null 表示放弃 AI 结果（调用方按需回退）。
     */
    private String trySummarize(String memoryTitle, String existing, String newConversation, String userId) {
        // 无现有记忆：AI 直接从纪要生成首版记忆（同样走校验+重试）
        if (existing == null || existing.isBlank()) {
            String first = callMemoryModel(memoryTitle, null, newConversation, userId);
            if (first != null && !first.isBlank() && validateMemoryResult(memoryTitle, first, newConversation)) {
                return first;
            }
            String retry = callMemoryModel(memoryTitle, null, newConversation, userId);
            if (retry != null && !retry.isBlank() && validateMemoryResult(memoryTitle, retry, newConversation)) {
                return retry;
            }
            // 校验两次不通过：仍返回可用的总结结果（优于直接丢弃），全部失败返回 null
            return first != null && !first.isBlank() ? first : retry;
        }
        // 有现有记忆：合并压缩去重后写回全文（校验+重试）
        String merged = callMemoryModel(memoryTitle, existing, newConversation, userId);
        if (merged != null && !merged.isBlank() && validateMemoryResult(memoryTitle, merged, newConversation)) {
            return merged;
        }
        // 校验不通过：重试一次
        logger.info("项目空间记忆总结校验未通过，重试一次（{}）", memoryTitle);
        String retry = callMemoryModel(memoryTitle, existing, newConversation, userId);
        if (retry != null && !retry.isBlank() && validateMemoryResult(memoryTitle, retry, newConversation)) {
            return retry;
        }
        if (merged != null && !merged.isBlank()) {
            // 两次校验均未通过但总结内容可用：仍采用总结结果（优于分节追加产生重复）
            logger.warn("项目空间记忆总结两次校验未通过，采用首次总结结果（{}）", memoryTitle);
            return merged;
        }
        // AI 失败兜底：带时间戳分节追加（粗略截断保留最新内容）
        return existing + "\n\n---- [" + LocalDateTime.now().format(TIME_FMT) + "] ----\n" + truncate(newConversation);
    }

    private String truncate(String text) {
        return text != null && text.length() > 2000 ? text.substring(0, 2000) + "…" : text;
    }

    /**
     * 校验总结结果是否为有效的记忆内容（防止模型返回寒暄、解释性文字或无效输出）。
     * 使用独立的大模型调用进行校验；校验模型异常时视为通过（不阻塞主流程）。
     *
     * @return true 校验通过；false 校验不通过（触发重试）
     */
    private boolean validateMemoryResult(String memoryTitle, String candidate, String newConversation) {
        try {
            Long modelId = parseMemoryModelId();
            LangChain4jChatModelListener listener = new LangChain4jChatModelListener(AiModelCallSourceEnum.MemoryOrganize)
                    .setUserId(null)
                    .setEventPublisher(eventPublisher);
            ChatModel chatModel = aiModelService.createChatModel(modelId, true, listener);
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是").append(memoryTitle).append("的质量校验员。请校验以下【待校验记忆】是否为有效的项目/任务记忆内容。\n");
            prompt.append("校验标准（全部满足才算通过）：\n");
            prompt.append("1. 内容与【新会话纪要】相关，是对纪要的有效整理或总结（不能答非所问、跑题或输出无关内容）。\n");
            prompt.append("2. 是正经的记忆正文（Markdown 格式或分节要点），没有寒暄、客套、解释性前后缀（如“好的，以下是...”“希望对您有帮助”等）。\n");
            prompt.append("3. 内容基本连贯可读，不是乱码、空壳、单字符流或无意义的重复。\n");
            prompt.append("4. 长度合理（不超过 5000 字，且不是只有标题没有实质内容）。\n");
            prompt.append("请只输出“通过”或“不通过”，不要输出任何其他内容。\n");
            prompt.append("\n【新会话纪要】\n").append(truncate(newConversation)).append("\n");
            prompt.append("\n【待校验记忆】\n").append(candidate).append("\n");

            ChatResponse response = chatModel.chat(UserMessage.from(prompt.toString()));
            String text = response == null || response.aiMessage() == null ? null : response.aiMessage().text();
            if (text == null || text.isBlank()) {
                // 校验模型无输出：视为通过，避免误伤正常结果
                return true;
            }
            boolean pass = text.trim().startsWith("通过") && !text.trim().startsWith("不通过");
            if (!pass) {
                logger.warn("项目空间记忆校验未通过（{}）：{}", memoryTitle, text.trim());
            }
            return pass;
        } catch (Exception e) {
            // 校验模型调用失败：视为通过（校验是增强手段，不阻塞主流程）
            logger.warn("项目空间记忆校验调用失败（{}）：{}", memoryTitle, e.getMessage());
            return true;
        }
    }

    /** 调用记忆整理模型：将现有记忆与新会话纪要合并为去重、压缩后的记忆全文 */
    private String callMemoryModel(String memoryTitle, String existing, String newConversation, String userId) {
        try {
            Long modelId = parseMemoryModelId();
            LangChain4jChatModelListener listener = new LangChain4jChatModelListener(AiModelCallSourceEnum.MemoryOrganize)
                    .setUserId(userId)
                    .setEventPublisher(eventPublisher);
            ChatModel chatModel = aiModelService.createChatModel(modelId, true, listener);
            StringBuilder prompt = new StringBuilder();
            prompt.append("你是").append(memoryTitle).append("的管理助手，请将【现有记忆】与【新会话纪要】合并整理为一份可供后续执行参考的记忆。\n");
            prompt.append("要求：\n");
            prompt.append("1. 按主题分节组织（如：目标、当前进展与状态、关键决策、待办事项、经验教训）。\n");
            prompt.append("2. 严格去重：同一主题或信息在多轮记录中重复出现时，只保留最新、最完整的一版，删除旧版本与重复的状态快照，禁止输出重复内容。\n");
            prompt.append("3. 剔除过时信息：已完成的临时目标、已变更的旧状态、已解决的问题等被新内容取代后直接删除。\n");
            if (existing != null && !existing.isBlank()) {
                prompt.append("\n【现有记忆】\n").append(existing).append("\n");
            }
            prompt.append("4. 直接输出 Markdown 正文，不要任何寒暄或解释性前后缀。\n");
            prompt.append("5. 控制在 5000 字以内，信息密度优先。\n");
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
