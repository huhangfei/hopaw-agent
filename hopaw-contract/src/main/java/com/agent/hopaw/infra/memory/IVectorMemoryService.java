package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.VectorMemoryTypeEnum;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;

import java.util.List;

public interface IVectorMemoryService {

    void store(String content, Long agentId, String userId, VectorMemoryTypeEnum memoryType);

    void storeBatch(List<String> contents, Long agentId, String userId, VectorMemoryTypeEnum memoryType);

    List<VectorSearchResult> search(String query, Long agentId, String userId,
                                    VectorMemoryTypeEnum memoryType, int maxResults, double minScore);

    boolean deleteByEmbeddingId(String embeddingId);
}
