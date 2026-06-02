package com.agent.hopaw.infra.event;

import java.util.Collections;
import java.util.Set;

public class UserConfigChangeEvent {

    private final String userId;
    private final Set<String> changedKeys;

    public UserConfigChangeEvent(String userId, Set<String> changedKeys) {
        this.userId = userId;
        this.changedKeys = Collections.unmodifiableSet(changedKeys);
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getChangedKeys() {
        return changedKeys;
    }
}
