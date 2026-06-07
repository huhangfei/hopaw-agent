package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.avatar.model.AvatarModelGroup;
import com.agent.hopaw.infra.model.dto.AvatarSettings;
import com.agent.hopaw.infra.service.IAvatarSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class AvatarSettingsService implements IAvatarSettingsService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarSettingsService.class);

    private static final List<AvatarModelGroup> DEFAULT_MODEL_GROUPS;

    static {
        List<AvatarModelGroup> groups = new ArrayList<>();
        groups.add(new AvatarModelGroup("小萝莉", new ArrayList<>(Arrays.asList(
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
        groups.add(new AvatarModelGroup("邻家妹妹", new ArrayList<>(Arrays.asList(
                "js/avatar/model/haru/haru_01.model.json",
                "js/avatar/model/haru/haru_02.model.json"
        ))));
        groups.add(new AvatarModelGroup("成熟姐姐", new ArrayList<>(Arrays.asList(
                "js/avatar/model/HyperdimensionNeptunia/vert_swimwear/index.json",
                "js/avatar/model/HyperdimensionNeptunia/vert_classic/index.json",
                "js/avatar/model/HyperdimensionNeptunia/vert_normal/index.json"
        ))));

        groups.add(new AvatarModelGroup("爆炸头少年", new ArrayList<>(Arrays.asList(
                "js/avatar/model/touma/touma.model.json"
        ))));
        groups.add(new AvatarModelGroup("阳光男孩", new ArrayList<>(Arrays.asList(
                "js/avatar/model/chiaki_kitty/chiaki_kitty.model.json"
        ))));
        groups.add(new AvatarModelGroup("男绅士", new ArrayList<>(Arrays.asList(
                "js/avatar/model/stl/stl.model.json"
        ))));
        groups.add(new AvatarModelGroup("丸子", new ArrayList<>(Arrays.asList(
                "js/avatar/model/penchan/penchan.model.json"
        ))));
        DEFAULT_MODEL_GROUPS = Collections.unmodifiableList(groups);
    }

    private final AvatarConfigMapper avatarConfigMapper;

    public AvatarSettingsService(AvatarConfigMapper avatarConfigMapper) {
        this.avatarConfigMapper = avatarConfigMapper;
    }

    /** agentId 为空时使用兜底值（0L），不实际写入，仅用于在缺失配置时返回默认值 */
    public static final long FALLBACK_AGENT_ID = 0L;

    @Override
    public AvatarSettings getSettings(String userId, Long agentId) {
        AgentAvatarConfig config = loadConfig(userId, agentId);
        AvatarSettings settings = new AvatarSettings();
        settings.setDisabled(Boolean.TRUE.equals(config.getDisabled()));
        settings.setSoundEnabled(!Boolean.FALSE.equals(config.getSoundEnabled()));
        settings.setModelSetting(config.getModelSetting());
        settings.setModelGroup(config.getModelGroup());
        settings.setPersonaSetting(config.getPersonaSetting());
        settings.setTtsConfigId(config.getTtsConfigId());
        settings.setTtsVoiceId(config.getTtsVoiceId());
        settings.setTtsEmotions(config.getTtsEmotions());
        settings.setTtsEnabled(Boolean.TRUE.equals(config.getTtsEnabled()));
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
                cfg.setTtsConfigId(settings.getTtsConfigId());
                cfg.setTtsVoiceId(settings.getTtsVoiceId());
                cfg.setTtsEmotions(settings.getTtsEmotions());
                cfg.setTtsEnabled(settings.isTtsEnabled());
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
                existing.setTtsConfigId(settings.getTtsConfigId());
                existing.setTtsVoiceId(settings.getTtsVoiceId());
                existing.setTtsEmotions(settings.getTtsEmotions());
                existing.setTtsEnabled(settings.isTtsEnabled());
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

    public String getPersonaSetting(String userId, Long agentId) {
        if (agentId == null) {
            return "";
        }
        String value = loadConfig(userId, agentId).getPersonaSetting();
        return value == null ? "" : value;
    }

    private AgentAvatarConfig loadConfig(String userId, Long agentId) {
        if (userId == null || userId.isEmpty() || agentId == null) {
            AgentAvatarConfig agentAvatarConfig = new AgentAvatarConfig();
            agentAvatarConfig.setPersonaSetting(DEFAULT_PERSONA_PROMPT);
            return agentAvatarConfig;
        }
        try {
            AgentAvatarConfig config = avatarConfigMapper.findByUserAndAgent(userId, agentId);
            if (config == null) {
                config = new AgentAvatarConfig();
                config.setPersonaSetting(DEFAULT_PERSONA_PROMPT);
                return config;
            }
            return config;
        } catch (Exception e) {
            logger.error("加载虚拟人配置失败 userId=[{}] agentId=[{}]", userId, agentId, e);
            return new AgentAvatarConfig();
        }
    }
}
