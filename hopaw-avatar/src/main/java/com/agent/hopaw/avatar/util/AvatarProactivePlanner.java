package com.agent.hopaw.avatar.util;

import com.agent.hopaw.avatar.service.AvatarSettingsService;
import com.agent.hopaw.avatar.tool.AvatarChangeModelTool;
import com.agent.hopaw.avatar.tool.AvatarMoveTool;
import com.agent.hopaw.avatar.tool.AvatarProactiveTool;
import com.agent.hopaw.infra.constant.AiModelCallSourceEnum;
import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.model.entity.ChatHistory;
import com.agent.hopaw.infra.model.entity.ScheduledTask;
import com.agent.hopaw.infra.service.IAiModelService;
import com.agent.hopaw.infra.service.IChatModelListenerProvider;
import com.agent.hopaw.infra.util.InvocationParametersWrapper;
import com.agent.hopaw.infra.util.UuidUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AvatarProactivePlanner {

    private static final Logger logger = LoggerFactory.getLogger(AvatarProactivePlanner.class);

    public static final int RECENT_USER_INPUT_LIMIT = 10;
    public static final long RECENT_WINDOW_MINUTES = 30L;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final IAiModelService aiModelService;
    private final IChatModelListenerProvider chatModelListenerProvider;
    private final AvatarSettingsService avatarSettingsService;
    private final ChatHistoryMapper chatHistoryMapper;

    private final AvatarProactiveTool avatarProactiveTool;
    private final AvatarMoveTool avatarMoveTool;
    private final AvatarChangeModelTool avatarChangeModelTool;

    public AvatarProactivePlanner(IAiModelService aiModelService,
                                  IChatModelListenerProvider chatModelListenerProvider,
                                  AvatarSettingsService avatarSettingsService,
                                  ChatHistoryMapper chatHistoryMapper, AvatarProactiveTool avatarProactiveTool,
                                  AvatarMoveTool avatarMoveTool,
                                  AvatarChangeModelTool avatarChangeModelTool) {
        this.aiModelService = aiModelService;
        this.chatModelListenerProvider = chatModelListenerProvider;
        this.avatarSettingsService = avatarSettingsService;
        this.chatHistoryMapper = chatHistoryMapper;
        this.avatarProactiveTool = avatarProactiveTool;
        this.avatarMoveTool = avatarMoveTool;
        this.avatarChangeModelTool = avatarChangeModelTool;
    }

    /**
     * 处理用户输入并决策是否发送主动消息。
     *
     * @param userId    用户ID
     * @param task      定时任务
     * @param afterId   仅查询 id 大于该值的增量记录；若为 null 则按时间窗口全量
     * @return 实际参与本次处理的记录列表（按 id 升序）；若没有增量则返回空列表
     */
    public List<ChatHistory> analyzeAndDecide(String userId, ScheduledTask task, Long afterId) {
        Long modelId = avatarSettingsService.getAvatarAiModelId(userId);
        if (modelId == null) {
            logger.warn("虚拟人定时任务跳过：未配置大脑模型 userId={}", userId);
            return Collections.emptyList();
        }
        String persona = avatarSettingsService.getPersonaSetting(userId);
        if (persona == null || persona.isBlank()) {
            persona = "（未设置人设）";
        }
        String promptTemplate = avatarSettingsService.getAvatarAiPrompt(userId);
        if (promptTemplate == null || promptTemplate.isBlank()) {
            promptTemplate = AvatarSettingsService.DEFAULT_AVATAR_AI_PROMPT;
        }

        List<ChatHistory> recentInputs = getIncrementalUserInputs(userId, afterId, RECENT_USER_INPUT_LIMIT);
        if (recentInputs.isEmpty()) {
            logger.info("虚拟人定时任务跳过：未查询到增量输入 userId={} afterId={}", userId, afterId);
            return Collections.emptyList();
        }
        String formattedInputs = formatRecentInputs(recentInputs);
        String currentTime = LocalDateTime.now().format(TIME_FORMAT);

        String finalPrompt = promptTemplate
                .replace("{persona}", persona)
                .replace("{currentTime}", currentTime);

        ChatModelListener listener = chatModelListenerProvider.getChatModelListener(
                AiModelCallSourceEnum.MEMORYORGANIZE, UUID.randomUUID().toString(), userId, null);
        ChatModel chatModel;
        try {
            chatModel = aiModelService.createChatModel(modelId, false, listener);
        } catch (Exception e) {
            logger.error("创建虚拟人大脑模型失败 userId={} modelId={}", userId, modelId, e);
            return recentInputs;
        }

        try {
            InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create()
                    .setUserId(task.getUserId())
                    .setRequestId(UuidUtil.generateSimpleUUID())
                    .setSessionId(UuidUtil.generateSimpleUUID());
            Assistant assistant = AiServices.builder(Assistant.class)
                    .chatModel(chatModel)
                    .systemMessageProvider(chatMemoryId -> finalPrompt)
                    .tools(Arrays.asList(avatarProactiveTool, avatarMoveTool, avatarChangeModelTool))
                    .maxSequentialToolsInvocations(1)
                    .build();
            ChatRequestParameters chatRequestParameters=ChatRequestParameters.builder()
                    .temperature(0.1)
                    .build();
            String result = assistant.chat("近期输入：" + formattedInputs, chatRequestParameters, invocationParametersWrapper.getParameters());
            logger.info("虚拟人大脑模型调用结果 userId={} modelId={} result={}", userId, modelId, result);
        } catch (Exception e) {
            logger.error("虚拟人大脑模型调用失败 userId={} modelId={}", userId, modelId, e);
        }
        return recentInputs;
    }
    public interface Assistant {
        @dev.langchain4j.service.UserMessage("{{content}}")
        String chat(String content,
                    ChatRequestParameters chatRequestParameters, InvocationParameters invocationParameters);
    }

    public List<ChatHistory> getIncrementalUserInputs(String userId, Long afterId, int limit) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(RECENT_WINDOW_MINUTES);
        try {
            List<ChatHistory> list;
            long startId = afterId == null ? 0L : afterId;
            list = chatHistoryMapper.findRecentByUserIdAndRoleAfterId(userId, "user", startId, since, limit);
            if (list == null) {
                return Collections.emptyList();
            }
            return list;
        } catch (Exception e) {
            logger.warn("查询用户增量输入失败 userId={} afterId={} err={}", userId, afterId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<ChatHistory> getRecentUserInputs(String userId, int limit) {
        if (userId == null || userId.isBlank()) {
            return Collections.emptyList();
        }
        LocalDateTime since = LocalDateTime.now().minusMinutes(RECENT_WINDOW_MINUTES);
        try {
            List<ChatHistory> list = chatHistoryMapper.findRecentByUserIdAndRole(userId, "user", since, limit);
            if (list == null) {
                return Collections.emptyList();
            }
            List<ChatHistory> reversed = new ArrayList<>(list);
            Collections.reverse(reversed);
            return reversed;
        } catch (Exception e) {
            logger.warn("查询用户最近输入失败 userId={} err={}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public String formatRecentInputs(List<ChatHistory> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return "（最近 30 分钟内暂无用户输入）";
        }
        return inputs.stream()
                .map(h -> {
                    String ts = h.getCreateTime() == null ? "" : h.getCreateTime().format(TIME_FORMAT);
                    String content = h.getContent() == null ? "" : h.getContent();
                    return "- [" + ts + "] " + content;
                })
                .collect(Collectors.joining("\n"));
    }

    private Optional<AvatarProactivePlan> parsePlan(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String cleaned = stripCodeFence(text).trim();
        try {
            JSONObject json = JSON.parseObject(cleaned);
            if (json == null) {
                return Optional.empty();
            }
            boolean needRemind = Boolean.TRUE.equals(json.getBoolean("needRemind"));
            String reason = json.getString("reason");
            String message = json.getString("message");
            if (needRemind) {
                return Optional.of(AvatarProactivePlan.remind(reason, message));
            }
            return Optional.of(AvatarProactivePlan.skip(reason));
        } catch (Exception e) {
            logger.warn("解析虚拟人主动消息计划失败，返回 skip。原始输出：{}", text);
            return Optional.of(AvatarProactivePlan.skip("解析失败"));
        }
    }

    private String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) {
                t = t.substring(firstNewline + 1);
            }
            int lastFence = t.lastIndexOf("```");
            if (lastFence >= 0) {
                t = t.substring(0, lastFence);
            }
        }
        return t;
    }
}
