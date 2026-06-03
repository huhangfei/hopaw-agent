package com.agent.hopaw.avatar.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "avatar.intimacy")
public class AvatarIntimacyConfig {

    private List<IntimacyThreshold> thresholds = new ArrayList<>();

    public AvatarIntimacyConfig() {
        thresholds.add(new IntimacyThreshold(0, 0, "陌生人"));
        thresholds.add(new IntimacyThreshold(1, 1000, "初相识"));
        thresholds.add(new IntimacyThreshold(2, 5000, "老熟人"));
        thresholds.add(new IntimacyThreshold(3, 20000, "好朋友"));
        thresholds.add(new IntimacyThreshold(4, 100000, "亲密伙伴"));
        thresholds.add(new IntimacyThreshold(5, 500000, "挚友"));
        thresholds.add(new IntimacyThreshold(6, 2000000, "知心人"));
        thresholds.add(new IntimacyThreshold(7, 10000000, "灵魂伴侣"));
        thresholds.add(new IntimacyThreshold(8, 50000000, "唯一知己"));
        thresholds.add(new IntimacyThreshold(9, 200000000, "永恒之约"));
    }

    public List<IntimacyThreshold> getThresholds() {
        return thresholds;
    }

    public void setThresholds(List<IntimacyThreshold> thresholds) {
        this.thresholds = thresholds;
    }

    public IntimacyThreshold getByTokens(long totalTokens) {
        IntimacyThreshold result = thresholds.get(0);
        for (IntimacyThreshold threshold : thresholds) {
            if (totalTokens >= threshold.getTokensRequired()) {
                result = threshold;
            } else {
                break;
            }
        }
        return result;
    }

    public IntimacyThreshold getNext(int currentLevel) {
        int nextIndex = currentLevel + 1;
        if (nextIndex >= 0 && nextIndex < thresholds.size()) {
            return thresholds.get(nextIndex);
        }
        return null;
    }

    public static class IntimacyThreshold {
        private int level;
        private long tokensRequired;
        private String title;

        public IntimacyThreshold() {
        }

        public IntimacyThreshold(int level, long tokensRequired, String title) {
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
