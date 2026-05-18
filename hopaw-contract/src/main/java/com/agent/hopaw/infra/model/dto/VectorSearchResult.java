package com.agent.hopaw.infra.model.dto;

public class VectorSearchResult {
    private String embeddingId;
    private double score;
    private String text;
    private String agentId;
    private String userId;
    private String memoryType;

    public VectorSearchResult() {}

    public VectorSearchResult(String embeddingId, double score, String text,
                              String agentId, String userId, String memoryType) {
        this.embeddingId = embeddingId;
        this.score = score;
        this.text = text;
        this.agentId = agentId;
        this.userId = userId;
        this.memoryType = memoryType;
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
}
