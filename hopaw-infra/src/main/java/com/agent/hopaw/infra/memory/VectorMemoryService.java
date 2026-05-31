package com.agent.hopaw.infra.memory;

import com.agent.hopaw.infra.constant.UserMemoryTypeEnum;
import com.agent.hopaw.infra.model.dto.VectorSearchResult;
import com.agent.hopaw.infra.service.SysConfigService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.community.store.embedding.jvector.JVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class VectorMemoryService implements IVectorMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(IVectorMemoryService.class);
    private static final String METADATA_SESSION_ID = "sessionId";
    private static final String METADATA_USER_ID = "userId";
    private static final String METADATA_MEMORY_TYPE = "memoryType";
    private static final String METADATA_MEMORY_DATE = "memoryDate";
    private static final String METADATA_MEMORY_ID = "memoryId";

    private final SysConfigService sysConfigService;
    private final EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    public VectorMemoryService(SysConfigService sysConfigService, EmbeddingModel embeddingModel) {
        this.sysConfigService = sysConfigService;
        this.embeddingModel = embeddingModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            int dimension = 512;

            String persistencePath = sysConfigService.getValueByKey("vector_store_path", "./vector_store");
            String profile = sysConfigService.getValueByKey("vector_store_profile", "stable");

            /*
             * JVector 基于融合 DiskANN + HNSW 算法的纯 Java 向量检索引擎。
             * 核心原理：构建分层可导航小世界图 (HNSW)，
             * 高层稀疏快速跳过不相关区域，底层密集精确定位近邻。
             *
             * ====== 三档预设 (通过 vector_store_profile 配置切换) ======
             *
             * 【precision - 精准模式】适用场景：对召回率要求极高，可接受较长构建时间。
             *   maxDegree=32  beamWidth=300  neighborOverflow=1.5  alpha=1.5
             *   特点：高连接度 + 高构建质量 + 高多样性 → 召回率最优
             *   代价：内存 ~2x，构建耗时 ~3x
             *
             * 【stable - 稳定模式】(默认) 适用场景：十万~百万级向量，均衡精度与性能。
             *   maxDegree=16  beamWidth=100  neighborOverflow=1.2  alpha=1.2
             *   特点：标准参数，业界推荐默认值
             *
             * 【speed - 极速模式】适用场景：数据量小或对构建速度和内存敏感。
             *   maxDegree=8   beamWidth=50   neighborOverflow=1.0  alpha=1.0
             *   特点：低连接度 + 快速构建 + 省内存 → 构建/加载最快
             *   代价：召回率下降约 5~15%
             *
             * ====== 参数含义 ======
             * maxDegree        : 每节点最大边数，影响召回率与内存
             * beamWidth        : 构建束宽，影响索引质量（不影响在线查询）
             * neighborOverflow : 候选邻居溢出系数，1.2=内存优先, 1.5=磁盘优先
             * alpha            : 边多样性 (DiskANN)，1.2=高维推荐, 2.0=低维推荐
             */

            int maxDegree, beamWidth;
            float neighborOverflow, alpha;

            switch (profile.toLowerCase()) {
                case "precision":
                    maxDegree = 32; beamWidth = 300; neighborOverflow = 1.5f; alpha = 1.5f; break;
                case "speed":
                    maxDegree = 8;  beamWidth = 50;  neighborOverflow = 1.0f; alpha = 1.0f; break;
                case "stable": default:
                    maxDegree = 16; beamWidth = 100; neighborOverflow = 1.2f; alpha = 1.2f; break;
            }

            embeddingStore = (JVectorEmbeddingStore) JVectorEmbeddingStore.builder()
                    .dimension(dimension)
                    .persistencePath(persistencePath)
                    .maxDegree(maxDegree)
                    .beamWidth(beamWidth)
                    .neighborOverflow(neighborOverflow)
                    .alpha(alpha)
                    .build();

            logger.info("JVectorEmbeddingStore initialized, profile={}, dimension={}, maxDegree={}, beamWidth={}, path={}",
                    profile, dimension, maxDegree, beamWidth, persistencePath);
        } catch (Exception e) {
            logger.error("Failed to initialize JVectorEmbeddingStore", e);
            throw new RuntimeException("Vector store initialization failed", e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (embeddingStore instanceof JVectorEmbeddingStore) {
               saveVectorStore ((JVectorEmbeddingStore) embeddingStore);
                logger.info("JVectorEmbeddingStore saved on shutdown");
            }
        } catch (Exception e) {
            logger.error("Failed to save vector store on shutdown", e);
        }
    }

    /**
     * 将内容写入向量库，附带 agent、用户、记忆类型、记忆ID 等分类信息
     */
    @Override
    public String store(String content, String sessionId,String userId, UserMemoryTypeEnum memoryType, LocalDateTime timestamp) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            TextSegment segment = TextSegment.from(content);
            segment.metadata().put(METADATA_SESSION_ID, sessionId);
            segment.metadata().put(METADATA_USER_ID, userId);
            segment.metadata().put(METADATA_MEMORY_TYPE, memoryType.getCode());
            segment.metadata().put(METADATA_MEMORY_DATE, timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            Embedding embedding = embeddingModel.embed(segment).content();
            String id= embeddingStore.add(embedding, segment);
            saveVectorStore((JVectorEmbeddingStore) embeddingStore);
            return id;
        } catch (Exception e) {
            logger.error("Failed to store vector memory, sessionId={}, userId={}, type={}",
                    sessionId, userId, memoryType, e);
        }
        return null;
    }

    /**
     * 批量写入向量库
     */
    @Override
    public void storeBatch(List<String> contents, String sessionId, String userId, UserMemoryTypeEnum memoryType, LocalDateTime timestamp) {
        if (contents == null || contents.isEmpty()) {
            return;
        }
        try {
            List<TextSegment> segments = new java.util.ArrayList<>();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (String content : contents) {
                if (content == null || content.isBlank()) {
                    continue;
                }
                TextSegment segment = TextSegment.from(content);
                segment.metadata().put(METADATA_SESSION_ID, sessionId);
                segment.metadata().put(METADATA_USER_ID, userId);
                segment.metadata().put(METADATA_MEMORY_TYPE, memoryType.getCode());
                segment.metadata().put(METADATA_MEMORY_DATE, timestamp.format(formatter));

                segments.add(segment);
            }

            if (segments.isEmpty()) {
                return;
            }

            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);

            saveVectorStore((JVectorEmbeddingStore) embeddingStore);

            logger.info("Batch stored {} vectors, sessionId={}, userId={}, type={}",
                    segments.size(), sessionId, userId, memoryType);
        } catch (Exception e) {
            logger.error("Failed to batch store vectors, sessionId={}, userId={}, type={}",
                    sessionId, userId, memoryType, e);
        }
    }

    /**
     * 安全地保存向量存储到磁盘。
     * JVector 不允许保存空的存储库，因此需要捕获此异常。
     */
    private void saveVectorStore(JVectorEmbeddingStore store) {
        try {
            store.save();
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("empty embedding store")) {
                logger.debug("Skipping save of empty embedding store");
            } else {
                throw e;
            }
        }
    }

    /**
     * 根据 embeddingId 删除向量库中的单条记录
     */
    @Override
    public boolean deleteByEmbeddingId(String embeddingId) {
        if (embeddingId == null || embeddingId.isBlank()) {
            return false;
        }
        try {
            embeddingStore.remove(embeddingId);
            saveVectorStore((JVectorEmbeddingStore) embeddingStore);
            logger.info("Deleted vector, embeddingId={}", embeddingId);
            return true;
        } catch (Exception e) {
            logger.error("Failed to delete vector, embeddingId={}", embeddingId, e);
            return false;
        }
    }

    /**
     * 语义检索向量库中的历史记忆
     *
     * @param query     用户查询文本
     * @param sessionId    会话ID（可选过滤），传 null 表示不过滤
     * @param userId    用户ID（可选过滤），传 null 表示不过滤
     * @param memoryType 记忆类型，传 null 表示不过滤
     * @param maxResults 最大返回数
     * @param minScore   最低相似度阈值
     * @param excludeMemoryTypes   排除的记忆类型
     */
    @Override
    public List<VectorSearchResult> search(String query,String sessionId, String userId,
                                          String memoryType, int maxResults, double minScore,UserMemoryTypeEnum... excludeMemoryTypes) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(TextSegment.from(query)).content();

            int searchLimit = Math.max(maxResults * 5, 20);

            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(searchLimit)
                    .minScore(minScore)
                    .build();

            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);

            List<String> excludeMemoryTypeStrList;
            if(excludeMemoryTypes!=null && excludeMemoryTypes.length>0) {
                excludeMemoryTypeStrList = Arrays.stream(excludeMemoryTypes).map(x -> x.getCode()).collect(Collectors.toList());
            } else {
                excludeMemoryTypeStrList = new ArrayList<>();
            }
            return result.matches().stream()
                    .filter(match -> {
                        Metadata metadata = match.embedded().metadata();
                        if (sessionId != null) {
                            String storedSessionId = metadata.getString(METADATA_SESSION_ID);
                            if (!sessionId.equals(storedSessionId)) {
                                return false;
                            }
                        }
                        if (userId != null) {
                            String storedUserId = metadata.getString(METADATA_USER_ID);
                            if (!userId.equals(storedUserId)) {
                                return false;
                            }
                        }
                        if (memoryType != null) {
                            String storedType = metadata.getString(METADATA_MEMORY_TYPE);
                            if (!memoryType.equals(storedType)) {
                                return false;
                            }
                        }
                        if(excludeMemoryTypes.length>0){
                            String storedType = metadata.getString(METADATA_MEMORY_TYPE);
                            if (excludeMemoryTypeStrList.contains(storedType)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .limit(maxResults)
                    .map(match -> {
                        Metadata m = match.embedded().metadata();
                        var r= new VectorSearchResult(
                                match.score(),
                                match.embedded().text(),
                                m != null ? m.getString(METADATA_SESSION_ID) : null,
                                m != null ? m.getString(METADATA_USER_ID) : null,
                                m != null ? m.getString(METADATA_MEMORY_TYPE) : null,
                                m != null ? m.getString(METADATA_MEMORY_DATE) : null,
                                match.embeddingId()
                        );
                        if(r.getMemoryType() != null){
                            r.setMemoryTypeName(UserMemoryTypeEnum.fromCode(r.getMemoryType()).getName());
                        }
                        return r;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            logger.error("Failed to search vector store, query={}", query, e);
            return List.of();
        }
    }
}
