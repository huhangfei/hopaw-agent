package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.model.AvatarAction;
import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.avatar.model.AvatarIntimacyConfig;
import com.agent.hopaw.avatar.model.UserIntimacyInfo;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.mapper.AvatarConfigMapper;
import com.agent.hopaw.infra.mapper.TokenUsageMapper;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
import com.agent.hopaw.infra.model.entity.AvatarConfig;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class AvatarService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarService.class);

    private final AvatarIntimacyConfig intimacyConfig;
    private final AvatarConfigMapper avatarConfigMapper;
    private final Map<String, AvatarAction> userLastActionCache = new ConcurrentHashMap<>();
    private final List<Consumer<AvatarEvent>> listeners = new ArrayList<>();

    public AvatarService(AvatarIntimacyConfig intimacyConfig,
                         AvatarConfigMapper avatarConfigMapper) {
        this.intimacyConfig = intimacyConfig;
        this.avatarConfigMapper = avatarConfigMapper;
    }

    public void registerListener(Consumer<AvatarEvent> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<AvatarEvent> listener) {
        listeners.remove(listener);
    }

    private void broadcast(AvatarEvent event) {
        for (Consumer<AvatarEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                logger.error("Failed to broadcast avatar event: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void onTokenUsage(TokenUsageEvent event) {
        String userId = event.getUserId();
        if (userId == null) {
            return;
        }
        Integer addedTokens = event.getTotalTokens();
        if (addedTokens == null || addedTokens <= 0) {
            return;
        }

        UserIntimacyInfo oldInfo = getUserIntimacyInfo(userId);
        int oldLevel = oldInfo.getIntimacyLevel();

        AvatarConfig before;
        AvatarConfig after;
        try {
            before = avatarConfigMapper.findByUserId(userId);
            if (before == null) {
                AvatarConfig cfg = new AvatarConfig();
                cfg.setUserId(userId);
                cfg.setDisabled(false);
                cfg.setTotalTokens(addedTokens.longValue());
                avatarConfigMapper.insert(cfg);
            } else {
                avatarConfigMapper.addTotalTokens(userId, addedTokens);
            }
            after = avatarConfigMapper.findByUserId(userId);
        } catch (Exception e) {
            logger.error("Failed to accumulate tokens for user {}: {}", userId, e.getMessage());
            return;
        }
        if (after == null) {
            return;
        }
        long totalTokens = after.getTotalTokens() != null ? after.getTotalTokens().longValue() : 0L;

        UserIntimacyInfo newInfo = UserIntimacyInfo.from(userId, totalTokens, intimacyConfig);

        if (newInfo.getIntimacyLevel() > oldLevel) {
            logger.info("User {} intimacy up: {} -> {} (title: {}, tokens: {})",
                    userId, oldLevel, newInfo.getIntimacyLevel(),
                    newInfo.getTitle(), totalTokens);
            AvatarEvent intimacyEvent = AvatarEvent.intimacyUp(userId, newInfo);
            intimacyEvent.setMessage(AvatarAction.INTIMACY_UP.getRandomPhrase());
            broadcast(intimacyEvent);
            return;
        }

        if (before == null
                || oldInfo.getTotalTokens() != newInfo.getTotalTokens()
                || oldInfo.getIntimacyLevel() != newInfo.getIntimacyLevel()
                || oldInfo.getProgressPercent() != newInfo.getProgressPercent()) {
            broadcast(AvatarEvent.intimacyUpdate(userId, newInfo));
        }
    }

    @EventListener
    public void onAgentMessage(AgentMessageEvent event) {
        String userId = event.getUserId();
        if (userId == null) {
            return;
        }

        AiMessageBaseInfo message = event.getMessage();
        if (message == null) {
            return;
        }

        AvatarAction action = AvatarAction.fromMessageType(message.getType());
        AvatarAction lastAction = userLastActionCache.get(userId);
        if (action == lastAction) {
            return;
        }
        userLastActionCache.put(userId, action);

        String phrase = action.getRandomPhrase();
        if (phrase == null || phrase.isEmpty()) {
            phrase = action.getDescription();
        }
        AvatarEvent avatarEvent = AvatarEvent.action(userId, action, phrase);
        broadcast(avatarEvent);
    }

    public UserIntimacyInfo getUserIntimacyInfo(String userId) {
        long totalTokens = loadTotalTokens(userId);
        return UserIntimacyInfo.from(userId, totalTokens, intimacyConfig);
    }

    public Map<String, UserIntimacyInfo> getAllUserIntimacies() {
        Map<String, UserIntimacyInfo> result = new LinkedHashMap<>();
        try {
            List<AvatarConfig> configs = avatarConfigMapper.findAll();
            for (AvatarConfig config : configs) {
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

    private long loadTotalTokens(String userId) {
        if (userId == null || userId.isEmpty()) {
            return 0L;
        }
        try {
            AvatarConfig config = avatarConfigMapper.findByUserId(userId);
            if (config != null && config.getTotalTokens() != null) {
                return config.getTotalTokens().longValue();
            }
        } catch (Exception e) {
            logger.warn("Failed to load avatar config for user {}: {}", userId, e.getMessage());
        }
        return 0L;
    }
}
