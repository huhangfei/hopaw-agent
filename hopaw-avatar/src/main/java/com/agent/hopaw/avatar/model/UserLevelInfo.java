package com.agent.hopaw.avatar.model;

import com.agent.hopaw.avatar.model.AvatarLevelConfig.LevelThreshold;

public class UserLevelInfo {
    private String userId;
    private int level;
    private String title;
    private long totalTokens;
    private long currentLevelTokens;
    private long nextLevelTokens;
    private int levelProgressPercent;

    public UserLevelInfo() {
    }

    public static UserLevelInfo from(String userId, long totalTokens, AvatarLevelConfig config) {
        UserLevelInfo info = new UserLevelInfo();
        info.userId = userId;
        info.totalTokens = totalTokens;

        LevelThreshold current = config.getLevelByTokens(totalTokens);
        info.level = current.getLevel();
        info.title = current.getTitle();
        info.currentLevelTokens = current.getTokensRequired();

        LevelThreshold next = config.getNextLevel(current.getLevel());
        if (next != null) {
            info.nextLevelTokens = next.getTokensRequired();
            long range = next.getTokensRequired() - current.getTokensRequired();
            long progress = totalTokens - current.getTokensRequired();
            info.levelProgressPercent = range > 0 ? (int) (progress * 100 / range) : 100;
        } else {
            info.nextLevelTokens = current.getTokensRequired();
            info.levelProgressPercent = 100;
        }

        return info;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
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

    public long getCurrentLevelTokens() {
        return currentLevelTokens;
    }

    public void setCurrentLevelTokens(long currentLevelTokens) {
        this.currentLevelTokens = currentLevelTokens;
    }

    public long getNextLevelTokens() {
        return nextLevelTokens;
    }

    public void setNextLevelTokens(long nextLevelTokens) {
        this.nextLevelTokens = nextLevelTokens;
    }

    public int getLevelProgressPercent() {
        return levelProgressPercent;
    }

    public void setLevelProgressPercent(int levelProgressPercent) {
        this.levelProgressPercent = levelProgressPercent;
    }
}