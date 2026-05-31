package com.agent.hopaw.infra.event;

import com.agent.hopaw.infra.model.entity.TokenUsage;

public class TokenUsageEvent {

    private final TokenUsage tokenUsage;

    public TokenUsageEvent(TokenUsage tokenUsage) {
        this.tokenUsage = tokenUsage;
    }

    public TokenUsage getTokenUsage() {
        return tokenUsage;
    }

    public String getUserId() {
        return tokenUsage != null ? tokenUsage.getUserId() : null;
    }
}