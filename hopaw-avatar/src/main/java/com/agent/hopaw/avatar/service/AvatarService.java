package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.avatar.model.AvatarAction;
import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.avatar.model.AvatarIntimacyConfig;
import com.agent.hopaw.avatar.model.UserIntimacyInfo;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
import com.agent.hopaw.infra.model.dto.AiTaskStatsMessageInfo;
import com.agent.hopaw.infra.model.dto.AiToolCallMessageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AvatarService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarService.class);

    private final AvatarIntimacyConfig intimacyConfig;
    private final AvatarConfigMapper avatarConfigMapper;
    private final com.agent.hopaw.infra.service.IAgentService agentService;
    private final ApplicationEventPublisher eventPublisher;
    /** userId -> agentId -> AvatarAction */
    private final Map<String, Map<Long, String>> userAgentLastActionCache = new ConcurrentHashMap<>();

    public AvatarService(AvatarIntimacyConfig intimacyConfig,
                         AvatarConfigMapper avatarConfigMapper,
                         com.agent.hopaw.infra.service.IAgentService agentService,
                         ApplicationEventPublisher eventPublisher) {
        this.intimacyConfig = intimacyConfig;
        this.avatarConfigMapper = avatarConfigMapper;
        this.agentService = agentService;
        this.eventPublisher = eventPublisher;
    }

    private void publish(AvatarEvent event) {
        if (event == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            logger.error("Failed to publish avatar event: {}", e.getMessage(), e);
        }
    }

    @EventListener
    public void onTokenUsage(TokenUsageEvent event) {
        String userId = event.getUserId();
        Long agentId = event.getAgentId();
        if (userId == null) {
            return;
        }
        if (agentId == null) {
            return;
        }
        Integer addedTokens = event.getTotalTokens();
        if (addedTokens == null || addedTokens <= 0) {
            return;
        }

        UserIntimacyInfo oldInfo = getUserAgentIntimacyInfo(userId, agentId);
        int oldLevel = oldInfo.getIntimacyLevel();

        AgentAvatarConfig before;
        AgentAvatarConfig after;
        try {
            before = avatarConfigMapper.findByUserAndAgent(userId, agentId);
            if (before == null) {
                AgentAvatarConfig cfg = new AgentAvatarConfig();
                cfg.setUserId(userId);
                cfg.setAgentId(agentId);
                cfg.setDisabled(false);
                cfg.setSoundEnabled(true);
                cfg.setTotalTokens(addedTokens.longValue());
                cfg.setPersonaSetting(AvatarSettingsService.DEFAULT_PERSONA_PROMPT);
                avatarConfigMapper.insert(cfg);
            } else {
                avatarConfigMapper.addTotalTokens(userId, agentId, addedTokens);
            }
            after = avatarConfigMapper.findByUserAndAgent(userId, agentId);
        } catch (Exception e) {
            logger.error("Failed to accumulate tokens for user {} agent {}: {}", userId, agentId, e.getMessage());
            return;
        }
        if (after == null) {
            return;
        }
        long totalTokens = after.getTotalTokens() != null ? after.getTotalTokens().longValue() : 0L;

        UserIntimacyInfo newInfo = UserIntimacyInfo.from(userId, totalTokens, intimacyConfig);

        if (newInfo.getIntimacyLevel() > oldLevel) {
            logger.info("User {} agent {} intimacy up: {} -> {} (title: {}, tokens: {})",
                    userId, agentId, oldLevel, newInfo.getIntimacyLevel(),
                    newInfo.getTitle(), totalTokens);
            AvatarEvent intimacyEvent = AvatarEvent.intimacyUp(userId, agentId, newInfo);
            intimacyEvent.setMessage(AvatarAction.INTIMACY_UP.getRandomPhrase());
            publish(intimacyEvent);
            return;
        }

        if (before == null
                || oldInfo.getTotalTokens() != newInfo.getTotalTokens()
                || oldInfo.getIntimacyLevel() != newInfo.getIntimacyLevel()
                || oldInfo.getProgressPercent() != newInfo.getProgressPercent()) {
            publish(AvatarEvent.intimacyUpdate(userId, agentId, newInfo));
        }
    }

    @EventListener
    public void onAgentMessage(AgentMessageEvent event) {
        String userId = event.getUserId();
        if (userId == null) {
            return;
        }
        Long agentId = event.getAgentId();
        if (agentId == null) {
            return;
        }

        AiMessageBaseInfo message = event.getMessage();
        if (message == null) {
            return;
        }
        // 执行统计消息不驱动虚拟人动作（否则会立刻打断 task-done 的庆祝状态）
        if (AiTaskStatsMessageInfo.TYPE_TASK_STATS.equals(message.getType())) {
            return;
        }

        AvatarAction action = AvatarAction.fromMessageType(message.getType());
        Map<Long, String> lastActions = userAgentLastActionCache
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>(5));
        String lastActionCode = lastActions.get(agentId);

        String currentActionCode = action.getCode();
        String phrase="";
        if(action.equals(AvatarAction.TOOL_EXECUTING) && message instanceof AiToolCallMessageInfo){
            AiToolCallMessageInfo toolCallMessageInfo=(AiToolCallMessageInfo)message;
            if(toolCallMessageInfo.getStatus().equals(AiToolCallMessageInfo.STATUS_STARTING))
                currentActionCode+=toolCallMessageInfo.getToolName()+toolCallMessageInfo.getStatus();
            if (currentActionCode.equals(lastActionCode)) {
                return;
            }
            List<String> toolDescriptions = toolCallMessageInfo.getToolDescriptions();
            if(toolDescriptions != null && !toolDescriptions.isEmpty()){
                String desc = toolCallMessageInfo.getToolDescriptions().get(0);
                if(AiToolCallMessageInfo.STATUS_APPROVAL.equals(toolCallMessageInfo.getStatus())){
                    phrase="我可以使用"+desc+"吗？";
                }else if(AiToolCallMessageInfo.STATUS_REJECTED.equals(toolCallMessageInfo.getStatus())){
                    phrase="那我就不用"+desc+"了！";
                }else if(AiToolCallMessageInfo.STATUS_STARTING.equals(toolCallMessageInfo.getStatus())){
                    phrase="让我执行"+desc+"看看";
                }
//                else if(AiToolCallMessageInfo.STATUS_PREPARING.equals(toolCallMessageInfo.getStatus())){
//                    phrase="让我准备一下工具 "+desc;
//                }
//                else if(AiToolCallMessageInfo.STATUS_RUNNING.equals(toolCallMessageInfo.getStatus())){
//                    phrase=desc+"运行很顺畅啊😊";
//                }
                else if(AiToolCallMessageInfo.STATUS_FAILED.equals(toolCallMessageInfo.getStatus())){
                    phrase="看来"+desc+"无法运行了😭";
                }else if(AiToolCallMessageInfo.STATUS_EXECUTED.equals(toolCallMessageInfo.getStatus())){
                    phrase="看来"+desc+"已经运行完毕😊";
                }else{
                    return;
                }
            }else{
                phrase = action.getRandomPhrase();
            }
        }else{
            phrase = action.getRandomPhrase();
        }
        if (phrase == null || phrase.isEmpty()) {
            phrase = action.getDescription();
        }
        if (currentActionCode.equals(lastActionCode)) {
            return;
        }
        lastActions.put(agentId, currentActionCode);
        AvatarEvent avatarEvent = AvatarEvent.action(userId, agentId, message.getSessionId(), action, phrase);
        publish(avatarEvent);
    }

    public UserIntimacyInfo getUserIntimacyInfo(String userId) {
        long totalTokens = 0L;
        try {
            List<AgentAvatarConfig> configs = avatarConfigMapper.findByUserId(userId);
            if (configs != null) {
                for (AgentAvatarConfig cfg : configs) {
                    if (cfg.getTotalTokens() != null) {
                        totalTokens += cfg.getTotalTokens();
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load avatar configs for user {}: {}", userId, e.getMessage());
        }
        return UserIntimacyInfo.from(userId, totalTokens, intimacyConfig);
    }

    public UserIntimacyInfo getUserAgentIntimacyInfo(String userId, Long agentId) {
        if (userId == null || agentId == null) {
            return UserIntimacyInfo.from(userId == null ? "" : userId, 0L, intimacyConfig);
        }
        long totalTokens = loadTotalTokens(userId, agentId);
        return UserIntimacyInfo.from(userId, totalTokens, intimacyConfig);
    }

    public Map<String, UserIntimacyInfo> getAllUserIntimacies() {
        Map<String, UserIntimacyInfo> result = new LinkedHashMap<>();
        try {
            List<AgentAvatarConfig> configs = avatarConfigMapper.findAll();
            for (AgentAvatarConfig config : configs) {
                if (config.getUserId() == null) {
                    continue;
                }
                long totalTokens = config.getTotalTokens() != null ? config.getTotalTokens().longValue() : 0L;
                result.put(config.getUserId(), UserIntimacyInfo.from(config.getUserId(), totalTokens, intimacyConfig));
            }
        } catch (Exception e) {
            logger.error("Failed to load all avatar configs: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 获取全部虚拟人排行数据（按 totalTokens 降序），每条包含 userId、agentId、level、title、totalTokens。
     */
    public java.util.List<java.util.Map<String, Object>> getAvatarRankings() {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        try {
            List<AgentAvatarConfig> configs = avatarConfigMapper.findAll();
            for (AgentAvatarConfig config : configs) {
                if (config.getUserId() == null || config.getAgentId() == null) {
                    continue;
                }
                long totalTokens = config.getTotalTokens() != null ? config.getTotalTokens().longValue() : 0L;
                UserIntimacyInfo info = UserIntimacyInfo.from(config.getUserId(), totalTokens, intimacyConfig);
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("userId", config.getUserId());
                row.put("agentId", config.getAgentId());
                String agentName = "";
                String agentAvatar = "";
                try {
                    com.agent.hopaw.infra.model.entity.Agent agent = agentService.getAgentById(config.getAgentId());
                    if (agent != null) {
                        agentName = agent.getName();
                        agentAvatar = agent.getAvatar() != null ? agent.getAvatar() : "";
                    }
                } catch (Exception ignored) {}
                row.put("agentName", agentName);
                row.put("agentAvatar", agentAvatar);
                row.put("level", info.getIntimacyLevel());
                row.put("title", info.getTitle());
                row.put("totalTokens", totalTokens);
                list.add(row);
            }
            list.sort((a, b) -> Long.compare((long) b.get("totalTokens"), (long) a.get("totalTokens")));
        } catch (Exception e) {
            logger.error("Failed to load avatar rankings: {}", e.getMessage());
        }
        return list;
    }

    private long loadTotalTokens(String userId, Long agentId) {
        if (userId == null || userId.isEmpty() || agentId == null) {
            return 0L;
        }
        try {
            AgentAvatarConfig config = avatarConfigMapper.findByUserAndAgent(userId, agentId);
            if (config != null && config.getTotalTokens() != null) {
                return config.getTotalTokens().longValue();
            }
        } catch (Exception e) {
            logger.warn("Failed to load avatar config for user {} agent {}: {}", userId, agentId, e.getMessage());
        }
        return 0L;
    }
}
