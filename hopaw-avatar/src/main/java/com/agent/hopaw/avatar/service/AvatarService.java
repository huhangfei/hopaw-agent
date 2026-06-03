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
    private final ApplicationEventPublisher eventPublisher;
    /** userId -> agentId -> AvatarAction */
    private final Map<String, Map<Long, AvatarAction>> userAgentLastActionCache = new ConcurrentHashMap<>();

    public AvatarService(AvatarIntimacyConfig intimacyConfig,
                         AvatarConfigMapper avatarConfigMapper,
                         ApplicationEventPublisher eventPublisher) {
        this.intimacyConfig = intimacyConfig;
        this.avatarConfigMapper = avatarConfigMapper;
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

        AvatarAction action = AvatarAction.fromMessageType(message.getType());
        Map<Long, AvatarAction> lastActions = userAgentLastActionCache
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>());
        AvatarAction lastAction = lastActions.get(agentId);
        if (action == lastAction) {
            return;
        }
        lastActions.put(agentId, action);

        String phrase = action.getRandomPhrase();
        if (phrase == null || phrase.isEmpty()) {
            phrase = action.getDescription();
        }
        AvatarEvent avatarEvent = AvatarEvent.action(userId, agentId, action, phrase);
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
