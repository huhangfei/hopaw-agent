package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.model.AvatarEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Service
public class AvatarProactiveMessageService {

    private static final Logger logger = LoggerFactory.getLogger(AvatarProactiveMessageService.class);

    private final Map<String, Consumer<AvatarEvent>> listeners = new ConcurrentHashMap<>();

    public void registerListener(String id, Consumer<AvatarEvent> listener) {
        if (id == null || listener == null) {
            return;
        }
        listeners.put(id, listener);
        logger.debug("Avatar proactive message listener registered: {}", id);
    }

    public void removeListener(String id) {
        if (id == null) {
            return;
        }
        listeners.remove(id);
    }

    public boolean sendProactiveMessage(String userId, String message) {
        if (userId == null || userId.isEmpty()) {
            logger.warn("Skip sending proactive avatar message: missing userId");
            return false;
        }
        if (message == null || message.isBlank()) {
            logger.debug("Skip sending proactive avatar message to user {}: empty message", userId);
            return false;
        }
        AvatarEvent event = AvatarEvent.proactiveMessage(userId, message.trim());
        dispatch(event);
        return true;
    }

    private void dispatch(AvatarEvent event) {
        for (Map.Entry<String, Consumer<AvatarEvent>> entry : listeners.entrySet()) {
            try {
                entry.getValue().accept(event);
            } catch (Exception e) {
                logger.error("Failed to dispatch proactive avatar event via listener [{}]: {}",
                        entry.getKey(), e.getMessage(), e);
            }
        }
    }
}
