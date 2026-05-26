package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.VectorMemoryTypeEnum;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;

import java.time.LocalDateTime;
import java.util.List;

public interface IVectorMemoryService {

    void store(String content, String sessionId, Long agentId, String userId, String memoryType, LocalDateTime timestamp);

    void storeBatch(List<String> contents,String sessionId, Long agentId, String userId, String memoryType, LocalDateTime timestamp);

    List<VectorSearchResult> search(String query,String sessionId, Long agentId, String userId,
                                    String memoryType, int maxResults, double minScore);

    boolean deleteByEmbeddingId(String embeddingId);
}
