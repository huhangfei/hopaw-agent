package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.model.AvatarAction;
import com.agent.hopaw.avatar.model.AvatarEvent;
import com.agent.hopaw.avatar.model.AvatarLevelConfig;
import com.agent.hopaw.avatar.model.UserLevelInfo;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.mapper.TokenUsageMapper;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class AvatarService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarService.class);

    private final AvatarLevelConfig levelConfig;
    private final TokenUsageMapper tokenUsageMapper;
    private final Map<String, UserLevelInfo> userLevelCache = new ConcurrentHashMap<>();
    private final List<Consumer<AvatarEvent>> listeners = new ArrayList<>();

    public AvatarService(AvatarLevelConfig levelConfig, TokenUsageMapper tokenUsageMapper) {
        this.levelConfig = levelConfig;
        this.tokenUsageMapper = tokenUsageMapper;
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

        UserLevelInfo oldLevelInfo = userLevelCache.get(userId);
        int oldLevel = oldLevelInfo != null ? oldLevelInfo.getLevel() : 0;

        TokenUsage summary = tokenUsageMapper.summaryByUserId(userId);
        long totalTokens = summary != null && summary.getTotalTokens() != null ? summary.getTotalTokens().longValue() : 0;

        UserLevelInfo newLevelInfo = UserLevelInfo.from(userId, totalTokens, levelConfig);
        userLevelCache.put(userId, newLevelInfo);

        if (oldLevelInfo == null || newLevelInfo.getLevel() > oldLevel) {
            logger.info("User {} leveled up: {} -> {} (title: {}, tokens: {})",
                    userId, oldLevel, newLevelInfo.getLevel(),
                    newLevelInfo.getTitle(), totalTokens);
            broadcast(AvatarEvent.levelUp(userId, newLevelInfo));
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
        AvatarEvent avatarEvent = AvatarEvent.action(userId, action);
        broadcast(avatarEvent);
    }

    public UserLevelInfo getUserLevelInfo(String userId) {
        UserLevelInfo cached = userLevelCache.get(userId);
        if (cached != null) {
            return cached;
        }

        TokenUsage summary = tokenUsageMapper.summaryByUserId(userId);
        long totalTokens = summary != null && summary.getTotalTokens() != null ? summary.getTotalTokens().longValue() : 0;
        UserLevelInfo info = UserLevelInfo.from(userId, totalTokens, levelConfig);
        userLevelCache.put(userId, info);
        return info;
    }

    public Map<String, UserLevelInfo> getAllUserLevels() {
        return Map.copyOf(userLevelCache);
    }
}