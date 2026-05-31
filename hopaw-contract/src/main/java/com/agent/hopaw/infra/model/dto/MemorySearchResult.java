package com.agent.hopaw.infra.model.dto;

public class MemorySearchResult {
    private double score;
    private String text;
    private String sessionId;
    private String userId;
    private String memoryType;
    private String memoryDate;
    public MemorySearchResult() {}


    public MemorySearchResult(double score, String text, String sessionId, String userId, String memoryType, String memoryDate) {
        this.score = score;
        this.text = text;
        this.sessionId = sessionId;
        this.userId = userId;
        this.memoryType = memoryType;
        this.memoryDate = memoryDate;
    }


    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
 public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getMemoryType() { return memoryType; }
    public void setMemoryType(String memoryType) { this.memoryType = memoryType; }

    public String getMemoryDate() {
        return memoryDate;
    }

    public void setMemoryDate(String memoryDate) {
        this.memoryDate = memoryDate;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
