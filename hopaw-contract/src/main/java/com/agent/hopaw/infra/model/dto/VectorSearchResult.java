package com.agent.hopaw.infra.model.dto;

public class VectorSearchResult {
    private String embeddingId;
    private double score;
    private String text;
    private String agentId;
    private String sessionId;
    private String userId;
    private String memoryType;
    private String memoryDate;

    public VectorSearchResult() {}

    public VectorSearchResult(String embeddingId, double score, String text,
                              String sessionId,
                              String agentId, String userId, String memoryType, String memoryDate) {
        this.embeddingId = embeddingId;
        this.score = score;
        this.text = text;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.userId = userId;
        this.memoryType = memoryType;
        this.memoryDate = memoryDate;

    }

    public String getEmbeddingId() { return embeddingId; }
    public void setEmbeddingId(String embeddingId) { this.embeddingId = embeddingId; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

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
