package com.agent.hopaw.avatar.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "avatar.level")
public class AvatarLevelConfig {

    private List<LevelThreshold> thresholds = new ArrayList<>();

    public AvatarLevelConfig() {
        thresholds.add(new LevelThreshold(0, 0, "新手"));
        thresholds.add(new LevelThreshold(1, 1000, "学徒"));
        thresholds.add(new LevelThreshold(2, 5000, "探索者"));
        thresholds.add(new LevelThreshold(3, 20000, "专家"));
        thresholds.add(new LevelThreshold(4, 100000, "大师"));
        thresholds.add(new LevelThreshold(5, 500000, "宗师"));
        thresholds.add(new LevelThreshold(6, 2000000, "传奇"));
        thresholds.add(new LevelThreshold(7, 10000000, "超凡"));
        thresholds.add(new LevelThreshold(8, 50000000, "神话"));
        thresholds.add(new LevelThreshold(9, 200000000, "终极"));
    }

    public List<LevelThreshold> getThresholds() {
        return thresholds;
    }

    public void setThresholds(List<LevelThreshold> thresholds) {
        this.thresholds = thresholds;
    }

    public LevelThreshold getLevelByTokens(long totalTokens) {
        LevelThreshold result = thresholds.get(0);
        for (LevelThreshold threshold : thresholds) {
            if (totalTokens >= threshold.getTokensRequired()) {
                result = threshold;
            } else {
                break;
            }
        }
        return result;
    }

    public LevelThreshold getNextLevel(int currentLevel) {
        int nextIndex = currentLevel + 1;
        if (nextIndex < thresholds.size()) {
            return thresholds.get(nextIndex);
        }
        return null;
    }

    public static class LevelThreshold {
        private int level;
        private long tokensRequired;
        private String title;

        public LevelThreshold() {
        }

        public LevelThreshold(int level, long tokensRequired, String title) {
            this.level = level;
            this.tokensRequired = tokensRequired;
            this.title = title;
        }

        public int getLevel() {
            return level;
        }

        public void setLevel(int level) {
            this.level = level;
        }

        public long getTokensRequired() {
            return tokensRequired;
        }

        public void setTokensRequired(long tokensRequired) {
            this.tokensRequired = tokensRequired;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}