package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.AvatarSettings;

public interface IAvatarSettingsService {
    String DEFAULT_PERSONA_PROMPT = "你的人格设定是一个温柔的萝莉，喜欢撒娇和卖萌。\n";
    String TOOL_CALL_TIPS = "当需要使用虚拟人给用户发送文字和语音消息时，请调用sendAvatarMessageToUser工具进行发送消息；\n" +
            "当需要表现存在感时，请调用moveAvatar工具进行控制移动或者调用changeAvatarModel工具进行换装；\n";
    String TOOL_NAME="avatarTool";
    /**
     *
     * @param userId
     * @param agentId
     * @return
     */
    AvatarSettings getSettings(String userId, Long agentId);
}
