package com.agent.hopaw.avatar.model;

import com.agent.hopaw.avatar.model.AvatarIntimacyConfig.IntimacyThreshold;

public class UserIntimacyInfo {
    private String userId;
    private int intimacyLevel;
    private String title;
    private long totalTokens;
    private long currentIntimacyTokens;
    private long nextIntimacyTokens;
    private int progressPercent;

    public UserIntimacyInfo() {
    }

    public static UserIntimacyInfo from(String userId, long totalTokens, AvatarIntimacyConfig config) {
        UserIntimacyInfo info = new UserIntimacyInfo();
        info.userId = userId;
        info.totalTokens = totalTokens;

        IntimacyThreshold current = config.getByTokens(totalTokens);
        info.intimacyLevel = current.getLevel();
        info.title = current.getTitle();
        info.currentIntimacyTokens = current.getTokensRequired();

        IntimacyThreshold next = config.getNext(current.getLevel());
        if (next != null) {
            info.nextIntimacyTokens = next.getTokensRequired();
            long range = next.getTokensRequired() - current.getTokensRequired();
            long progress = totalTokens - current.getTokensRequired();
            info.progressPercent = range > 0 ? (int) (progress * 100 / range) : 100;
        } else {
            info.nextIntimacyTokens = current.getTokensRequired();
            info.progressPercent = 100;
        }

        return info;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getIntimacyLevel() {
        return intimacyLevel;
    }

    public void setIntimacyLevel(int intimacyLevel) {
        this.intimacyLevel = intimacyLevel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public long getCurrentIntimacyTokens() {
        return currentIntimacyTokens;
    }

    public void setCurrentIntimacyTokens(long currentIntimacyTokens) {
        this.currentIntimacyTokens = currentIntimacyTokens;
    }

    public long getNextIntimacyTokens() {
        return nextIntimacyTokens;
    }

    public void setNextIntimacyTokens(long nextIntimacyTokens) {
        this.nextIntimacyTokens = nextIntimacyTokens;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = progressPercent;
    }
}
