package com.agent.hopaw.infra.event;

import java.util.Collections;
import java.util.Set;

public class ConfigChangeEvent {

    private final Set<String> changedKeys;

    public ConfigChangeEvent(Set<String> changedKeys) {
        this.changedKeys = Collections.unmodifiableSet(changedKeys);
    }

    public Set<String> getChangedKeys() {
        return changedKeys;
    }
}