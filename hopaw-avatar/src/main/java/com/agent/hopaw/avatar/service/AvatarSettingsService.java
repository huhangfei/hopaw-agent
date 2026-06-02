package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.model.AvatarModelGroup;
import com.agent.hopaw.avatar.model.AvatarSettings;
import com.agent.hopaw.infra.model.entity.UserConfig;
import com.agent.hopaw.infra.service.IUserConfigService;
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

    private static final String DESC_DISABLED = "是否关闭虚拟人";
    private static final String DESC_MODEL_SETTING = "虚拟人模型设置";
    private static final String DESC_MODEL_GROUP = "虚拟人模型组选择";
    private static final String DESC_PERSONA_SETTING = "虚拟人人设设置";

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

    private final IUserConfigService userConfigService;

    public AvatarSettingsService(IUserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    public AvatarSettings getSettings(String userId) {
        AvatarSettings settings = new AvatarSettings();
        settings.setDisabled(parseBoolean(userConfigService.getValueByKey(userId, KEY_DISABLED, "false")));
        settings.setModelSetting(userConfigService.getValueByKey(userId, KEY_MODEL_SETTING, ""));
        settings.setModelGroup(userConfigService.getValueByKey(userId, KEY_MODEL_GROUP, ""));
        settings.setPersonaSetting(userConfigService.getValueByKey(userId, KEY_PERSONA_SETTING, ""));
        return settings;
    }

    public void saveSettings(String userId, AvatarSettings settings) {
        if (settings == null) {
            return;
        }
        saveConfig(userId, KEY_DISABLED, Boolean.toString(settings.isDisabled()), DESC_DISABLED);
        saveConfig(userId, KEY_MODEL_SETTING, settings.getModelSetting() == null ? "" : settings.getModelSetting(), DESC_MODEL_SETTING);
        saveConfig(userId, KEY_MODEL_GROUP, settings.getModelGroup() == null ? "" : settings.getModelGroup(), DESC_MODEL_GROUP);
        saveConfig(userId, KEY_PERSONA_SETTING, settings.getPersonaSetting() == null ? "" : settings.getPersonaSetting(), DESC_PERSONA_SETTING);
    }

    public boolean isAvatarDisabled(String userId) {
        return parseBoolean(userConfigService.getValueByKey(userId, KEY_DISABLED, "false"));
    }

    public List<AvatarModelGroup> listModelGroups() {
        return DEFAULT_MODEL_GROUPS;
    }

    public String getSelectedModelGroup(String userId) {
        String group = userConfigService.getValueByKey(userId, KEY_MODEL_GROUP, "");
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

    private void saveConfig(String userId, String key, String value, String description) {
        if (userId == null || userId.isEmpty()) {
            logger.warn("跳过保存虚拟人配置，userId 为空 [{}]", key);
            return;
        }
        try {
            UserConfig existing = userConfigService.getByUserIdAndKey(userId, key);
            UserConfig cfg = new UserConfig(userId, key, value, description);
            if (existing == null) {
                userConfigService.insert(cfg);
            } else {
                cfg.setId(existing.getId());
                userConfigService.update(cfg);
            }
        } catch (Exception e) {
            logger.error("保存虚拟人配置失败 userId=[{}] key=[{}]", userId, key, e);
        }
    }

    private boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v);
    }
}
