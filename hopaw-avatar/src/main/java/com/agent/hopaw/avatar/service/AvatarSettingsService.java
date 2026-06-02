package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.model.AvatarModelGroup;
import com.agent.hopaw.avatar.model.AvatarSettings;
import com.agent.hopaw.infra.mapper.AvatarConfigMapper;
import com.agent.hopaw.infra.model.entity.AvatarConfig;
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

    public static final String KEY_DISABLED = "avatar_disabled";
    public static final String KEY_MODEL_SETTING = "avatar_model_setting";
    public static final String KEY_MODEL_GROUP = "avatar_model_group";
    public static final String KEY_PERSONA_SETTING = "avatar_persona_setting";
    public static final String KEY_AVATAR_AI_MODEL_ID = "avatar_ai_model_id";
    public static final String KEY_AVATAR_AI_PROMPT = "avatar_ai_prompt";

    public static final String DEFAULT_AVATAR_AI_PROMPT = "你是一个贴心的虚拟人助手。请结合用户的最近 30 分钟内输入内容（前时间{currentTime}），分析用户当前可能在做什么、处于什么状态，以及是否需要主动提醒。\n" +
            "人设设定：\n{persona}"+
            "当需要提醒时，请调用sendMessageToUser工具进行发送\n";

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

    public AvatarSettings getSettings(String userId) {
        AvatarConfig config = loadConfig(userId);
        AvatarSettings settings = new AvatarSettings();
        settings.setDisabled(Boolean.TRUE.equals(config.getDisabled()));
        settings.setModelSetting(config.getModelSetting());
        settings.setModelGroup(config.getModelGroup());
        settings.setPersonaSetting(config.getPersonaSetting());
        settings.setAvatarAiModelId(config.getAvatarAiModelId());
        settings.setAvatarAiPrompt(config.getAvatarAiPrompt());
        return settings;
    }

    public void saveSettings(String userId, AvatarSettings settings) {
        if (settings == null) {
            return;
        }
        if (userId == null || userId.isEmpty()) {
            logger.warn("跳过保存虚拟人配置，userId 为空");
            return;
        }
        try {
            AvatarConfig existing = avatarConfigMapper.findByUserId(userId);
            AvatarConfig cfg = existing != null ? existing : new AvatarConfig();
            cfg.setUserId(userId);
            cfg.setDisabled(settings.isDisabled());
            cfg.setModelSetting(settings.getModelSetting());
            cfg.setModelGroup(settings.getModelGroup());
            cfg.setPersonaSetting(settings.getPersonaSetting());
            cfg.setAvatarAiModelId(settings.getAvatarAiModelId());
            cfg.setAvatarAiPrompt(settings.getAvatarAiPrompt());
            if (existing == null) {
                avatarConfigMapper.insert(cfg);
            } else {
                avatarConfigMapper.update(cfg);
            }
        } catch (Exception e) {
            logger.error("保存虚拟人配置失败 userId=[{}]", userId, e);
        }
    }

    public boolean isAvatarDisabled(String userId) {
        return Boolean.TRUE.equals(loadConfig(userId).getDisabled());
    }

    public List<AvatarModelGroup> listModelGroups() {
        return DEFAULT_MODEL_GROUPS;
    }

    public String getSelectedModelGroup(String userId) {
        String group = loadConfig(userId).getModelGroup();
        return group == null ? "" : group.trim();
    }

    public List<String> resolveModelPool(String userId) {
        String selected = getSelectedModelGroup(userId);
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

    public Long getAvatarAiModelId(String userId) {
        return loadConfig(userId).getAvatarAiModelId();
    }

    public String getAvatarAiPrompt(String userId) {
        String value = loadConfig(userId).getAvatarAiPrompt();
        return value == null ? "" : value;
    }

    public String getPersonaSetting(String userId) {
        String value = loadConfig(userId).getPersonaSetting();
        return value == null ? "" : value;
    }

    private AvatarConfig loadConfig(String userId) {
        if (userId == null || userId.isEmpty()) {
            return new AvatarConfig();
        }
        try {
            AvatarConfig config = avatarConfigMapper.findByUserId(userId);
            return config != null ? config : new AvatarConfig();
        } catch (Exception e) {
            logger.error("加载虚拟人配置失败 userId=[{}]", userId, e);
            return new AvatarConfig();
        }
    }
}
