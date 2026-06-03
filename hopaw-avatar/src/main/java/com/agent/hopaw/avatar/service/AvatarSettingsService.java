package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.avatar.model.AvatarModelGroup;
import com.agent.hopaw.avatar.model.AvatarSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class AvatarSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarSettingsService.class);

    public static final String DEFAULT_AVATAR_AI_PROMPT = "你是{agentName}，{agentDesc}，这是用户的记忆画像{userProfile}，请根据用户最近输入的内容，分析用户当前可能在做什么、处于什么状态，以及是否需要主动提醒。{toolCallTips}\n现在时间是{currentTime}。";
    public static final String TOOL_CALL_TIPS = "当需要提醒时，请调用sendMessageToUser工具进行发送消息；\n当需要表现存在感时，请调用moveAvatar工具进行控制移动或者调用changeAvatarModel工具进行换装；\n";

    /** 主动消息回忆窗口默认分钟数（用户未配置时的回退值） */
    public static final int DEFAULT_MEMORY_WINDOW_MINUTES = 10;
    /** 主动消息回忆默认最大记录数（用户未配置时的回退值） */
    public static final int DEFAULT_MEMORY_MAX_RECORDS = 20;

    private static final List<AvatarModelGroup> DEFAULT_MODEL_GROUPS;

    static {
        List<AvatarModelGroup> groups = new ArrayList<>();
        groups.add(new AvatarModelGroup("22", new ArrayList<>(Arrays.asList(
                "js/avatar/model/22/model.default.json",
                "js/avatar/model/22/model.2016.xmas.1.json",
                "js/avatar/model/22/model.2016.xmas.2.json",
                "js/avatar/model/22/model.2017.cba-normal.json",
                "js/avatar/model/22/model.2017.cba-super.json",
                "js/avatar/model/22/model.2017.newyear.json",
                "js/avatar/model/22/model.2017.school.json",
                "js/avatar/model/22/model.2017.summer.normal.1.json",
                "js/avatar/model/22/model.2017.summer.normal.2.json",
                "js/avatar/model/22/model.2017.summer.super.1.json",
                "js/avatar/model/22/model.2017.summer.super.2.json",
                "js/avatar/model/22/model.2017.tomo-bukatsu.high.json",
                "js/avatar/model/22/model.2017.tomo-bukatsu.low.json",
                "js/avatar/model/22/model.2017.valley.json",
                "js/avatar/model/22/model.2017.vdays.json",
                "js/avatar/model/22/model.2018.bls-summer.json",
                "js/avatar/model/22/model.2018.bls-winter.json",
                "js/avatar/model/22/model.2018.lover.json",
                "js/avatar/model/22/model.2018.spring.json"
        ))));
        groups.add(new AvatarModelGroup("haru", new ArrayList<>(Arrays.asList(
                "js/avatar/model/haru/haru_01.model.json",
                "js/avatar/model/haru/haru_02.model.json"
        ))));
        DEFAULT_MODEL_GROUPS = Collections.unmodifiableList(groups);
    }

    private final AvatarConfigMapper avatarConfigMapper;

    public AvatarSettingsService(AvatarConfigMapper avatarConfigMapper) {
        this.avatarConfigMapper = avatarConfigMapper;
    }

    /** agentId 为空时使用兜底值（0L），不实际写入，仅用于在缺失配置时返回默认值 */
    public static final long FALLBACK_AGENT_ID = 0L;

    public AvatarSettings getSettings(String userId, Long agentId) {
        AgentAvatarConfig config = loadConfig(userId, agentId);
        AvatarSettings settings = new AvatarSettings();
        settings.setDisabled(Boolean.TRUE.equals(config.getDisabled()));
        settings.setSoundEnabled(!Boolean.FALSE.equals(config.getSoundEnabled()));
        settings.setModelSetting(config.getModelSetting());
        settings.setModelGroup(config.getModelGroup());
        settings.setPersonaSetting(config.getPersonaSetting());
        settings.setAvatarAiPrompt(config.getAvatarAiPrompt());
        settings.setMemoryWindowMinutes(config.getMemoryWindowMinutes() == null
                ? DEFAULT_MEMORY_WINDOW_MINUTES : config.getMemoryWindowMinutes());
        settings.setMemoryMaxRecords(config.getMemoryMaxRecords() == null
                ? DEFAULT_MEMORY_MAX_RECORDS : config.getMemoryMaxRecords());
        return settings;
    }

    public void saveSettings(String userId, Long agentId, AvatarSettings settings) {
        if (settings == null) {
            return;
        }
        if (userId == null || userId.isEmpty()) {
            logger.warn("跳过保存虚拟人配置，userId 为空");
            return;
        }
        if (agentId == null) {
            logger.warn("跳过保存虚拟人配置，agentId 为空 userId=[{}]", userId);
            return;
        }
        try {
            // 先判断该 (userId, agentId) 是否已存在
            AgentAvatarConfig existing = avatarConfigMapper.findByUserAndAgent(userId, agentId);
            boolean isInsert = (existing == null);
            logger.info("[avatar-settings] 保存判断 userId=[{}] agentId=[{}] 已存在={} modelGroup=[{}]",
                    userId, agentId, !isInsert, settings.getModelGroup());

            if (isInsert) {
                // 不存在 → INSERT 新行
                AgentAvatarConfig cfg = new AgentAvatarConfig();
                cfg.setUserId(userId);
                cfg.setAgentId(agentId);
                cfg.setDisabled(settings.isDisabled());
                cfg.setSoundEnabled(settings.isSoundEnabled());
                cfg.setModelSetting(settings.getModelSetting());
                cfg.setModelGroup(settings.getModelGroup());
                cfg.setPersonaSetting(settings.getPersonaSetting());
                cfg.setAvatarAiPrompt(settings.getAvatarAiPrompt());
                cfg.setMemoryWindowMinutes(settings.getMemoryWindowMinutes() == null
                        ? DEFAULT_MEMORY_WINDOW_MINUTES : settings.getMemoryWindowMinutes());
                cfg.setMemoryMaxRecords(settings.getMemoryMaxRecords() == null
                        ? DEFAULT_MEMORY_MAX_RECORDS : settings.getMemoryMaxRecords());
                cfg.setTotalTokens(0L);
                cfg.setLastProcessedChatId(0L);
                int rows = avatarConfigMapper.insert(cfg);
                logger.info("[avatar-settings] INSERT 影响行数={} 新行 id={} userId=[{}] agentId=[{}] modelGroup=[{}]",
                        rows, cfg.getId(), userId, agentId, cfg.getModelGroup());
            } else {
                // 已存在 → UPDATE
                existing.setDisabled(settings.isDisabled());
                existing.setSoundEnabled(settings.isSoundEnabled());
                existing.setModelSetting(settings.getModelSetting());
                existing.setModelGroup(settings.getModelGroup());
                existing.setPersonaSetting(settings.getPersonaSetting());
                existing.setAvatarAiPrompt(settings.getAvatarAiPrompt());
                existing.setMemoryWindowMinutes(settings.getMemoryWindowMinutes() == null
                        ? existing.getMemoryWindowMinutes()
                        : settings.getMemoryWindowMinutes());
                existing.setMemoryMaxRecords(settings.getMemoryMaxRecords() == null
                        ? existing.getMemoryMaxRecords()
                        : settings.getMemoryMaxRecords());
                int rows = avatarConfigMapper.update(existing);
                logger.info("[avatar-settings] UPDATE 影响行数={} userId=[{}] agentId=[{}] modelGroup=[{}]",
                        rows, userId, agentId, existing.getModelGroup());
            }
        } catch (Exception e) {
            logger.error("保存虚拟人配置失败 userId=[{}] agentId=[{}]", userId, agentId, e);
        }
    }

    public boolean isAvatarDisabled(String userId, Long agentId) {
        if (agentId == null) {
            return false;
        }
        return Boolean.TRUE.equals(loadConfig(userId, agentId).getDisabled());
    }

    public boolean isSoundEnabled(String userId, Long agentId) {
        if (agentId == null) {
            return true;
        }
        AgentAvatarConfig config = loadConfig(userId, agentId);
        return !Boolean.FALSE.equals(config.getSoundEnabled());
    }

    public List<AvatarModelGroup> listModelGroups() {
        return DEFAULT_MODEL_GROUPS;
    }

    public String getSelectedModelGroup(String userId, Long agentId) {
        if (agentId == null) {
            return "";
        }
        String group = loadConfig(userId, agentId).getModelGroup();
        return group == null ? "" : group.trim();
    }

    public List<String> resolveModelPool(String userId, Long agentId) {
        String selected = getSelectedModelGroup(userId, agentId);
        if (selected.isEmpty()) {
            List<String> all = new ArrayList<>();
            for (AvatarModelGroup g : DEFAULT_MODEL_GROUPS) {
                if (g.getModels() != null) {
                    all.addAll(g.getModels());
                }
            }
            return all;
        }
        for (AvatarModelGroup g : DEFAULT_MODEL_GROUPS) {
            if (selected.equalsIgnoreCase(g.getName())) {
                return g.getModels() == null ? Collections.emptyList() : new ArrayList<>(g.getModels());
            }
        }
        logger.warn("未找到指定的模型组 [{}]，将返回全部模型", selected);
        List<String> all = new ArrayList<>();
        for (AvatarModelGroup g : DEFAULT_MODEL_GROUPS) {
            if (g.getModels() != null) {
                all.addAll(g.getModels());
            }
        }
        return all;
    }

    public String getAvatarAiPrompt(String userId, Long agentId) {
        if (agentId == null) {
            return "";
        }
        String value = loadConfig(userId, agentId).getAvatarAiPrompt();
        return value == null ? "" : value;
    }

    public String getPersonaSetting(String userId, Long agentId) {
        if (agentId == null) {
            return "";
        }
        String value = loadConfig(userId, agentId).getPersonaSetting();
        return value == null ? "" : value;
    }

    /**
     * 主动消息回忆窗口分钟数。用户未配置时回退到默认 10 分钟。
     */
    public int getMemoryWindowMinutes(String userId, Long agentId) {
        if (agentId == null) {
            return DEFAULT_MEMORY_WINDOW_MINUTES;
        }
        Integer value = loadConfig(userId, agentId).getMemoryWindowMinutes();
        return value == null || value <= 0 ? DEFAULT_MEMORY_WINDOW_MINUTES : value;
    }

    /**
     * 主动消息回忆最大记录数。用户未配置时回退到默认 20 条。
     */
    public int getMemoryMaxRecords(String userId, Long agentId) {
        if (agentId == null) {
            return DEFAULT_MEMORY_MAX_RECORDS;
        }
        Integer value = loadConfig(userId, agentId).getMemoryMaxRecords();
        return value == null || value <= 0 ? DEFAULT_MEMORY_MAX_RECORDS : value;
    }

    private AgentAvatarConfig loadConfig(String userId, Long agentId) {
        if (userId == null || userId.isEmpty() || agentId == null) {
            return new AgentAvatarConfig();
        }
        try {
            AgentAvatarConfig config = avatarConfigMapper.findByUserAndAgent(userId, agentId);
            return config != null ? config : new AgentAvatarConfig();
        } catch (Exception e) {
            logger.error("加载虚拟人配置失败 userId=[{}] agentId=[{}]", userId, agentId, e);
            return new AgentAvatarConfig();
        }
    }
}
