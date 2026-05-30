package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;

import java.time.LocalDateTime;
import java.util.List;

public interface IVectorMemoryService {

    String store(String content, String sessionId, String userId, UserMemoryTypeEnum memoryType, LocalDateTime timestamp);

    void storeBatch(List<String> contents, String sessionId, String userId, UserMemoryTypeEnum memoryType, LocalDateTime timestamp);

    /**
     * 语义检索向量库中的记忆
     *
     * @param query     用户查询文本
     * @param userId    用户ID（可选过滤），传 null 表示不过滤
     * @param memoryType 记忆类型，传 null 表示不过滤
     * @param maxResults 最大返回数
     * @param minScore   最低相似度阈值
     * @param excludeMemoryTypes   排除的记忆类型
     */
    List<VectorSearchResult> search(String query,String sessionId, String userId,
                                    String memoryType, int maxResults, double minScore,UserMemoryTypeEnum... excludeMemoryTypes);

    boolean deleteByEmbeddingId(String embeddingId);
}
