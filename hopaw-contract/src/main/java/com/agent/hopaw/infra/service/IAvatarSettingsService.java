package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.AvatarSettings;

public interface IAvatarSettingsService {
    String DEFAULT_PERSONA_PROMPT = "你的人格设定是一个温柔的萝莉，喜欢撒娇和卖萌。\n";
    String TOOL_NAME="avatarTool";
    /**
     *
     * @param userId
     * @param agentId
     * @return
     */
    AvatarSettings getSettings(String userId, Long agentId);
}
