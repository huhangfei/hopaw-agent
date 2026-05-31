package com.agent.hopaw.infra.model.dto;

public class VectorSearchResult  extends MemorySearchResult{

    private String embeddingId;


    private String memoryTypeName;

    public VectorSearchResult() {}

    public VectorSearchResult(String embeddingId) {
        this.embeddingId = embeddingId;
    }

    public VectorSearchResult(double score, String text, String sessionId, String userId, String memoryType, String memoryDate, String embeddingId) {
        super(score, text, sessionId, userId, memoryType, memoryDate);
        this.embeddingId = embeddingId;
    }


    public String getEmbeddingId() { return embeddingId; }
    public void setEmbeddingId(String embeddingId) { this.embeddingId = embeddingId; }

    public String getMemoryTypeName() { return memoryTypeName; }
    public void setMemoryTypeName(String memoryTypeName) { this.memoryTypeName = memoryTypeName; }
}
